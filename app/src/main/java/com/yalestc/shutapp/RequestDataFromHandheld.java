package com.yalestc.shutapp;

import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.UnsupportedEncodingException;

/**
 * Created by stan on 10/18/16.
 */

public class RequestDataFromHandheld extends Thread {
    String path;
    String message;
    GoogleApiClient mClient;

    // Constructor to send a message to the data layer
    RequestDataFromHandheld(String p, String msg, GoogleApiClient client) {
        path = p;
        message = msg;
        mClient = client;
    }

    public void run() {
        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mClient).await();

        for (Node node : nodes.getNodes()) {
            MessageApi.SendMessageResult result =
                    null;
            try {
                result = Wearable.MessageApi
                        .sendMessage(
                                mClient,
                                node.getId(),
                                path,
                                message.getBytes("UTF8"))
                        .await();
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.d("RequestDataFromHandheld", String.valueOf(e.getStackTrace()));
                return;
            }
            if (result.getStatus().isSuccess()) {
                Log.d("RequestDataFromHandheld", "Message: {" + message + "} sent to: " + node.getDisplayName());
            }
            else {
                // Log an error
                Log.e("RequestDataFromHandheld", "ERROR: failed to send Message");
            }
        }
    }
}