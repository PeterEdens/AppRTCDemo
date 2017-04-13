package org.appspot.apprtc;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.Image;
import android.os.Handler;
import android.os.Message;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;


import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

import static org.appspot.apprtc.RoomActivity.EXTRA_SERVER_NAME;

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
        holder.mServer = mServer;

        if (buddyPic.length() != 0) {
            String path = buddyPic.substring(4);
            String url = "https://" + mServer + RoomActivity.BUDDY_IMG_PATH + path;
            ThumbnailsCacheManager.LoadImage(url, holder.image);
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

        /*ImageButton callButton;
        ImageButton chatButton;
        ImageButton shareFileButton;*/
        protected ImageView image;
        protected TextView text;
        public User user;
        public String mServer = "";

        public UsersViewHolder(View itemView) {
            super(itemView);
            image= (ImageView) itemView.findViewById(R.id.image_id);
            text= (TextView) itemView.findViewById(R.id.text_id);
            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(view.getContext(), UserActivity.class);
                    intent.putExtra(EXTRA_SERVER_NAME, mServer);
                    intent.putExtra(CallActivity.EXTRA_USER, user);
                    view.getContext().startActivity(intent);
                }
            });
            /*callButton = (ImageButton) itemView.findViewById(R.id.call_button);
            chatButton = (ImageButton) itemView.findViewById(R.id.chat_button);
            shareFileButton = (ImageButton) itemView.findViewById(R.id.file_button);

            shareFileButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getId() == shareFileButton.getId()){
                        Intent intent = new Intent(v.getContext(), RoomActivity.class);
                        intent.setAction(RoomActivity.ACTION_SHARE_FILE);
                        intent.putExtra(CallActivity.EXTRA_USER, user);
                        v.getContext().startActivity(intent);
                    }
                }
            });

            callButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getId() == callButton.getId()){
                        Intent intent = new Intent(v.getContext(), CallActivity.class);
                        intent.setAction(CallActivity.ACTION_NEW_CALL);
                        intent.putExtra(ConnectActivity.EXTRA_SERVERURL, mServer);
                        intent.putExtra(CallActivity.EXTRA_USER, user);
                        v.getContext().startActivity(intent);
                    }
                }
            });

            chatButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (v.getId() == chatButton.getId()){
                        Intent intent = new Intent(v.getContext(), RoomActivity.class);
                        intent.setAction(RoomActivity.ACTION_NEW_CHAT);
                        intent.putExtra(RoomActivity.EXTRA_USER, user);
                        v.getContext().startActivity(intent);
                    }
                }
            });*/
        }

    }
}