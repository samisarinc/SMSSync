/** 
 ** Copyright (c) 2010 Ushahidi Inc
 ** All rights reserved
 ** Contact: team@ushahidi.com
 ** Website: http://www.ushahidi.com
 ** 
 ** GNU Lesser General Public License Usage
 ** This file may be used under the terms of the GNU Lesser
 ** General Public License version 3 as published by the Free Software
 ** Foundation and appearing in the file LICENSE.LGPL included in the
 ** packaging of this file. Please review the following information to
 ** ensure the GNU Lesser General Public License version 3 requirements
 ** will be met: http://www.gnu.org/licenses/lgpl.html.	
 **	
 **
 ** If you have questions regarding the use of this file, please contact
 ** Ushahidi developers at team@ushahidi.com.
 ** 
 **/

package org.addhen.smssync.services;

import org.addhen.smssync.MessagesTabActivity;
import org.addhen.smssync.PendingMessagesActivity;
import org.addhen.smssync.Prefrences;
import org.addhen.smssync.R;
import org.addhen.smssync.SentMessagesActivity;
import org.addhen.smssync.receivers.ConnectivityChangedReceiver;
import org.addhen.smssync.util.AggregateMessage;
import org.addhen.smssync.util.AggregateMessageFactory;
import org.addhen.smssync.util.SentMessagesUtil;
import org.addhen.smssync.util.ServicesConstants;
import org.addhen.smssync.util.Util;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.PowerManager;
import android.os.Process;
import android.telephony.SmsMessage;
import android.util.Log;

public class SmsReceiverService extends Service {
	private static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";

	private ServiceHandler mServiceHandler;

	private Looper mServiceLooper;

	private Context mContext;

	private String messagesFrom = "";

	private String messagesBody = "";

	private String messagesTimestamp = "";

	private String messagesId = "";

	private static final Object mStartingServiceSync = new Object();

	private static PowerManager.WakeLock mStartingService;

	private static WifiManager.WifiLock wifilock;

	public double latitude;

	public double longitude;

	private NotificationManager notificationManager;

	private SmsMessage sms;

	private static final String CLASS_TAG = SmsReceiverService.class.getSimpleName();

	private Handler handler = new Handler();

	// holds the status of the sync and sends it to pending messages activity to
	// update the ui
	private Intent statusIntent;

	@Override
	public void onCreate() {

		HandlerThread thread = new HandlerThread(CLASS_TAG, Process.THREAD_PRIORITY_BACKGROUND);
		thread.start();
		mContext = getApplicationContext();
		statusIntent = new Intent(ServicesConstants.AUTO_SYNC_ACTION);
		Prefrences.loadPreferences(mContext);
		notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
		mServiceLooper = thread.getLooper();
		mServiceHandler = new ServiceHandler(mServiceLooper);

	}

	@Override
	public void onStart(Intent intent, int startId) {
		Message msg = mServiceHandler.obtainMessage();
		msg.arg1 = startId;
		msg.obj = intent;
		mServiceHandler.sendMessage(msg);
	}

	@Override
	public void onDestroy() {
		mServiceLooper.quit();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	private final class ServiceHandler extends Handler {
		public ServiceHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {

			int serviceId = msg.arg1;
			Intent intent = (Intent)msg.obj;
			if (intent != null) {
				String action = intent.getAction();

				if (ACTION_SMS_RECEIVED.equals(action)) {
					handleSmsReceived(intent);
				}
			}
			finishStartingService(SmsReceiverService.this, serviceId);
		}
	}

	/**
	 * Handle receiving a SMS message
	 */
	private void handleSmsReceived(Intent intent) {

		Bundle bundle = intent.getExtras();
		Prefrences.loadPreferences(SmsReceiverService.this);

		if (bundle != null) {
			SmsMessage[] messages = getMessagesFromIntent(intent);
			sms = messages[0];
			if (messages != null) {
				// extract message details. phone number and the message body
				messagesFrom = sms.getOriginatingAddress();
				messagesTimestamp = String.valueOf(sms.getTimestampMillis());
				messagesId = String.valueOf(Util.getId(this, sms, "id"));
				String body;
				if (messages.length == 1 || sms.isReplace()) {
					body = sms.getDisplayMessageBody();

				} else {
					StringBuilder bodyText = new StringBuilder();
					for (int i = 0; i < messages.length; i++) {
						bodyText.append(messages[i].getMessageBody());
					}
					body = bodyText.toString();
				}
				messagesBody = body;
			}
		}

		if (Prefrences.enabled) {
			//check if right format and post if possible, otherwise add to pending box
			AggregateMessage aggregateMessage = AggregateMessageFactory.getAggregateMessage(messagesBody, messagesTimestamp );
			if(aggregateMessage != null) {
				if (Util.isConnected(SmsReceiverService.this)) {
					// get the right format
					aggregateMessage.parse();
					AggregateMessage mappedMessage = aggregateMessage.convert();

					String xml = mappedMessage.getXMLString();

					boolean posted = Util.postToAWebService(xml, SmsReceiverService.this);
					// send auto response from phone not server.
					if (Prefrences.enableReply) {
						// send auto response
						Util.sendSms(SmsReceiverService.this, messagesFrom, Prefrences.reply);
					}

					if (!posted) {
						this.showNotification(messagesBody, getString(R.string.sending_failed));
						this.postToPendingBox();
						handler.post(mDisplayMessages);

						// attempt to make a data connection
						connectToDataNetwork();

						if (Prefrences.autoDelete) {
							Util.delSmsFromInbox(SmsReceiverService.this, sms);
						}
					} else {
						this.postToSentBox();
						if (Prefrences.autoDelete) {
							Util.delSmsFromInbox(SmsReceiverService.this, sms);
						}
						this.showNotification(messagesBody, getString(R.string.sending_succeeded));
					}

				} else {
					// no internet
					this.showNotification(messagesBody, getString(R.string.sending_failed));
					this.postToPendingBox();
					handler.post(mDisplayMessages);

					connectToDataNetwork();
					if (Prefrences.autoDelete) {
						Util.delSmsFromInbox(SmsReceiverService.this, sms);
					}
				}
			}
		}
	}

	/**
	 * Show a notification
	 * 
	 * @param String message to display
	 * @param String notification title
	 */
	private void showNotification(String message, String notification_title) {

		Intent baseIntent = new Intent(this, MessagesTabActivity.class);
		baseIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		Notification notification = new Notification(R.drawable.icon, getString(R.string.status),
				System.currentTimeMillis());
		PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, baseIntent, 0);
		notification.setLatestEventInfo(this, notification_title, message, pendingIntent);
		notificationManager.notify(1, notification);

	}

	/**
	 * Put failed messages to be sent to the callback URL to the local database.
	 * 
	 * @return void
	 */
	private void postToPendingBox() {
		Log.i(CLASS_TAG, "postToOutbox(): post failed messages to outbox");
		// Get message id.
		Long msgId = new Long(Util.getId(SmsReceiverService.this, sms, "id"));

		String messageId = msgId.toString();

		String messageDate = String.valueOf(sms.getTimestampMillis());
		Util.smsMap.put("messagesFrom", messagesFrom);
		Util.smsMap.put("messagesBody", messagesBody);
		Util.smsMap.put("messagesDate", messageDate);
		Util.smsMap.put("messagesId", messageId);

		Util.processMessages(SmsReceiverService.this);

	}

	/**
	 * Put successfully sent messages to a local database for logging sake
	 * 
	 * @return void
	 */
	private void postToSentBox() {
		Log.i(CLASS_TAG, "postToOutbox(): post failed messages to outbox");
		// Get message id.
		Long msgId = new Long(Util.getId(SmsReceiverService.this, sms, "id"));

		String messageId = msgId.toString();

		String messageDate = String.valueOf(sms.getTimestampMillis());
		SentMessagesUtil.smsMap.put("messagesFrom", messagesFrom);
		SentMessagesUtil.smsMap.put("messagesBody", messagesBody);
		SentMessagesUtil.smsMap.put("messagesDate", messageDate);
		SentMessagesUtil.smsMap.put("messagesId", messageId);

		int status = SentMessagesUtil.processSentMessages(SmsReceiverService.this);
		statusIntent.putExtra("status", status);
		sendBroadcast(statusIntent);

	}

	/**
	 * Get the SMS message.
	 * 
	 * @param Intent intent - The SMS message intent.
	 * @return SmsMessage
	 */
	public static final SmsMessage[] getMessagesFromIntent(Intent intent) {
		Log.i(CLASS_TAG, "getMessagesFromIntent(): getting SMS message");
		Object[] messages = (Object[])intent.getSerializableExtra("pdus");

		if (messages == null) {
			return null;
		}

		if (messages.length == 0) {
			return null;
		}

		byte[][] pduObjs = new byte[messages.length][];

		for (int i = 0; i < messages.length; i++) {
			pduObjs[i] = (byte[])messages[i];
		}

		byte[][] pdus = new byte[pduObjs.length][];
		int pduCount = pdus.length;

		SmsMessage[] msgs = new SmsMessage[pduCount];
		for (int i = 0; i < pduCount; i++) {
			pdus[i] = pduObjs[i];
			msgs[i] = SmsMessage.createFromPdu(pdus[i]);
		}
		return msgs;
	}

	/**
	 * Start the service to process the current event notifications, acquiring
	 * the wake lock before returning to ensure that the service will run.
	 * 
	 * @param Context context - The context of the calling activity.
	 * @param Intent intent - The calling intent.
	 * @return void
	 */
	public static void beginStartingService(Context context, Intent intent) {
		synchronized (mStartingServiceSync) {

			if (mStartingService == null) {
				PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
				mStartingService = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, CLASS_TAG);
				mStartingService.setReferenceCounted(false);
			}

			// keep wifi alive
			if (wifilock == null) {
				WifiManager manager = (WifiManager)context.getSystemService(Context.WIFI_SERVICE);
				wifilock = manager.createWifiLock(CLASS_TAG);
			}

			mStartingService.acquire();
			wifilock.acquire();
			context.startService(intent);
		}
	}

	/**
	 * Called back by the service when it has finished processing notifications,
	 * releasing the wake lock and wifi lock if the service is now stopping.
	 * 
	 * @param Service service - The calling service.
	 * @param int startId - The service start id.
	 * @return void
	 */
	public static void finishStartingService(Service service, int startId) {

		synchronized (mStartingServiceSync) {

			if (mStartingService != null) {
				if (service.stopSelfResult(startId)) {
					mStartingService.release();
				}
			}

		}
	}

	// Display pending messages.
	final Runnable mDisplayMessages = new Runnable() {

		public void run() {
			PendingMessagesActivity.showMessages();
		}

	};

	// Display pending messages.
	final Runnable mDisplaySentMessages = new Runnable() {

		public void run() {
			SentMessagesActivity.showMessages();
		}

	};

	/**
	 * Makes an attempt to connect to a data network.
	 */
	public void connectToDataNetwork() {
		// Enable the Connectivity Changed Receiver to listen for
		// connection to a network so we can send pending messages.
		PackageManager pm = getPackageManager();
		ComponentName connectivityReceiver = new ComponentName(this,
				ConnectivityChangedReceiver.class);
		pm.setComponentEnabledSetting(connectivityReceiver,
				PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
	}
}
