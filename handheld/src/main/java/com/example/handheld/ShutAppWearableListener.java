package com.example.handheld;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;
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
import java.util.Vector;

/**
 * Created by stan on 10/18/16.
 */

public class ShutAppWearableListener extends WearableListenerService implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    // a hardcoded map between route IDs and colors, to avoid unnecessary api calls
    //
    // TODO:
    //     Transfer this to assets (color map). Leave for now.
    private static final HashMap<Integer, Integer> routeToColor;
    static {
        routeToColor = new HashMap<Integer, Integer>();
        routeToColor.put(4003414, 0x6d8dbf);
        routeToColor.put(4004306, 0xffff00);
        routeToColor.put(4005230, 0x11e5e5);
        routeToColor.put(4000342, 0xcc0000);
        routeToColor.put(4000346, 0x007ec5);
        routeToColor.put(4000350, 0xff0000);
        routeToColor.put(4000354, 0xcc33cc);
        routeToColor.put(4000366, 0xff9900);
        routeToColor.put(4000370, 0x007ec5);
        routeToColor.put(4000374, 0xff6699);
        routeToColor.put(4000378, 0xff9900);
        routeToColor.put(4000382, 0x660066);
        routeToColor.put(4000386, 0x54bd00);
        routeToColor.put(4000390, 0xff9900);
        routeToColor.put(4000394, 0x0000ff);
        routeToColor.put(4000398, 0xff9900);
        routeToColor.put(4003418, 0x0000ff);
        routeToColor.put(4004382, 0xf36e21);
        routeToColor.put(4005002, 0x0000ff);
        routeToColor.put(4006518, 0xe9ff00);
        routeToColor.put(4007474, 0xf7adc8);
        routeToColor.put(4007666, 0xffff00);
    }

    // a hardcoded set of headers necessary for transloc API
    private static HashMap<String, String> translocAPIHeaders = new HashMap<String, String>();

    private static final String YALE_AGENCY_ID = "128";

    double myLong;
    double myLat;
    // GoogleApiClient
    private GoogleApiClient mApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        translocAPIHeaders.put("X-Mashape-Key", getResources().getString(R.string.x_mashape_key));
        translocAPIHeaders.put("Accept", "application/json");

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    // fired when a message from the watch has bene received.
    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        String event = messageEvent.getPath();
        Log.d("onMessageReceived", "received message with event: " + event);

        // only react to transloc data requests!
        if (event.equals("/get_transloc_data")) {
            String locationData;
            try {
                locationData = new String(messageEvent.getData(), "UTF8");
            } catch (UnsupportedEncodingException e) {
                Log.e("onMessageReceived", e.getMessage());
                return;
            }

            Uri.Builder builder = new Uri.Builder();
            // TODO:
            //      Parametrize this and avoid evil string hacking.
            myLong = Float.valueOf(locationData.split(",")[0]);
            myLat = Float.valueOf(locationData.split(",")[1].split("\\|")[0]);
            builder.scheme("https")
                    .authority("transloc-api-1-2.p.mashape.com")
                    .appendPath("stops.json")
                    .appendQueryParameter("agencies", YALE_AGENCY_ID)
                    .appendQueryParameter("geo_area", locationData);

            // execute the http api query
            Log.d("onMessageReceived", "Querying with the url: " + builder.toString());
            HttpRequestHandler reqHandler = new HttpRequestHandler(
                    this,
                    builder.toString(),
                    translocAPIHeaders,
                    new StopsFetchedListener(),
                    false);
            reqHandler.execute();
        } else {
            Log.e("onMessageReceived", "unrecognized event received.");
        }
    }

    // Get a string of routes, and call
    private void callVehiclesApi(String routes, int stopID, String address) {
        Uri.Builder builder = new Uri.Builder();
        // TODO: Parametrize this.
        //       For now everything is literally hardcoded.
        // Prepare the URI  and headers
        builder.scheme("https")
                .authority("transloc-api-1-2.p.mashape.com")
                .appendPath("vehicles.json")
                .appendQueryParameter("agencies", YALE_AGENCY_ID)
                .appendQueryParameter("routes", routes);

        // execute the http api query
        Log.d("callVehiclesApi", "Querying with the url: " + builder.toString());
        HttpRequestHandler reqHandler = new HttpRequestHandler(
                this,
                builder.toString(),
                translocAPIHeaders,
                new VehiclesFetchedListener(stopID, address),
                false);
        reqHandler.execute();
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d("onConnected", "I'm connected! #cloud");
        // Note that this does not do anything. We have no additional listeners to register.
    }

    @Override
    public void onConnectionSuspended(int i) {
        // TODO -- if we want to be robust.
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.e("asd", "onConnectionFailed: " + connectionResult);
    }

    private void sendResponseToWatch(String response) {
        (new PushDataToWatch("/push_shuttle_info", response, mApiClient)).start();
    }

    // Handler for received stops response.
    private class StopsFetchedListener implements HttpRequestHandler.Listener {

        private Vector<Stop> mStops = new Vector<>();

        private class Stop {
            public int index;   // index into json object
            public String name;
            public int stopID;
            public double lat;
            public double lng;
            public String address;

            Stop(int index, String n, int c, double la, double ln, String addr) {
                this.index = index;
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
                Log.d("StopsFetchedListener", "Fetched " + stopInfo.length() + " stops!");

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
                            i,
                            tmpObj.getString("name"),
                            tmpObj.getInt("stop_id"),
                            tmpObj.getJSONObject("location").getDouble("lat"),
                            tmpObj.getJSONObject("location").getDouble("lng"),
                            addresses.get(0).getAddressLine(0)
                    ));
                }

                // signal to the watch that no stops have been found
                if (mStops.size() == 0) {
                    Log.e("StopsFetchedListener", "No Nearby Stops Found");
                    sendResponseToWatch("ERROR: No nearby stops!");
                    return;
                }

                // sort by distance -- increasingly
                Collections.sort(mStops, new Comparator<Stop>() {

                    @Override
                    public int compare(Stop o1, Stop o2) {
                        Location me = new Location("me");
                        me.setLatitude(myLat);
                        me.setLongitude(myLong);

                        Location s1 = new Location("stop1");
                        s1.setLatitude(o1.lat);
                        s1.setLongitude(o1.lng);

                        Location s2 = new Location("stop2");
                        s2.setLatitude(o2.lat);
                        s2.setLongitude(o2.lng);

                        return (new Double(me.distanceTo(s2))).compareTo(new Double(me.distanceTo(s1)));
                    }
                });
                Log.d("asd", myLat + " : " + myLong);

                for (Stop s:mStops)
                        Log.d("asd", s.address);

                // get all route numbers for the nearest stop
                Stop closest = mStops.get(0);
                tmpObj = stopInfo.getJSONObject(closest.index);
                JSONArray routeIDs = tmpObj.getJSONArray("routes");

                // parse the JSON array, into a string
                if (routeIDs == null)
                    throw (new JSONException("No routes!"));

                // ecode the data into a string.
                String routes = routeIDs.getString(0);
                for (int i = 1; i < routeIDs.length(); i++)
                    routes += "," + routeIDs.getString(i);

                // perform call
                callVehiclesApi(routes, closest.stopID, closest.address);
            } catch (JSONException e) {
                Log.e("StopsFetchedListener", e.getMessage());
                return;
            } catch (IOException e) {
                Log.e("StopsFetchedListener", e.getMessage());
                return;
            }
        }

        @Override
        public void onRequestFailed() {
            Log.e("StopsFetchedListener", "Failed to fetch close stops...");
        }
    }


    // This class will fetch a vehicles JSON object, get vehicle info for a particular stop,
    // and send the results back to the watch
    private class VehiclesFetchedListener implements HttpRequestHandler.Listener {
        // The vehicles call will get all stops for each vehicle, so we need to keep
        // track of a single stop
        int stopID;
        String address;

        public VehiclesFetchedListener(int stopID, String address) {
            super();
            this.stopID = stopID;
            this.address = address;
        }

        @Override
        public void onResponseFetched(HttpRequestHandler.MyResult result) {
            try {
                JSONArray vehicleInfo = ((JSONObject) result.myObject)
                        .getJSONObject("data")
                        .getJSONArray(YALE_AGENCY_ID);
                Log.d("VehiclesFetchedListener", "Fetched " + vehicleInfo.length() + " vehicles!");

                // string to be returned to watch
                String responseToWatch = this.address;

                JSONObject vehicle;
                JSONArray arrivalEstimates;
                JSONObject tmpEstimate;
                int tmpRouteID;

                // this is an ugly loop, it'll be an exhaustive search basically
                for (int i = 0; i < vehicleInfo.length(); i++) {
                    vehicle = vehicleInfo.getJSONObject(i);
                    arrivalEstimates = vehicle.getJSONArray("arrival_estimates");

                    // loop through arrival estimates, and look for stopid
                    for (int j = 0; j < arrivalEstimates.length(); j++) {
                        tmpEstimate = arrivalEstimates.getJSONObject(j);

                        // only do anything useful if the stop id is our stop id
                        if (tmpEstimate.getInt("stop_id") == this.stopID) {
                            // each entry looks like <hexColor,dateTime> with | separators
                            tmpRouteID = tmpEstimate.getInt("route_id");
                            responseToWatch += "|" + routeToColor.get(tmpRouteID) + ",";
                            responseToWatch += tmpEstimate.getString("arrival_at");
                        }
                    }
                }

                Log.d("VehiclesFetchedListener", responseToWatch);
                sendResponseToWatch(responseToWatch);

            } catch (JSONException e) {
                Log.e("VehiclesFetchedListener", e.getMessage());
                sendResponseToWatch("ERROR: No shuttles at this time!");
                return;
            }
        }

        @Override
        public void onRequestFailed() {
            Log.d("VehiclesFetchedListener", "Failed to fetch vehichle info!!!");
        }
    }
}
