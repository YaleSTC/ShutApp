package com.example.handheld;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.WearableListenerService;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Vector;

/**
 * Created by stan on 10/18/16.
 */

public class ShutAppWearableListener extends WearableListenerService {

    double myLong;
    double myLat;

    @Override
    public void onCreate() {
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
            myLong = Float.valueOf(locationData.split(",")[0]);
            myLat = Float.valueOf(locationData.split(",")[1].split("\\|")[0]);
            builder.scheme("https")
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

        private Vector<Stop> mStops = new Vector<>();

        private class Stop {
            public String name;
            public int stopID;
            public double lat;
            public double lng;
            public String address;

            Stop(String n, int c, double la, double ln, String addr) {
                this.name = n;
                this.stopID = c;
                this.lat = la;
                this.lng = ln;
                this.address = addr;
            }
        }

        @Override
        public void onResponseFetched(HttpRequestHandler.MyResult result) {
            try {
                JSONArray stopInfo = ((JSONObject) result.myObject).getJSONArray("data");
                Log.d("asd", "Fetched " + stopInfo.length() + " stops!");

                JSONObject tmpObj;
                Geocoder geo = new Geocoder(getApplicationContext(), Locale.getDefault());
                for (int i = 0; i < stopInfo.length(); i++) {
                    tmpObj = stopInfo.getJSONObject(i);
                    List<Address> addresses = geo.getFromLocation(
                            tmpObj.getJSONObject("location").getDouble("lat"),
                            tmpObj.getJSONObject("location").getDouble("lng"),
                            1
                    );
                    mStops.add(new Stop(
                            tmpObj.getString("name"),
                            tmpObj.getInt("stop_id"),
                            tmpObj.getJSONObject("location").getDouble("lat"),
                            tmpObj.getJSONObject("location").getDouble("lng"),
                            addresses.get(0).getAddressLine(0)
                    ));
                    //    Log.d("asd", "Stop found at " + addresses.get(0).getAddressLine(0));

                }

                // sort by distance
                Collections.sort(mStops, new Comparator<Stop>(){

                    @Override
                    public int compare(Stop o1, Stop o2) {
                        float[] d1 = new float[10];
                        float[] d2 = new float[10];
                        Location.distanceBetween(myLat, myLong, o1.lat, o1.lng, d1);
                        Location.distanceBetween(myLat, myLong, o2.lat, o2.lng, d2);
                        return  (new Float(d2[0])).compareTo(new Float(d1[0]));
                    }
                });

                for(Stop s : mStops) {
                    Log.d("asd", s.address);
                }


            } catch (JSONException e) {
                Log.d("asd", "Shit went south! " + e.getMessage());
                return;
            } catch (IOException e) {
                Log.d("asd", e.getMessage());
                return;
            }
        }

        @Override
        public void onRequestFailed() {
            Log.d("asd", "Failed to fetch close stops...");
        }
    }
}
