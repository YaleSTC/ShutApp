package com.yalestc.shutapp;

import android.content.Context;
import android.graphics.Color;
import android.support.wearable.view.WearableListView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

/**
 * Created by Katherine on 10/19/16.
 */

public final class ShuttleListAdapter extends WearableListView.Adapter {
    private List<String> mTimes;
    private List<Integer> mColors;
    private final Context mContext;
    private final LayoutInflater mInflater;

    // Provide a suitable constructor (depends on the kind of dataset)
    public ShuttleListAdapter(Context context, List<String> times, List<Integer> colors) {
        mContext = context;
        mInflater = LayoutInflater.from(context);
        mTimes = times;
        mColors = colors;
    }

    // update the info (to avoid constructing a new Adapter)
    public void updateData(List<String> times, List<Integer> colors)
    {
        Log.d("WearableListener", "colors :" + colors);
        Log.d("WearableListener", "times :" + times);

        mTimes = times;
        mColors = colors;
        notifyDataSetChanged();
    }

    // Provide a reference to the type of views you're using
    public static class ItemViewHolder extends WearableListView.ViewHolder {
        private TextView textView;
        public ItemViewHolder(View itemView) {
            super(itemView);
            // find the text view within the custom item's layout
            textView = (TextView) itemView.findViewById(R.id.name);
        }
    }

    // Create new views for list items
    // (invoked by the WearableListView's layout manager)
    @Override
    public WearableListView.ViewHolder onCreateViewHolder(ViewGroup parent,
                                                          int viewType) {
        // Inflate our custom layout for list items
        return new ItemViewHolder(mInflater.inflate(R.layout.list_item, null));
    }

    // Replace the contents of a list item
    // Instead of creating new views, the list tries to recycle existing ones
    // (invoked by the WearableListView's layout manager)
    @Override
    public void onBindViewHolder(WearableListView.ViewHolder holder,
                                 int position)
    {
        if (mColors == null || mTimes == null)
            return;

        Log.d("ShuttleListAdapter", "Drawing " + position + " th element");
        // retrieve the text view
        ItemViewHolder itemHolder = (ItemViewHolder) holder;
        TextView view = itemHolder.textView;
        // replace text contents
        view.setText(mTimes.get(position));

        Log.d("asd", Integer.toHexString(mColors.get(position)));
        view.setTextColor(Color.parseColor("#" + Integer.toHexString(mColors.get(position))));
        Log.d("asd", "#" + Integer.toHexString(mColors.get(position)));
        // replace list item's metadata
        holder.itemView.setTag(position);
    }

    // Return the size of your dataset
    // (invoked by the WearableListView's layout manager)
    @Override
    public int getItemCount()
    {
        if (mTimes == null)
            return 0;

        return mTimes.size();
    }
}
