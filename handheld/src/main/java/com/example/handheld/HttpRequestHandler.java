package com.example.handheld;

/**
 * Originally created by Steph Ree for All Around Yale.
 * <p>
 * Repurposed and modified by Stan Swidwinski. Mostly cut down the functionality for readability and
 * conciseness.
 */

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class HttpRequestHandler extends AsyncTask<String, Void, HttpRequestHandler.MyResult> {

    // NEW: We have to return multiple values for some calls because the getNumAvailable/getTotal
    // return exactly the same data format.  Add a String[] so we can store multiple kinds of data.
    public class MyResult {
        public Object myObject;
        public String[] parameters;

        MyResult(Object o, String[] s) {
            myObject = o;
            parameters = s;
        }
    }

    private static final int MAX_RETRIES = 3;
    private int tries;

    private final Context mContext;
    private final String mRequestString;
    private final Map<String, String> mHeaders;
    private final Listener mListener;
    protected ProgressDialog mStatusDialog;
    private final boolean mShouldShowStatusDialog;

    public HttpRequestHandler(
            Context context,
            String requestString,
            Map<String, String> headers,
            Listener listener) {
        this(context, requestString, headers, listener, true);
    }

    public HttpRequestHandler(
            Context context,
            String requestString,
            Map<String, String> headers,
            Listener listener,
            boolean shouldShowStatusDialog) {
        Log.d("asd", "creating http request handler");
        mContext = context;
        mRequestString = requestString;
        mHeaders = headers;
        mListener = listener;
        tries = 0;
        mShouldShowStatusDialog = shouldShowStatusDialog;
        if (mShouldShowStatusDialog) {
            mStatusDialog = new ProgressDialog(mContext);
            mStatusDialog.setCancelable(false);
            mStatusDialog.setMessage("Retrieving the most recent information...");
            mStatusDialog.setTitle("Updating...");
            mStatusDialog.setIndeterminate(true);
        }
    }

    @Override
    protected void onCancelled(MyResult myResult) {
        super.onCancelled(myResult);
        if (mStatusDialog != null && mStatusDialog.isShowing()) {
            mStatusDialog.dismiss();
        }
    }

    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        // If the context isn't valid, we cannot show the mStatusDialog box
        if (mShouldShowStatusDialog && !((Activity) mContext).isFinishing())
            // make sure the activity isn't "Laundry"
            if (!mContext.getClass().getSimpleName().equals("Laundry"))
                mStatusDialog.show();
    }

    @Override
    protected MyResult doInBackground(String... params) {
        if (isOnline(mContext)) {
            Log.d("doInBackground", "Begun");
            return getHTTPResponse();
        } else {
            cancel(true);
            Log.d("HttpRequestHandler", "Offline");
            return null;
        }
    }

    @Override
    protected void onPostExecute(MyResult result) {
        super.onPostExecute(result);
        if (mStatusDialog != null && mStatusDialog.isShowing()) {
            mStatusDialog.dismiss();
        }
        if (result == null) {
            mListener.onRequestFailed();
        } else {
            mListener.onResponseFetched(result);
        }
    }

    @Override
    protected void onCancelled() {
        super.onCancelled();
        mListener.onRequestFailed();
    }

    private MyResult getHTTPResponse() {
        if (mRequestString == null) {
            Log.d("HttpRequestHandler", "invalid HTTP request string");
            return null;
        }
        try {
            URL url = new URL(mRequestString);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            for (Map.Entry<String, String> elt : mHeaders.entrySet()) {
                urlConnection.setRequestProperty(elt.getKey(), elt.getValue());
            }

            Log.d("getHTTPResponse", "Awaiting response.");
            if (urlConnection != null) {
                Uri uri = Uri.parse(mRequestString);
                String[] params = {uri.getQueryParameter("method"), uri.getQueryParameter("location")};

                try {
                    Log.d("getHTTPResponse", "attempting to return...");
                    return new MyResult(
                            new JSONObject(convertInputStreamToString(urlConnection.getInputStream())),
                            params);
                } catch (JSONException e) {
                    Log.e("JSONException", e.toString());
                    return null;
                }
            }
        } catch (IOException e) {
            Log.e("HttpRequestHandler", e.toString());

            tries++;
            if (tries >= MAX_RETRIES)
                return null;

            return this.getHTTPResponse();
        }

        // This should never be hit. Adding for compiler satisfaction.
        return null;
    }

    private String convertInputStreamToString(InputStream stream) {
        BufferedReader streamReader;
        StringBuilder responseBuilder = new StringBuilder();
        String inputStr;

        try {
            streamReader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
            while (null != (inputStr = streamReader.readLine())) {
                responseBuilder.append(inputStr);
            }
            return responseBuilder.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public interface Listener {
        public void onResponseFetched(MyResult result);

        public void onRequestFailed();
    }

    private static boolean isOnline(Context ct) {
        // Check for connectivity, return true if connected or connecting.
        ConnectivityManager cm = (ConnectivityManager) ct.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo netInfo = cm.getActiveNetworkInfo();
        return (netInfo != null && netInfo.isConnectedOrConnecting());
    }
}
