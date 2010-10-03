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

package se.anyro.tagtider.utils;

import java.util.LinkedList;
import java.util.List;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;

/**
 * Maintains a queue of strings with a max size, stored as preferences.
 */
public class RecentList {

	private LinkedList<String> mItems = new LinkedList<String>();
	private int mMaxItems;
	private SharedPreferences mPreferences;
	private String mPreferenceName;
	
	public RecentList(int maxItems, String name, SharedPreferences preferences) {
		mMaxItems = maxItems;
		mPreferenceName = name;
		mPreferences = preferences;
		load();
	}
	
	public void addItem(String item) {
		// Move item to top of list
		mItems.remove(item);
		mItems.addFirst(item);
		
		if (mItems.size() > mMaxItems) {
			mItems.removeLast();
		}
		
		save();
	}

	private void save() {
		Editor editor = mPreferences.edit();
		for (int i=0; i < mItems.size(); ++i) {
			editor.putString(mPreferenceName + "." + i, mItems.get(i));
		}
		editor.commit();
	}

	private void load() {
		for (int i = 0; i < mMaxItems; ++i) {
			String item = mPreferences.getString(mPreferenceName + '.' + i, null);
			if (item == null)
				break;
			mItems.addLast(item);
		}
	}
	
	public List<String> getList() {
		return mItems;
	}
}
