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

/**
 * A transfer is the information about a train arriving and departing at a specific station.
 */
public class Transfer {

	public int id;
	public String arrival, departure; // Date/time
	public String origin, destination; // Station names
	public int track;
	public int train;
	public String type; // Train type, e.g. "SJ Regional"
	public String comment;
	public String detected, updated; // Date/time

	public Transfer(JSONObject station) throws JSONException {
		id = station.getInt("id");
		arrival = station.getString("arrival");
		departure = station.getString("departure");
		origin = station.getString("origin");
		destination = station.getString("destination");
		track = station.getInt("track");
    	train = station.getInt("train");
    	type = station.getString("type");
    	comment = station.getString("comment");
    	detected = station.getString("detected");
    	updated = station.getString("updated");	
	}
	
	@Override
	public String toString() {
		return origin + "/" + destination;
	}
}
