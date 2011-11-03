package org.addhen.smssync.util;

import java.io.StringWriter;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

import org.xmlpull.v1.XmlSerializer;

import android.util.Log;
import android.util.Xml;

/**
 * Class PipedMessage
 * @author 
 *
 */
class PipedMessage extends AggregateMessage {
	private static final String CLASS_TAG = PipedMessage.class
			.getCanonicalName();
	private Map<String, String> dataValues;

	public PipedMessage(String formId, String periodText,
			Map<String, String> dataValues) {
		super(formId, periodText, dataValues);
		// TODO Auto-generated constructor stub
	}

	public PipedMessage(String body) {
		super(body);
		dataValues = new HashMap<String, String>();
	}

	public PipedMessage(PipedMessage pipedMessage) {
		this.formId = pipedMessage.formId;
		this.periodText = pipedMessage.periodText;
		this.dataValues = new HashMap<String, String>();
		this.orgUnit = pipedMessage.orgUnit;
	}

	/**
	 * 
	 */
	public boolean parse() {

		if (text == null || text.length() < 0) {
			return false;
		}

		String[] values;

		String[] parts = text.split("#");
		if (parts.length != 3) {
			return false;
		}

		formId = parts[0];
		periodText = parts[1];
		values = parts[2].split("\\|", 1000);

		for (int i = 0; i < values.length; i++) {
			dataValues.put(new String("" + i), values[i]);
		}

		return true;
	}
	public String getXMLString() {
		StringWriter writer;
		XmlSerializer serializer;

		writer = new StringWriter();
		serializer = Xml.newSerializer();
		// start building xml file
		try {
			// we set the FileOutputStream as output for the serializer, using
			serializer.setOutput(writer);


			// Data
			serializer.startTag(null, "dataValueSet");
			serializer.attribute(null, "xmlns", "http://dhis2.org/schema/dxf/2.0-SNAPSHOT");
			serializer.attribute(null, "dataSet", formId);
			serializer.attribute(null, "period", periodText);
			serializer.attribute(null, "orgUnit", orgUnit);

			for (String element : dataValues.keySet()) {
				serializer.startTag(null, "dataValue");
				serializer.attribute(null, "dataElement", element);
				serializer.attribute(null, "value", dataValues.get(element));
				serializer.endTag(null, "dataValue");
			}
			serializer.endTag(null, "dataValueSet");

			serializer.endDocument();
		} catch (Exception e) {
			Log.i(CLASS_TAG, "Error occurred while creating xml");
			return null;
		}
		return writer.toString();
	}

	@Override
	public AggregateMessage convert() {
		PipedMessage convertedMsg = new PipedMessage(this);		
		DhisMappingHandler dhisMapping = new DhisMappingHandler();
		convertedMsg.formId = dhisMapping.getDataSetUUID(convertedMsg.formId);
		convertedMsg.orgUnit = dhisMapping.getOrgUnitID(convertedMsg.orgUnit);
		for (String element : this.dataValues.keySet()) {
			convertedMsg.dataValues.put(dhisMapping.getDataElementID(element),this.dataValues.get(element));
		}
		return convertedMsg;
	}	
}

/**
 * Class KeysMessage
 * @author 
 *
 */
class PairMessage extends AggregateMessage {
	private static final String CLASS_TAG = PairMessage.class
			.getCanonicalName();
	private Map<String, String> dataValues;
	private String date;

	public PairMessage(String formId, String periodText,
			Map<String, String> dataValues) {
		super(formId, periodText, dataValues);
		// TODO Auto-generated constructor stub
	}

	public PairMessage(String body) {
		super(body);
		dataValues = new HashMap<String, String>();
	}

	public PairMessage(String body, String date) {
		this(body);
		this.date = date;
	}
	
	public PairMessage(PairMessage pairMessage) {
		this.formId = pairMessage.formId;
		this.periodText = pairMessage.periodText;
		this.dataValues = new HashMap<String, String>();
		this.orgUnit = pairMessage.orgUnit;
	}

	public boolean parse() {
		if (text == null || text.length() < 0) {
			return false;
		}
		formId = text.split("\\s+")[0];

		String rest = text.substring(formId.length());
		String elementValuePairs[] = rest.split(",", 1000);

		for (int i = 0; i < elementValuePairs.length; i++) {
			String[] pair = elementValuePairs[i].split("=");
			if (pair.length == 2) {
				dataValues.put(pair[0].trim(), pair[1].trim());
			}
		}

		StringBuilder stringBuilder = new StringBuilder();
		Calendar cal = Calendar.getInstance();
		cal.setTimeInMillis(Long.parseLong(date));
		
		stringBuilder.append(cal.get(Calendar.YEAR));
		int month = cal.get(Calendar.MONTH) + 1;
		
		if(month<10) 
			stringBuilder.append("0" + month);
		else
			stringBuilder.append(month);
		periodText = stringBuilder.toString();
		return true;

	}
	
	public String getXMLString() {
		StringWriter writer;
		XmlSerializer serializer;

		writer = new StringWriter();
		serializer = Xml.newSerializer();
		// start building xml file
		try {
			// we set the FileOutputStream as output for the serializer, using
			serializer.setOutput(writer);

			// Data
			serializer.startTag(null, "dataValueSet");
			serializer.attribute(null, "xmlns", "http://dhis2.org/schema/dxf/2.0-SNAPSHOT");
			serializer.attribute(null, "dataSet", formId);
			serializer.attribute(null, "period", periodText);
			serializer.attribute(null, "orgUnit", orgUnit);

			for (String element : dataValues.keySet()) {
				serializer.startTag(null, "dataValue");
				// gets the correct list of dataelements, and gets item
				serializer.attribute(null, "dataElement", element);
				serializer.attribute(null, "value", dataValues.get(element));
				serializer.endTag(null, "dataValue");
			}
			serializer.endTag(null, "dataValueSet");

			serializer.endDocument();
		} catch (Exception e) {
			Log.i(CLASS_TAG, "Error occurred while creating xml");
			return null;
		}
		return writer.toString();
	}

	@Override
	public AggregateMessage convert() {
		PairMessage convertedMsg = new PairMessage(this);		
		DhisMappingHandler dhisMapping = new DhisMappingHandler();
		convertedMsg.formId = dhisMapping.getDataSetUUID(convertedMsg.formId);
		convertedMsg.orgUnit = dhisMapping.getOrgUnitID(convertedMsg.orgUnit);
		for (String element : this.dataValues.keySet()) {
			convertedMsg.dataValues.put(dhisMapping.getDataElementID(element),this.dataValues.get(element));
		}
		return convertedMsg;
	}
}

public class AggregateMessageFactory {

	private static final String PIPED_REGEX = "\\w+#\\w+#((\\w+\\|)*(\\w+))";
	private static final String PAIR_REGEX = "\\w+\\s+((\\w+=\\w+\\,\\s*)*(\\w+=\\w+){1})";

	/**
	 * Returns AggregateMessage according to the body
	 * 
	 * @param body
	 * @return
	 */
	public static AggregateMessage getAggregateMessage(final String body,
			final String date) {
		if (body == null)
			return null;
		Pattern p1 = Pattern.compile(PIPED_REGEX);
		Pattern p2 = Pattern.compile(PAIR_REGEX);
		if (p1.matcher(body).matches()) {
			// piped message format
			return new PipedMessage(body);
		} else if (p2.matcher(body).matches()) {
			// pair message format
			return new PairMessage(body, date);
		} else {
			return null;
		}
	}

}
