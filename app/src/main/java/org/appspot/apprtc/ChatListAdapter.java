package org.appspot.apprtc;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.RecyclerView;
import android.text.format.Formatter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;


import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ChatListViewHolder> {

    String mServer = "";
    HashMap<String, ArrayList<ChatItem>> chatList;
    HashMap<String, User> userIdList;
    Context mContext;
    String mRoomName;

    public ChatListAdapter(HashMap<String, ArrayList<ChatItem>> chatList, HashMap<String, User> userIdList, Context context, String server, String roomName) {
        this.chatList = chatList;
        this.userIdList = userIdList;
        mServer = server;
        mContext = context;
        mRoomName = roomName;
    }

    @Override
    public ChatListAdapter.ChatListViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;

        v = LayoutInflater.from(parent.getContext()).inflate(R.layout.chatlist_row, parent, false);

        ChatListViewHolder viewHolder=new ChatListViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(ChatListAdapter.ChatListViewHolder holder, int position) {

        String key = (String)chatList.keySet().toArray()[position];
        User user = userIdList.get(key);

        if (user != null) {
            String buddyPic = user.buddyPicture;
            if (buddyPic.length() != 0) {
                String path = buddyPic.substring(4);
                String url = "https://" + mServer + RoomActivity.BUDDY_IMG_PATH + path;
                ThumbnailsCacheManager.LoadImage(url, holder.image);
            } else {
                holder.image.setImageResource(R.drawable.user_icon);
            }/*
            holder.time.setText(chatItem.time);
            holder.displayName.setText(chatItem.displayName);
            holder.text.setText(chatItem.text);
            holder.chatItem = chatItem;
            holder.position = position;
            holder.ToggleViews();*/
            holder.key = key;
            holder.displayName.setText(user.displayName);
        }
        else {
            // room chat
            holder.key = "";
            holder.displayName.setText(mRoomName);
        }
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ChatListViewHolder extends RecyclerView.ViewHolder {

        protected ImageView image;
        protected TextView time;
        protected TextView displayName;
        String key;
        Context context;

        public ChatListViewHolder(View itemView) {
            super(itemView);
            context = itemView.getContext();
            image= (ImageView) itemView.findViewById(R.id.image_id);
            time= (TextView) itemView.findViewById(R.id.msgtime);
            displayName= (TextView) itemView.findViewById(R.id.text_id);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(context, RoomActivity.class);
                    intent.setAction(RoomActivity.ACTION_VIEW_CHAT);
                    intent.putExtra(RoomActivity.EXTRA_CHAT_ID, key);
                    context.startActivity(intent);
                }
            });

        }
    }
}