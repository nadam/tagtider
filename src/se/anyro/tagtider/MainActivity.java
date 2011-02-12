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
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import se.anyro.tagtider.model.Station;
import se.anyro.tagtider.utils.Http;
import se.anyro.tagtider.utils.RecentList;
import se.anyro.tagtider.utils.StringUtils;
import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
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
	
    private static final String LAST_VERSION_CODE = "last_version_code";
	private AutoCompleteTextView mStationView;
    private Station[] mStations;
    private List<String> mStationNames = new ArrayList<String>();
	private AlertDialog mMessageDialog;
	private RecentList mRecentStations;
	private ArrayAdapter<String> mRecentStationsAdapter;
	private ArrayAdapter<String> mStationsAdapter;
	private SharedPreferences preferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.main);

        loadStations();
        setupAutocompletion();
		setupButtonActions();
		setupDialogs();
		loadRecentStations();
		
		Http.setVersionName(getVersionName());
		
		oneTimeNotify();
    }

	private void oneTimeNotify() {
		int lastVersionCode = preferences.getInt(LAST_VERSION_CODE, 0);
		if (lastVersionCode < 7) {
			showMessage(getString(R.string.one_time_notifier));
			preferences.edit().putInt(LAST_VERSION_CODE, 7).commit();
		}
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
					mRecentStations.addItem(mStationView.getText().toString());
					
					Station station = mStations[selection];
					String type = Station.DEPARTURES;
					if (button == showArrivalsButton)
						type = Station.ARRIVIALS;
					
					// Show the arrivals or departures at this station
					Intent intent = new Intent(MainActivity.this, StationActivity.class);
					intent.putExtra("stationName", station.name);
					intent.putExtra("stationId", station.id);
					intent.putExtra("type", type);
					startActivity(intent);
				}
			}
		};

		showDeparturesButton.setOnClickListener(clickListener);
		showArrivalsButton.setOnClickListener(clickListener);
	}

	private void setupDialogs() {
		mMessageDialog = new AlertDialog.Builder(this).setNeutralButton("Ok", null).create();
	}

	private void loadRecentStations() {
		mRecentStations = new RecentList(4, "recent_station", preferences);
		mRecentStationsAdapter = new ArrayAdapter<String>(this, R.layout.station_row, mRecentStations.getList());
		setListAdapter(mRecentStationsAdapter);		
	}
	
    @Override
    protected void onResume() {
    	super.onResume();
    	// Make sure the latest searched station is displayed
    	mRecentStationsAdapter.notifyDataSetChanged();
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

	private int getVersionCode() {
		try {
			PackageInfo mPackageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return mPackageInfo.versionCode;
		} catch (NameNotFoundException e) {
			return 0;
		}
	}
	
	private String getVersionName() {
		try {
			PackageInfo mPackageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
			return mPackageInfo.versionName;
		} catch (NameNotFoundException e) {
			return "UNAVAILABLE";
		}
	}
}