package org.appspot.apprtc;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;


import org.w3c.dom.Text;

import java.util.ArrayList;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    String mServer = "";
    ArrayList<ChatItem> chatList;
    Context mContext;

    private final int INCOMING = 0;
    private final int OUTGOING = 1;

    public ChatAdapter(ArrayList<ChatItem> chatList, Context context, String server) {
        this.chatList = chatList;
        mServer = server;
        mContext = context;
    }

    @Override
    public ChatAdapter.ChatViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;

        if (viewType == OUTGOING) {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.outgoing_chat_row, parent, false);
        }
        else {
            v = LayoutInflater.from(parent.getContext()).inflate(R.layout.incoming_chat_row, parent, false);
        }
        ChatViewHolder viewHolder=new ChatViewHolder(v);
        return viewHolder;
    }

    @Override
    public int getItemViewType(int position) {
        ChatItem item = chatList.get(position);
        if (item.outgoing) {
            return OUTGOING;
        }

        return INCOMING;
    }
    @Override
    public void onBindViewHolder(ChatAdapter.ChatViewHolder holder, int position) {
        ChatItem chatItem = chatList.get(position);
        String buddyPic = chatItem.buddyPicture;
        if (buddyPic.length() != 0) {
            String path = buddyPic.substring(4);
            String url = "https://" + mServer + RoomActivity.BUDDY_IMG_PATH + path;
        }
        else {
            holder.image.setImageResource(R.drawable.user_icon);
        }
        holder.time.setText(chatItem.time);
        holder.displayName.setText(chatItem.displayName);
        holder.text.setText(chatItem.text);
        holder.chatItem = chatItem;
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {

        ImageButton callButton;
        protected ImageView image;
        protected TextView text;
        protected TextView time;
        protected TextView displayName;
        public ChatItem chatItem;

        public ChatViewHolder(View itemView) {
            super(itemView);
            image= (ImageView) itemView.findViewById(R.id.image_id);
            text= (TextView) itemView.findViewById(R.id.msgtext);
            time= (TextView) itemView.findViewById(R.id.msgtime);
            displayName= (TextView) itemView.findViewById(R.id.displayName);


        }

    }
}