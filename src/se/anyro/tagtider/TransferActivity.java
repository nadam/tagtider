/*
 * Copyright (C) 2010 Adam Nybäck
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package se.anyro.tagtider;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpGet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import se.anyro.tagtider.utils.Http;
import se.anyro.tagtider.utils.StringUtils;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.telephony.gsm.SmsManager;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.SimpleAdapter.ViewBinder;

@SuppressWarnings("deprecation")
public class TransferActivity extends ListActivity {

	private SimpleAdapter mChangesAdapter;
	private List<Map<String, Object>> mChanges = new ArrayList<Map<String, Object>>();

	private final String SMS_SENT = "se.anyro.tagtider.SMS_SENT";
    private SmsStatusReceiver mSmsStatusReceiver = new SmsStatusReceiver();
    private IntentFilter mSentFilter = new IntentFilter(SMS_SENT);
	private ProgressDialog mProgress;
	private AlertDialog mDialog;
    
	// Display mapping from keys to view id:s
    private static final String[] FROM = {"detected", "comment", "other"};
    private static final int[] TO = {R.id.time, R.id.comment, R.id.other};
    
	private TextView mEmptyView;
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		setContentView(R.layout.transfer);
		
		TextView trainView = (TextView) findViewById(R.id.train);
		
		ViewGroup originGroup = (ViewGroup) findViewById(R.id.origin_group);
		TextView originView = (TextView) findViewById(R.id.origin);
		TextView arrivalView = (TextView) findViewById(R.id.arrival);
	
		TextView stationTrackView = (TextView) findViewById(R.id.station_track);

		ViewGroup destinationGroup = (ViewGroup) findViewById(R.id.destination_group);
		TextView destinationView = (TextView) findViewById(R.id.destination);
		TextView departureView = (TextView) findViewById(R.id.departure);

		TextView commentView = (TextView) findViewById(R.id.comment);
		mEmptyView = (TextView) findViewById(android.R.id.empty);
		
		Intent intent = getIntent();
		final Bundle extras = intent.getExtras();
		
		trainView.setText("Tåg " + extras.getString("train") + " (" + extras.getString("type") + ")");

		String origin = extras.getString("origin");
		if (origin != null) {
			originView.setText("Från " + origin);
			originGroup.setVisibility(View.VISIBLE);
		} else {
			originGroup.setVisibility(View.GONE);
		}

		String track = extras.getString("track");
		if (track == null || track.equalsIgnoreCase("x"))
			track = "";
		
		String arrival = extras.getString("arrival");
		if (arrival != null) { 
			arrivalView.setText("Ankommer " + StringUtils.extractTime(arrival));
			String newArrival = extras.getString("newArrival"); 
			if (newArrival != null) {
				newArrival = StringUtils.extractTime(newArrival);
				SpannableString strike = new SpannableString(arrivalView.getText() + " " + newArrival);
			    strike.setSpan(new StrikethroughSpan(), strike.length() - 11, strike.length() - 6, 0); 
				arrivalView.setText(strike, TextView.BufferType.SPANNABLE);
			}
			if (track.length() == 0) {
				SpannableString strike = new SpannableString(arrivalView.getText());
			    strike.setSpan(new StrikethroughSpan(), strike.length() - 5, strike.length(), 0); 
			    arrivalView.setText(strike, TextView.BufferType.SPANNABLE);
			}
		}

		if (track.length() > 0)
			stationTrackView.setText(extras.getString("station") + ", spår " +  track);
		else
			stationTrackView.setText(extras.getString("station"));

		String destination = extras.getString("destination");
		if (destination != null) {
			destinationView.setText("Till " + destination);
			destinationGroup.setVisibility(View.VISIBLE);
		} else {
			destinationGroup.setVisibility(View.GONE);
		}
	
		String departure = extras.getString("departure");
		if (departure != null) {
			departureView.setText("Avgår " + StringUtils.extractTime(departure));
			String newDeparture = extras.getString("newDeparture"); 
			if (newDeparture != null) {
				newDeparture = StringUtils.extractTime(newDeparture);
				SpannableString strike = new SpannableString(departureView.getText() + " " + newDeparture);
			    strike.setSpan(new StrikethroughSpan(), strike.length() - 11, strike.length() - 6, 0); 
				departureView.setText(strike, TextView.BufferType.SPANNABLE);
			}
			if (track.length() == 0) {
				SpannableString strike = new SpannableString(departureView.getText());
			    strike.setSpan(new StrikethroughSpan(), strike.length() - 5, strike.length(), 0); 
				departureView.setText(strike, TextView.BufferType.SPANNABLE);
			}
		}
		
		String comment = extras.getString("comment");
		if (comment != null) {
			commentView.setText(comment);
			commentView.setVisibility(View.VISIBLE);
		} else {
			commentView.setVisibility(View.GONE);
		}
		
		new FetchChangesTask().execute(extras.getString("id"));
		
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setTitle(R.string.sms_dialog_title);
		builder.setMessage(R.string.sms_dialog_message);
		builder.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
			String train = extras.getString("train");
			String station = extras.getString("station");
			@Override
			public void onClick(DialogInterface dialog, int which) {
				sendSms("0730121096", train + " " + station);
			}
		});
		builder.setNegativeButton("Avbryt", null);
		final AlertDialog smsDialog = builder.create();
		
		Button sendSmsButton = (Button) findViewById(R.id.sms);
		sendSmsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				smsDialog.show();
			}
		});
		
		mProgress = new ProgressDialog(this);
		mDialog = new AlertDialog.Builder(this).setNeutralButton("Ok", null).create();
	}	
	
	private void addAdapter() {
        mChangesAdapter = new SimpleAdapter(this, mChanges, R.layout.change_row, FROM, TO);
        mChangesAdapter.setViewBinder(new ViewBinder() {
        	public boolean setViewValue(View view, Object data, String textRepresentation) {
        		// Hide views with empty text
        		if (textRepresentation.length() == 0) {
        			view.setVisibility(View.GONE);
        			return true;
        		}
        		view.setVisibility(View.VISIBLE);

        		return false;
        	}
        });
        setListAdapter(mChangesAdapter);
	}
	
    private class FetchChangesTask extends AsyncTask<String, String, String> {

    	@Override
    	protected void onPreExecute() {
    		mEmptyView.setText("Hämtar ändringar...");
    	}
    	
		@Override
		protected String doInBackground(String... params) {
			
			String transferId = params[0];
			
			HttpGet httpGet = new HttpGet("http://api.tagtider.net/v1/transfers/" + transferId + ".json");
			
			try {
				HttpResponse response = Http.getClient().execute(httpGet);
				if (response.getStatusLine().getStatusCode() == 200) {
					HttpEntity entity = response.getEntity();
					InputStream content = entity.getContent();
					
					String json = StringUtils.readTextFile(content);
					try {
						JSONObject root = new JSONObject(json);
						JSONObject transfer = root.getJSONObject("transfer");
						if (!transfer.has("changes"))
							return "Inga ändringar ännu";
						JSONArray changes = transfer.getJSONObject("changes").getJSONArray("change");

						for (int i = 0; i < changes.length(); ++i) {
							JSONObject change = changes.getJSONObject(i);
							@SuppressWarnings("unchecked")
							Iterator keys = change.keys();
							Map<String, Object> changeMap = new HashMap<String, Object>();
							while (keys.hasNext()) {
								Object key = keys.next();
								Object value = change.get((String) key);
								if (value == JSONObject.NULL)
									value = null;
								changeMap.put((String) key, value);
							}
							
							String track = change.getString("track");
							if (track.length() > 0)
								track = "Spår " + track;
							
							String arrival = change.getString("arrival");
							if (arrival.length() > 0 && arrival.charAt(0) != '0')
								arrival = "Ankommer " + StringUtils.extractTime(arrival);
							else
								arrival = "";
							
							String departure = change.getString("departure");
							if (departure.length() > 0 && departure.charAt(0) != '0')
								departure = "Avgår " + StringUtils.extractTime(departure);
							else
								departure = "";
							
							String[] other = new String[]{track, arrival, departure};
							changeMap.put("other", StringUtils.join(other, ", "));
							mChanges.add(changeMap);
						}
						return null;
					} catch (JSONException e) {
						return "Tillfälligt fel i svaret";
					}
				}

			} catch (ClientProtocolException e) {
				return "Kommunikationsfel";
			} catch (IOException e) {
				return "Ingen kontakt";
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(String result) {
			if (result == null) {
				addAdapter();
			} else {
				mEmptyView.setText(result);
			}
		}
    }
    
	private void sendSms(String phoneNumber, String message) {
		 
        registerReceiver(mSmsStatusReceiver, mSentFilter);

        PendingIntent sentIntent = PendingIntent.getBroadcast(this, 0, new Intent(SMS_SENT), 0);
 
        ArrayList<PendingIntent> sentIntents = new ArrayList<PendingIntent>();
        sentIntents.add(sentIntent); 
        
        SmsManager sms = SmsManager.getDefault();
        ArrayList<String> smstext = sms.divideMessage(message);
        
		mProgress.setMessage("Hämtar data...");
		mProgress.show();

        // Using multipart as a work-around for a bug in HTC Tattoo
        sms.sendMultipartTextMessage(phoneNumber, null, smstext, sentIntents, null);
    }
	
    private class SmsStatusReceiver extends BroadcastReceiver {
    	
        @Override
        public void onReceive(Context context, Intent intent) {
        	
        	if (intent.getAction().equals(SMS_SENT)) {
        		onSmsSent();
            	unregisterReceiver(mSmsStatusReceiver);
        	}
        }

		private void onSmsSent() {

			mProgress.hide();

			String error = null;
			switch (getResultCode()) {
			case Activity.RESULT_OK:
				break;
			case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
				error = "Tekniskt fel";
				break;
			case SmsManager.RESULT_ERROR_NO_SERVICE:
				error = "Ingen service";
				break;
			case SmsManager.RESULT_ERROR_NULL_PDU:
				error = "Ingen PDU";
				break;
			case SmsManager.RESULT_ERROR_RADIO_OFF:
				error = "SMS är avstängt på telefonen";
				break;
			default:
				error = "Okänt fel: " + getResultCode();
			}
			
			if (error != null) {
				showMessage("Problem", error);
			}
		}
    }
	
	public void showMessage(String title, String message) {
		mDialog.setTitle(title);
		mDialog.setMessage(message);
		mDialog.show();
	}    
}
