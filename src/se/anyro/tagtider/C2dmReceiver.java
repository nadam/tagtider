package se.anyro.tagtider;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

public class C2dmReceiver extends BroadcastReceiver {

	private static final int NOTIFICATION_ID = 42;

	@Override
	public void onReceive(Context context, Intent intent) {
	    if (intent.getAction().equals("com.google.android.c2dm.intent.REGISTRATION")) {
	        handleRegistration(context, intent);
	    } else if (intent.getAction().equals("com.google.android.c2dm.intent.RECEIVE")) {
	        handleMessage(context, intent);
	    }
	}
	
	private void handleRegistration(Context context, Intent intent) {
		String registrationId = intent.getStringExtra("registration_id");
		Bundle extras = intent.getExtras();
		if (extras.getString("error") != null) {
			Intent registeredIntent = new Intent(TransferActivity.C2DM_REGISTERED);
			registeredIntent.putExtra("error", translateC2dmError(extras.getString("error")));
			context.sendBroadcast(registeredIntent);
		} else if (intent.getStringExtra("unregistered") != null) {
			// unregistration done, new messages from the authorized sender will be rejected
		} else if (registrationId != null) {
/*			Intent registeredIntent = new Intent(TransferActivity.C2DM_REGISTERED);
			registeredIntent.putExtra("registrationId", registrationId);
			context.sendBroadcast(registeredIntent);*/
			makeNotification(context, "67", "Nytt spår eller nåt!"); // TODO: remove
		}
	}

	private String translateC2dmError(String error) {
		if ("SERVICE_NOT_AVAILABLE".equals(error))
			return "Tillfälligt fel hos Google";
		else if ("ACCOUNT_MISSING".equals(error))
			return "Google konto saknas på telefonen";
		else if ("AUTHENTICATION_FAILED".equals(error))
			return "Fel google lösenord";
		return error;
	}

	private void handleMessage(Context context, Intent intent) {
	    Bundle extras = intent.getExtras();
	    if (extras == null) {
	    	return;
	    }

	    String transfer = extras.getString("transfer_id");
	    if (transfer == null) {
	    	return;
	    }
	    
	    String message = extras.getString("message");
	    
	    makeNotification(context, transfer, message);
	}

	private void makeNotification(Context context, String transfer, String message) {
		String ns = Context.NOTIFICATION_SERVICE;
		NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(ns);
		
		int icon = android.R.drawable.stat_notify_error;
		CharSequence tickerText = "Tågtider ändring! " + message;
		long when = System.currentTimeMillis();

		Intent notificationIntent = new Intent(context, TransferActivity.class);
		notificationIntent.putExtra("id", transfer);
		notificationIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent,
				PendingIntent.FLAG_UPDATE_CURRENT);

		Notification notification = new Notification(icon, tickerText, when);
//		notification.defaults |= Notification.DEFAULT_ALL;
		notification.setLatestEventInfo(context, "Tågtider", message, contentIntent);
		notification.flags |= Notification.FLAG_AUTO_CANCEL;
	       
		mNotificationManager.notify(NOTIFICATION_ID, notification);
	}
}
