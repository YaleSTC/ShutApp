package com.yalestc.shutapp;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.wearable.view.BoxInsetLayout;
import android.support.wearable.view.WatchViewStub;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.Manifest;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Result;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Wearable;

import java.io.UnsupportedEncodingException;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener,
        LocationListener {

    // Necessary for the unified google services.
    private GoogleApiClient mApiClient;
    private TextView initialText;
    private TextView GPSStatusText;
    private BoxInsetLayout stopListContainer;
    private RelativeLayout initialLayout;

    // Sample dataset for the list
    private String[] elements = {"List Item 1", "List Item 2", "List Item 3"};
    private String[] colors = {"#ffffff", "#ffffff", "#ffffff"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("onCreate", "onCreate");
        // Set layout
        setContentView(R.layout.activity_main);
        final WatchViewStub stub = (WatchViewStub) findViewById(R.id.watch_view_stub);
        stub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
            @Override
            public void onLayoutInflated(WatchViewStub stub) {
                Log.d("onCreate", "onLayoutInflated");
                initialLayout = (RelativeLayout) stub.findViewById(R.id.pre_loading_stuff);
                initialText = (TextView) stub.findViewById(R.id.text);
                GPSStatusText = (TextView) stub.findViewById(R.id.gps_status);

                // Get the list component from the layout of the activity
                WearableListView mListView = (WearableListView) stub.findViewById(R.id.wearable_list);

                // Assign an adapter to the list
                mListView.setAdapter(new Adapter(getApplicationContext(), elements, colors));
                stopListContainer = (BoxInsetLayout) stub.findViewById(R.id.outer_list_view);
                Log.d("onCreate", "onLayoutInflated finished");
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
        // request an update every 15 seconds.
        LocationRequest lr = LocationRequest.create()
                .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
                .setInterval(15000);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION},
                    200);
            Log.d("onConnect", "Permissions requested!");
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
                            GPSStatusText.setText("ON");
                            GPSStatusText.setTextColor(Color.GREEN);
                            Log.d("onConnected", "GPS service connected.");
                        } else {
                            initialText.setText("Cannot fetch your location. Will try again!");
                            Log.e("onConnected", "GPS service could not be connected/");
                        }
                    }
                });

        // register a listener
        Wearable.MessageApi.addListener(mApiClient, new WearableListener());
    }

    @Override
    public void onConnectionSuspended(int i) {
        // TODO
        //
        // Could await for the connection to be restored or ask the user to panic.
    }

    @Override
    protected void onResume() {
        super.onResume();
        mApiClient.connect();
    }

    private void cleanUp() {
        if (mApiClient.isConnected()) {
            LocationServices.FusedLocationApi
                    .removeLocationUpdates(mApiClient, this);
        }
        mApiClient.disconnect();
    }

    @Override
    protected void onPause() {
        super.onPause();
        cleanUp();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cleanUp();
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        // TODO
        //
        // As before, if we want to handle this add code!
    }

    // Called automatically by the unified google api client when location has been fetched and
    // changed.
    @Override
    public void onLocationChanged(Location location) {
        initialText.setText("Your location has been found. Fetching Shuttle info...");

        // Send a message to the phone requesting data from the translock api.
        // Quick and dirty.
        String locationInfo =
                location.getLatitude() +
                        "," +
                        location.getLongitude() +
                        "|" +
                        "500";
        (new RequestDataFromHandheld("/get_transloc_data", locationInfo, mApiClient)).start();
    }

    // wearable listener for message API
    private class WearableListener implements MessageApi.MessageListener {
        @Override
        public void onMessageReceived(MessageEvent messageEvent) {
            String event = messageEvent.getPath();
            Log.d("WearableListener", "received message with event: " + event);

            if (event.equals("/push_shuttle_info")) {
                String shuttleData;
                try {
                    shuttleData = new String(messageEvent.getData(), "UTF8");
                } catch (UnsupportedEncodingException e) {
                    Log.e("WearableListener", "Unsupported encoding");
                    return;
                }

                // TODO: parse info
                initialLayout.setVisibility(View.GONE);
                stopListContainer.setVisibility(View.VISIBLE);
                // TODO set stuff  here
                initialText.setText(shuttleData);
            }
        }
    }
}
