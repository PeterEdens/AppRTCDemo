package org.appspot.apprtc;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;


import java.util.ArrayList;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UsersViewHolder> {

    String mServer = "";
    ArrayList<User> userList;
    Context mContext;

    public UsersAdapter(ArrayList<User> userList, Context context, String server) {
        this.userList = userList;
        mServer = server;
        mContext = context;
    }

    @Override
    public UsersAdapter.UsersViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v= LayoutInflater.from(parent.getContext()).inflate(R.layout.user_row,parent,false);
        UsersViewHolder viewHolder=new UsersViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(UsersAdapter.UsersViewHolder holder, int position) {
        String buddyPic = userList.get(position).buddyPicture;
        if (buddyPic.length() != 0) {
            String path = buddyPic.substring(4);
            String url = "https://" + mServer + RoomActivity.BUDDY_IMG_PATH + path;
        }
        else {
            holder.image.setImageResource(R.drawable.user_icon);
        }
        holder.text.setText(userList.get(position).displayName);
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UsersViewHolder extends RecyclerView.ViewHolder{

        protected ImageView image;
        protected TextView text;

        public UsersViewHolder(View itemView) {
            super(itemView);
            image= (ImageView) itemView.findViewById(R.id.image_id);
            text= (TextView) itemView.findViewById(R.id.text_id);
        }
    }
}