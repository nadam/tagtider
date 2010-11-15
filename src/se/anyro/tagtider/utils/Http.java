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

import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.DefaultHttpClient;

/**
 * Simple class for global access to the http client
 */
public class Http {
	
	private static final String APP_NAME = "Tagtider Android Client";
	private static DefaultHttpClient client;
	private static String userAgent = APP_NAME;
	
	public static DefaultHttpClient getClient() {
		if (client == null) {
			client = new DefaultHttpClient();
			Credentials creds = new UsernamePasswordCredentials("tagtider", "codemocracy");
			AuthScope scope = new AuthScope(AuthScope.ANY_HOST, AuthScope.ANY_PORT, AuthScope.ANY_REALM);
			client.getCredentialsProvider().setCredentials(scope, creds);
		}
		return client;
	}
	
	public static void setVersionName(String versionName) {
		userAgent = APP_NAME + "/" + versionName;
	}
	
	public static String getUserAgent() {
		return userAgent;
	}
}
