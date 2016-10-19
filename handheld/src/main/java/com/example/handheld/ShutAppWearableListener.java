package com.example.handheld;

import android.net.Uri;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

/**
 * Created by stan on 10/18/16.
 */

public class ShutAppWearableListener extends WearableListenerService {

    @Override
    public void onCreate(){
        super.onCreate();
    }

        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            super.onMessageReceived(messageEvent);
            String event = messageEvent.getPath();
            Log.e("asd", "received message with event: " + event);

            if (event.equals("/get_translock_data")) {
                String locationData;
                try {
                    locationData = new String(messageEvent.getData(), "UTF8");
                } catch (UnsupportedEncodingException e) {
                    Log.d("asd", "Unsupported encoding");
                    return;
                }

                Uri.Builder builder = new Uri.Builder();
                // TODO: Parametrize this. For now everything is literally hardcoded.
                // Prepare the URI  and headers
                builder.scheme("http")
                        .authority("transloc-api-1-2.p.mashape.com")
                        .appendPath("stops.json")
                        .appendQueryParameter("agencies", "128")
                        .appendQueryParameter("geo_area", locationData);

                Map<String, String> headers = new HashMap<String, String>();
                headers.put("X-Mashape-Key", "fz7Q6hHUCXmshuArB2putNEJEWoup10QP7sjsnCuGdQZDKdGPg");
                headers.put("Accept", "application/json");

                // execute the http api query
                Log.d("asd", "Querying with the url: " + builder.toString());
                HttpRequestHandler reqHandler = new HttpRequestHandler(
                        this,
                        builder.toString(),
                        headers,
                        new stopsFetchedListener(),
                        false);
                reqHandler.execute();
            }
        }


    // Handler for received stops response.
    private class stopsFetchedListener implements HttpRequestHandler.Listener {

        private Vector<Stop> mStops;

        private class Stop {
            public String name;
            public int stopID;
            public float lat;
            public float lng;

            Stop(String n, int c, int la, int ln) {
                this.name = n;
                this.stopID = c;
                this.lat = la;
                this.lng = ln;
            }
        }

        @Override
        public void onResponseFetched(HttpRequestHandler.MyResult result) {
            try {
                JSONArray stopInfo = ((JSONObject)result.myObject).getJSONArray("data");
                Log.d("asd", "Fetched " + stopInfo.length() + " stops!");

                JSONObject tmpObj;
                for (int i = 0; i < stopInfo.length(); i++) {
                    tmpObj = stopInfo.getJSONObject(i);
                    mStops.add(new Stop(
                            tmpObj.getString("name"),
                            tmpObj.getInt("stop_id"),
                            tmpObj.getJSONObject("location").getInt("lat"),
                            tmpObj.getJSONObject("location").getInt("lng")
                    ));
                }

                // find the closest


            } catch (JSONException e) {
                Log.d("asd", "Shit went south! " + e.getMessage());
                return;
            }


        }

        @Override
        public void onRequestFailed() {
            Log.d("asd", "Failed to fetch close stops...");
        }
    }
}
