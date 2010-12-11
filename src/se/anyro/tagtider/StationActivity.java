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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import se.anyro.tagtider.model.Station;
import se.anyro.tagtider.utils.StringUtils;
import android.app.ListActivity;
import android.content.Intent;
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

	private static final int BG_COLOR_DEFAULT = 0xff000000;
	private static final int BG_COLOR_DELAYED = 0xffcc1100;
	private static final int TEXT_COLOR_DEFAULT = 0xffeeee00;
	private static final int TEXT_COLOR_DELAYED = 0xffffffff;
	private static final int TEXT_COLOR_CANCELLED = 0xffaaaaaa;
	
	private static List<Map<String, Object>> mTransfers = new ArrayList<Map<String, Object>>();
	private SimpleAdapter mTransfersAdapter;
	private static String mStation;
	private static String mType;
	
    // Display mapping from keys to view id:s
    private static final String[] FROM_DEPARTURE = {"departure", "destination", "newDeparture", "track"};
    private static final String[] FROM_ARRIVAL = {"arrival", "origin", "newArrival", "track"};
    private static final int[] TO = {R.id.time, R.id.destination, R.id.new_time, R.id.track};
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		setContentView(R.layout.transfers);
		
		TextView title = (TextView) findViewById(R.id.title);
		TextView place = (TextView) findViewById(R.id.place);
		String[] from;
		if (mType == Station.ARRIVIALS) {
			title.setText(mStation + " - Ankomster");
			from = FROM_ARRIVAL;
			place.setText("Från");
		} else {
			title.setText(mStation + " - Avgångar");
			from = FROM_DEPARTURE;
			place.setText("Till");
		}
		
        mTransfersAdapter = new SimpleAdapter(this, mTransfers, R.layout.transfer_row, from, TO);
        mTransfersAdapter.setViewBinder(new ViewBinder() {
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
        				try {
        					parent.setBackgroundResource(R.drawable.row_delayed_background);
        				}
        				catch (Exception e) {
        					parent.setBackgroundColor(BG_COLOR_DELAYED);
        				}
        				textColor = TEXT_COLOR_DELAYED;
        			} else {
        				try {
        					parent.setBackgroundResource(R.drawable.row_background);
        				}
        				catch (Exception e) {
        					parent.setBackgroundColor(BG_COLOR_DEFAULT);
        				}
        				textColor = TEXT_COLOR_DEFAULT;
        			}
    				int childCount = parent.getChildCount();
    				for (int i = 0; i < childCount; ++i) {
    					TextView child = (TextView) parent.getChildAt(i);
    					child.setTextColor(textColor);
    				}
        			// Note! Fall through to next case
        		case R.id.time:
        			// Only show hour and minutes, e.g. 12:59
        			TextView textView = (TextView) view;
        			textView.setText(StringUtils.extractTime(textRepresentation));
        			return true;
        		case R.id.track:
        			// Change text color if the train has been cancelled
        			if (textRepresentation.length() == 0 || textRepresentation.equalsIgnoreCase("x")) {
        				textRepresentation = "x";
        				parent = (ViewGroup) view.getParent();
        				childCount = parent.getChildCount();
        				for (int i = 0; i < childCount; ++i) {
        					TextView child = (TextView) parent.getChildAt(i);
        					child.setTextColor(TEXT_COLOR_CANCELLED);
        				}
        			}
        			textView = (TextView) view;
        			textView.setText(StringUtils.padLeft(textRepresentation, 4));
        			return true;
        		}
        		return false;
        	}
        });
        setListAdapter(mTransfersAdapter);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		// Start the transfer activity
		Intent intent = new Intent(this, TransferActivity.class);
		intent.putExtra("station", mStation);
		Map<String, Object> transfer = mTransfers.get(position);
		for (Map.Entry<String, Object> entry : transfer.entrySet()) {
			intent.putExtra(entry.getKey(), (String) entry.getValue());
		}
		startActivity(intent);
	}

	public static void setStation(String station) {
		mStation = station;
	}
	
	public static void addTransfer(Map<String, Object> transfer) {
		mTransfers.add(transfer);
	}

	public static void clear() {
		mTransfers.clear();
	}

	public static void setType(String type) {
		mType = type;
	}
}
