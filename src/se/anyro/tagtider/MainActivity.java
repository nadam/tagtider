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
import se.anyro.tagtider.utils.RecentList;
import se.anyro.tagtider.utils.StringUtils;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

public class MainActivity extends ListActivity {
	
    private AutoCompleteTextView mStationView;
    private Station[] mStations;
    private List<String> mStationNames = new ArrayList<String>();
	private ProgressDialog mProgress;
	private AlertDialog mMessageDialog;
	private RecentList mRecentStations;
	private ArrayAdapter<String> mRecentStationsAdapter;
	private ArrayAdapter<String> mStationsAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.main);
        
        loadStations();
        setupAutocompletion();
		setupButtonActions();
		setupDialogs();
		loadRecentStations();
    }

	private void loadStations() {

        AssetManager assetManager = getAssets();
		InputStream inputStream = null;
		try {
			inputStream = assetManager.open("stations.json");
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		String json = StringUtils.readTextFile(inputStream);
		try {
			JSONArray stations = new JSONArray(json);
			mStations = new Station[stations.length()];
			for (int i = 0; i < stations.length(); ++i) {
				JSONObject station = stations.getJSONObject(i);
				mStationNames.add(station.getString("name"));
				mStations[i] = new Station(station);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	private void setupAutocompletion() {
		mStationsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_dropdown_item_1line, mStationNames);
        mStationView = (AutoCompleteTextView) findViewById(R.id.station);
		mStationView.setAdapter(mStationsAdapter);
		mStationView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				// Hide the keyboard
				InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
				imm.hideSoftInputFromWindow(mStationView.getWindowToken(), 0);
			}
		});
	}

	private void setupButtonActions() {
		
		final Button showDeparturesButton = (Button) findViewById(R.id.departures);
		final Button showArrivalsButton = (Button) findViewById(R.id.arrivals);
		
		OnClickListener clickListener = new OnClickListener() {
			@Override
			public void onClick(View button) {
				// Check if the user wrote a valid station
				int selection = mStationNames.indexOf(mStationView.getText().toString());
				if (selection >= 0) {
					Station station = mStations[selection];
					StationActivity.setStation(station.name);
					String type = Station.DEPARTURES;
					if (button == showArrivalsButton)
						type = Station.ARRIVIALS;
					StationActivity.setType(type);
					new FetchTransfersTask().execute(Integer.toString(station.id), type);
				}
			}
		};

		showDeparturesButton.setOnClickListener(clickListener);
		showArrivalsButton.setOnClickListener(clickListener);
	}

	private void setupDialogs() {
		mProgress = new ProgressDialog(this);
		mMessageDialog = new AlertDialog.Builder(this).setNeutralButton("Ok", null).create();
	}

	private void loadRecentStations() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
		mRecentStations = new RecentList(4, "recent_station", preferences);
		mRecentStationsAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, mRecentStations.getList());
		setListAdapter(mRecentStationsAdapter);		
	}
	
    @Override
    protected void onResume() {
    	super.onResume();
    	// Make sure the latest searched station is displayed
    	mRecentStationsAdapter.notifyDataSetChanged();
    }
    
	/**
	 * Class for fetching arrivals or departures for a specific station asynchronously
	 */
	private class FetchTransfersTask extends AsyncTask<String, String, String> {

    	@Override
    	protected void onPreExecute() {
    		mProgress.setMessage("Hämtar data...");
    		mProgress.show();
    	}
    	
		@Override
		protected String doInBackground(String... params) {
			
			String stationId = params[0];
			String type = params[1];
			
			HttpGet httpGet = new HttpGet("http://api.tagtider.net/v1/stations/" + stationId + "/transfers/" + type + ".json");
			
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
						StationActivity.clear();
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
							StationActivity.addTransfer(transferMap);
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
			mProgress.setMessage(values[0]);
		}
		
		@Override
		protected void onPostExecute(String errorMessage) {
			mProgress.hide();
			if (errorMessage == null) {
				// Show the arrivals or departures at this station
				Intent intent = new Intent(MainActivity.this, StationActivity.class);
				startActivity(intent);
			} else {
				showMessage(errorMessage);
			}
			mRecentStations.addItem(mStationView.getText().toString());
		}
    }
    
	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		// Temporary remove the adapter to prevent the drop down
		mStationView.setAdapter((ArrayAdapter<String>) null);
		TextView textView = (TextView) v;
		mStationView.setText(textView.getText());
		mStationView.setAdapter(mStationsAdapter);
	}
	
	public void showMessage(String message) {
		mMessageDialog.setMessage(message);
		mMessageDialog.show();
	}
}