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

import se.anyro.tagtider.model.Station;
import se.anyro.tagtider.utils.Http;
import se.anyro.tagtider.utils.StringUtils;
import android.app.ListActivity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.style.StrikethroughSpan;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.SimpleAdapter.ViewBinder;

/**
 * Activity displaying arrivals or departures at a specific station
 */
public class StationActivity extends ListActivity {

	private static final int FIVE_MINUTES = 300000;
	private static final int TEXT_COLOR_DEFAULT = 0xffeeee00;
	private static final int TEXT_COLOR_ALERT = 0xffffffff;
	
	private static long sLastUpdate = 0;
	
	private static List<Map<String, Object>> sTransfers = new ArrayList<Map<String, Object>>();
	private static SimpleAdapter sTransfersAdapter;
	
	private static int sLastStationId;
	private static String sLastType;
	
	private String mStationName;
	private int mStationId;
	private String mType;
	private TextView mEmptyView;
	private View mProgress;
	
    // Display mapping from keys to view id:s
    private static final String[] FROM_DEPARTURE = {"departure", "destination", "newDeparture", "track"};
    private static final String[] FROM_ARRIVAL = {"arrival", "origin", "newArrival", "track"};
    private static final int[] TO = {R.id.time, R.id.destination, R.id.new_time, R.id.track};
    private String[] mFrom;
    
    private ViewBinder viewBinder = new ViewBinder() {
    	public boolean setViewValue(View view, Object data, String textRepresentation) {
    		switch (view.getId()) {
    		case R.id.new_time:
    			// When there is a new time, make sure the old one is stroke through
				ViewGroup parent = (ViewGroup) view.getParent();
    			int textColor;
    			if (data != null) {
    				TextView timeView = (TextView) parent.findViewById(R.id.time);
    				SpannableString strike = new SpannableString(timeView.getText());
    			    strike.setSpan(new StrikethroughSpan(), 0, 5, 0); 
    				timeView.setText(strike, TextView.BufferType.SPANNABLE);
    			}
    			// Note! Fall through to next case
    		case R.id.time:
    			// Only show hour and minutes, e.g. 12:59
    			TextView textView = (TextView) view;
    			textView.setText(StringUtils.extractTime(textRepresentation));
    			return true;
    		case R.id.track:
				parent = (ViewGroup) view.getParent();
    			// Change text color if the train has been cancelled
    			if (textRepresentation.length() == 0 || textRepresentation.equalsIgnoreCase("x")) {
    				textRepresentation = "-";
					parent.setBackgroundResource(R.drawable.row_cancelled_background);
					textColor = TEXT_COLOR_ALERT;
    			} else {
					parent.setBackgroundResource(R.drawable.row_background);
					textColor = TEXT_COLOR_DEFAULT;
    			}
				int childCount = parent.getChildCount();
				for (int i = 0; i < childCount; ++i) {
					TextView child = (TextView) parent.getChildAt(i);
					if (child.getId() != R.id.new_time)
						child.setTextColor(textColor);
				}
    			textView = (TextView) view;
    			textView.setText(StringUtils.padLeft(textRepresentation, 4));
    			return true;
    		}
    		return false;
    	}
    };
    
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.transfers);

		Bundle extras = getIntent().getExtras();
		
		mStationName = extras.getString("stationName");
		mStationId = extras.getInt("stationId");
		mType = extras.getString("type");
		
		if (!mType.equals(sLastType) || mStationId != sLastStationId) {
			sLastUpdate = 0; // Make sure we reload the data
			sLastStationId = mStationId;
			sLastType = mType;
		}

		TextView title = (TextView) findViewById(R.id.title);
		TextView place = (TextView) findViewById(R.id.place);
		if (Station.ARRIVIALS.equals(mType)) {
			title.setText(mStationName + " - Ankomster");
			mFrom = FROM_ARRIVAL;
			place.setText("Från");
		} else {
			title.setText(mStationName + " - Avgångar");
			mFrom = FROM_DEPARTURE;
			place.setText("Till");
		}
		mEmptyView = (TextView) findViewById(android.R.id.empty);
		mProgress = findViewById(R.id.progress);
	}

	@Override
	protected void onResume() {
		super.onResume();
		
		if (getListAdapter() == null) {
			addAdapter();
		}
		
		long timeSinceLastUpdate = System.currentTimeMillis() - sLastUpdate;
		if (timeSinceLastUpdate > FIVE_MINUTES) {
			new FetchTransfersTask().execute(Integer.toString(mStationId), mType);
		} 
	}
	
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		// Start the transfer activity
		Intent intent = new Intent(this, TransferActivity.class);
		intent.putExtra("stationName", mStationName);
		intent.putExtra("stationId", mStationId);
		Map<String, Object> transfer = sTransfers.get(position);
		for (Map.Entry<String, Object> entry : transfer.entrySet()) {
			String key = entry.getKey();
			String value = (String) entry.getValue();
			intent.putExtra(key, value);
		}
		startActivity(intent);
	}

	/**
	 * Class for fetching arrivals or departures for a specific station asynchronously
	 */
	private class FetchTransfersTask extends AsyncTask<String, String, String> {

    	@Override
    	protected void onPreExecute() {
    		
    		if (Station.ARRIVIALS.equals(mType)) {
    			mEmptyView.setText("Hämtar ankomster...");
    		} else {
    			mEmptyView.setText("Hämtar avgångar...");
    		}
    		
    		mProgress.setVisibility(View.VISIBLE);
    	}
    	
		@Override
		protected String doInBackground(String... params) {
			
			String stationId = params[0];
			String type = params[1];
			
			HttpGet httpGet = new HttpGet("http://api.tagtider.net/v1/stations/" + stationId + "/transfers/" + type + ".json");
			httpGet.setHeader("User-Agent", Http.getUserAgent());
			
			try {
				HttpResponse response = Http.getClient().execute(httpGet);
				if (response.getStatusLine().getStatusCode() == 200) {
					publishProgress("Bearbetar data...");
					HttpEntity entity = response.getEntity();
					InputStream content = entity.getContent();
					
					String json = StringUtils.readTextFile(content);
					try {
						JSONObject root = new JSONObject(json);
						JSONObject station = root.getJSONObject("station");
						JSONArray transfers = station.getJSONObject("transfers").getJSONArray("transfer");
						sTransfers.clear();
						for (int i = 0; i < transfers.length(); ++i) {
							JSONObject transfer = transfers.getJSONObject(i);
							@SuppressWarnings("unchecked")
							Iterator keys = transfer.keys();
							Map<String, Object> transferMap = new HashMap<String, Object>();
							while (keys.hasNext()) {
								Object key = keys.next();
								Object value = transfer.get((String) key);
								if (value == JSONObject.NULL)
									value = null;
								transferMap.put((String) key, value);
							}
							sTransfers.add(transferMap);
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
		protected void onProgressUpdate(String... values) {
			mEmptyView.setText(values[0]);
		}
		
		@Override
		protected void onPostExecute(String errorMessage) {
			mProgress.setVisibility(View.GONE);
			if (errorMessage == null) {
				addAdapter();
				sLastUpdate = System.currentTimeMillis();
			} else {
				mEmptyView.setText(errorMessage);
				deleteAdapter();
				sLastUpdate = 0;
			}
		}
    }
	
	private void addAdapter() {
        sTransfersAdapter = new SimpleAdapter(this, sTransfers, R.layout.transfer_row, mFrom, TO);
        sTransfersAdapter.setViewBinder(viewBinder);
        setListAdapter(sTransfersAdapter);
	}
	
	private void deleteAdapter() {
		sTransfers.clear();
		setListAdapter(null);
	}
}
