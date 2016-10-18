package com.yalestc.shutapp;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.wearable.view.WatchViewStub;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.Wearable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    // Necessary for the unified google services.
    private GoogleApiClient mApiClient;
    private TextView mTextView;
    private ImageView gpsStatusCircle;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                mTextView = (TextView) stub.findViewById(R.id.text);
                gpsStatusCircle = (ImageView) stub.findViewById(R.id.gps_status_circle);
            }
        });

        mApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
    }

    // Called automatically by mApiClient when it connects to the google services.
    @Override
    public void onConnected(Bundle bundle) {
        // request an update every 2 seconds.
        LocationRequest lr = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(2000);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }

        // register the request and the inline the callback definition.
        LocationServices.FusedLocationApi
                .requestLocationUpdates(mApiClient, lr, this)
                .setResultCallback(new ResultCallback() {
                    @Override
                    public void onResult(@NonNull Result result) {
                        Status status = result.getStatus();
                        if (status.isSuccess()) {
                            // TODO: make this UI work. No clue why it doesn't draw lol.
                            ShapeDrawable circle = new ShapeDrawable(new OvalShape());
                            circle.getPaint().setColor(Color.GREEN);
                            circle.getShape().resize(50, 50);
                            ((ImageView) findViewById(R.id.gps_status_circle)).setImageDrawable(circle);
                            mTextView.setText("GPS connected.");
                            Log.e("asD", "GPS service connected.");
                        } else {
                            mTextView.setText("Cannot fetch your location. Will try again!");
                            Log.e("asd", "GPS service could not be connected/");
                        }
                    }
                });
    }

    @Override
    public void onConnectionSuspended(int i) {
        // TODO
    }

    @Override
    protected void onResume() {
        super.onResume();
        mApiClient.connect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mApiClient, this);
        }
        mApiClient.disconnect();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // TODO
    }

    // Called automatically by the unified google api client when location has been fetched and
    // changed.
    @Override
    public void onLocationChanged(Location location) {
        mTextView.setText("Your location has been found. Fetching Shuttle info...");

        Uri.Builder builder = new Uri.Builder();
        // TODO: Parametrize this. For now everything is literally hardcoded.
        // Prepare the URI  and headers
        builder.scheme("http")
                .authority("transloc-api-1-2.p.mashape.com")
                .appendPath("stops.json")
                .appendQueryParameter("agencies", "128")
                .appendQueryParameter("geo_area",
                        location.getLongitude() +
                                "," +
                                location.getLatitude() +
                                "|" +
                                "500"
                );

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("X-Mashape-Key", "fz7Q6hHUCXmshuArB2putNEJEWoup10QP7sjsnCuGdQZDKdGPg");
        headers.put("Accept", "application/json");

        Log.e("asd", "Querying " + builder.toString());

        // execute the http api query
        HttpRequestHandler reqHandler = new HttpRequestHandler(
                this,
                builder.toString(),
                headers,
                new stopsFetchedListener(),
                false);
        reqHandler.execute();
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
                if (stopInfo.length() == 0) {
                    mTextView.setText("It seems there are no stops close to you!");
                    return;
                } else {
                    mTextView.setText("We've found " + stopInfo.length() + " stops close to you!");
                }

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

            } catch (JSONException e) {
                e.printStackTrace();
                // TODO: change this to something more presentable.
                mTextView.setText("Shit went wrong...");
                return;
            }
        }

        @Override
        public void onRequestFailed() {
            mTextView.setText("Failed to fetch close stops...");
        }
    }
}
