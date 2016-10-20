package com.example.handheld;

import android.util.Log;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.io.UnsupportedEncodingException;

/**
 * Created by FMZ on 10/19/16.
 */

public class PushDataToWatch extends Thread
{
    String path;
    String message;
    GoogleApiClient mClient;

    // Constructor to send a message to the data layer
    PushDataToWatch(String p, String msg, GoogleApiClient client)
    {
        Log.d("asd", "PushDataToWatch constructor");
        path = p;
        message = msg;
        mClient = client;

        if(!mClient.isConnected()){
            mClient.connect();
            Log.d("asd", "Connecting to mClient...");
        }
    }

    public void run()
    {
        NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(mClient).await();

        for (Node node : nodes.getNodes()) {
            Log.d("asd", "pushing to node " + node.getDisplayName());
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
                Log.d("asd", "Successfully pushed to the watch!");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                Log.d("asd", String.valueOf(e.getStackTrace()));
                return;
            }
            if (result.getStatus().isSuccess()) {
                Log.d("asd", "Message: {" + message + "} sent to: " + node.getDisplayName());
            }
            else {
                // Log an error
                Log.e("asd", "ERROR: failed to send Message");
            }
        }
    }

}