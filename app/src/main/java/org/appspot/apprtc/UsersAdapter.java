package org.appspot.apprtc;

import android.content.Context;
import android.content.Intent;
import android.media.Image;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


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
        User user = userList.get(position);
        String buddyPic = user.buddyPicture;
        if (buddyPic.length() != 0) {
            String path = buddyPic.substring(4);
            String url = "https://" + mServer + RoomActivity.BUDDY_IMG_PATH + path;
        }
        else {
            holder.image.setImageResource(R.drawable.user_icon);
        }
        holder.text.setText(user.displayName);
        holder.user = user;
    }

    @Override
    public int getItemCount() {
        return userList.size();
    }

    public static class UsersViewHolder extends RecyclerView.ViewHolder {

        ImageButton callButton;
        protected ImageView image;
        protected TextView text;
        public User user;

        public UsersViewHolder(View itemView) {
            super(itemView);
            image= (ImageView) itemView.findViewById(R.id.image_id);
            text= (TextView) itemView.findViewById(R.id.text_id);
            callButton = (ImageButton) itemView.findViewById(R.id.call_button);

            callButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getId() == callButton.getId()){
                        Intent intent = new Intent(v.getContext(), CallActivity.class);
                        intent.setAction(CallActivity.ACTION_NEW_CALL);
                        intent.putExtra(CallActivity.EXTRA_USER, user);
                        v.getContext().startActivity(intent);
                    }
                }
            });
        }

    }
}