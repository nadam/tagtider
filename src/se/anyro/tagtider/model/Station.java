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

package se.anyro.tagtider.model;

import org.json.JSONException;
import org.json.JSONObject;

import android.location.Location;

public class Station {
	
	public static final String DEPARTURES = "departures";
	public static final String ARRIVIALS = "arrivals";
	
	public int id;
	public String name;
	public Location location;
	
	public Station(JSONObject station) throws JSONException {
		id = station.getInt("id");
		name = station.getString("name");
		String lat, lng;
		lat = station.getString("lat");
		lng = station.getString("lng");
		if (lat != null && lng != null) {
    		location = new Location("Tågtider");
    		try {
    			location.setLatitude(Double.parseDouble(lat));
    			location.setLongitude(Double.parseDouble(lng));
    		} catch (NumberFormatException e) {
    			location.setLatitude(0);
    		}
		}
	}

	@Override
	public String toString() {
		return name;
	}
}
