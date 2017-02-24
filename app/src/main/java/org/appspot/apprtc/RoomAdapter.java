package org.appspot.apprtc;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class RoomAdapter extends ArrayAdapter<String> {
    Context mContext;

    public RoomAdapter(Context context, int layout_id, ArrayList<String> items){
        super(context, layout_id, items);
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent){
        convertView = super.getView(position, convertView, parent);
        // Get the data item for this position
        String name = getItem(position);

        // Lookup view for data population
        TextView text1 = (TextView) convertView.findViewById(android.R.id.text1);
        // Populate the data into the template view using the data object
        if (name.length() == 0) {
            name = mContext.getString(R.string.default_room);
        }
        text1.setText(name);

        return convertView;
    }
}