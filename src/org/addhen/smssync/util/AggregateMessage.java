package org.addhen.smssync.util;

import java.util.Map;

 public abstract class AggregateMessage {
	// TODO Auto-generated constructor stub - setters/getters
	public   String formId;
	public   String periodText;
	public   String orgUnit;
	protected String text;
	
	/**
	 * 
	 */
	public AggregateMessage() {
	}
	/**
	 * 
	 * @param formId
	 * @param periodText
	 * @param dataValues
	 */
	public AggregateMessage(String formId, String periodText,Map<String,String>  dataValues) {

		this.formId = formId;
		this.periodText = periodText;
	}
	
	public AggregateMessage(String text) {
		this.text = text;
	}
	/**
	 * 
	 */
	public abstract boolean parse();
	
	public abstract String getXMLString();
	public abstract AggregateMessage convert();
}
