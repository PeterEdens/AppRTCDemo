package org.appspot.apprtc;

import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.text.format.DateUtils;
import android.text.format.Formatter;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;


import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.w3c.dom.Text;

import java.io.File;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Date;
import java.util.TimeZone;

import static android.content.Context.NOTIFICATION_SERVICE;
import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ChatViewHolder> {

    private final OnChatAdapterEvents mEvents;
    String mServer = "";
    String mAvatarUrl;
    ArrayList<ChatItem> chatList;
    Context mContext;

    private final int INCOMING = 0;
    private final int OUTGOING = 1;
    public interface OnChatAdapterEvents {
        void onMessageShown();
    }

    public ChatAdapter(ArrayList<ChatItem> chatList, Context context, String server, String avatarUrl, OnChatAdapterEvents events) {
        this.chatList = chatList;
        mServer = server;
        mContext = context;
        mAvatarUrl = avatarUrl;
        mEvents = events;
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

        if (chatItem.getNotificationId() != 0) {
            NotificationManager mNotifyMgr =
                    (NotificationManager) mContext.getSystemService(NOTIFICATION_SERVICE);

            mNotifyMgr.cancel(chatItem.getNotificationId());
            chatItem.setNotificationId(0);
            mEvents.onMessageShown();
        }

        String buddyPic = chatItem.buddyPicture;
        if (buddyPic.length() != 0) {
            if (buddyPic.equals("self")) {
                ThumbnailsCacheManager.LoadImage(mAvatarUrl, holder.image, chatItem.displayName, false, true);
            }
            else {
                String path = buddyPic.substring(4);
                String url = "https://" + mServer + RoomActivity.BUDDY_IMG_PATH + path;
                ThumbnailsCacheManager.LoadImage(url, holder.image, chatItem.displayName, false, true);
            }
        }
        else {
            holder.image.setImageResource(R.drawable.user_icon);
        }

        SimpleDateFormat format = chatItem.time.endsWith("Z") ? new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'") : new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ");
        format.setTimeZone(TimeZone.getTimeZone("GMT"));
        String dateString = "";
        try {
            Date date = format.parse(chatItem.time);
            if (DateUtils.isToday(date.getTime())) {
                dateString = DateFormat.getTimeInstance().format(date);
            }
            else {
                dateString = DateFormat.getDateTimeInstance().format(date);
            }
        } catch (ParseException e) {
            e.printStackTrace();
        }
        holder.time.setText(dateString);
        holder.text.setText(chatItem.text);
        holder.chatItem = chatItem;
        holder.position = position;
        holder.ToggleViews();
    }

    @Override
    public int getItemCount() {
        return chatList.size();
    }

    public static class ChatViewHolder extends RecyclerView.ViewHolder {


        private final Button downloadButton;
        private TextView filename;
        private TextView filesize;
        protected ImageView image;
        protected TextView text;
        protected TextView time;
        public ChatItem chatItem;
        protected RelativeLayout fileLayout;
        protected  RelativeLayout downloadLayout;
        protected ProgressBar downloadProgress;
        protected TextView downloadProgressText;
        int position;
        Context context;

        public ChatViewHolder(View itemView) {
            super(itemView);
            context = itemView.getContext();
            image= (ImageView) itemView.findViewById(R.id.image_id);
            text= (TextView) itemView.findViewById(R.id.msgtext);
            time= (TextView) itemView.findViewById(R.id.msgtime);
            fileLayout = (RelativeLayout) itemView.findViewById(R.id.fileLayout);
            filename = (TextView) itemView.findViewById(R.id.name);
            filesize = (TextView) itemView.findViewById(R.id.size);
            downloadButton = (Button) itemView.findViewById(R.id.downloadButton);
            downloadLayout = (RelativeLayout) itemView.findViewById(R.id.downloadLayout);
            downloadProgress = (ProgressBar) itemView.findViewById(R.id.downloadProgress);
            downloadProgressText = (TextView) itemView.findViewById(R.id.downloadProgressText);

            downloadButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (chatItem.outgoing) {
                        downloadButton.setVisibility(View.GONE);
                    }
                    else if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.IDLE) {

                        Intent intent = new Intent(v.getContext(), RoomActivity.class);
                        intent.setAction(RoomActivity.ACTION_DOWNLOAD);
                        intent.putExtra(WebsocketService.EXTRA_FILEINFO, chatItem.fileinfo);
                        intent.putExtra(RoomActivity.EXTRA_TO, chatItem.Id);
                        intent.putExtra(RoomActivity.EXTRA_INDEX, position);
                        v.getContext().startActivity(intent);
                        downloadButton.setText(R.string.cancel);
                        downloadLayout.setVisibility(View.VISIBLE);
                        filesize.setVisibility(View.GONE);
                        downloadProgress.setMax(100);
                        Formatter formatter = new Formatter();
                        long sizeBytes = Long.valueOf(chatItem.fileinfo.size);
                        downloadProgressText.setText(formatter.formatFileSize(v.getContext(), sizeBytes) + " / " + chatItem.fileinfo.percentDownloaded + "%");
                        chatItem.fileinfo.setDownloadState(FileInfo.DownloadState.DOWNLOADING);
                    }
                    else if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.DOWNLOADING ||
                            chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.FAILED){
                        Intent intent = new Intent(v.getContext(), RoomActivity.class);
                        intent.setAction(RoomActivity.ACTION_CANCEL_DOWNLOAD);
                        intent.putExtra(WebsocketService.EXTRA_FILEINFO, chatItem.fileinfo);
                        intent.putExtra(RoomActivity.EXTRA_TO, chatItem.Id);
                        v.getContext().startActivity(intent);
                        downloadButton.setText(R.string.download);
                        downloadLayout.setVisibility(View.GONE);
                        filesize.setVisibility(View.VISIBLE);
                        chatItem.fileinfo.setDownloadState(FileInfo.DownloadState.IDLE);
                    }
                    else if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.COMPLETED) {
                        // Open the file
                        String mime = chatItem.fileinfo.type;
                        File file = new File(chatItem.getDownloadPath());
                        Intent intent = new Intent();
                        intent.setAction(android.content.Intent.ACTION_VIEW);
                        Uri fileURI = FileProvider.getUriForFile(context, "spreedbox.me.app.files", file);
                        intent.addFlags(FLAG_GRANT_READ_URI_PERMISSION);
                        intent.setDataAndType(fileURI, mime);
                        try {
                            context.startActivity(intent);
                        }
                        catch (ActivityNotFoundException e) {
                            e.printStackTrace();
                            Toast.makeText(context, R.string.no_activity, Toast.LENGTH_LONG);
                        }
                    }
                }
            });

        }

        public void ToggleViews() {
            if (chatItem.fileinfo == null) {
                fileLayout.setVisibility(View.GONE);
            }
            else {
                text.setVisibility(View.GONE);
                filename.setText(chatItem.fileinfo.name);
                Formatter formatter = new Formatter();
                long sizeBytes = Long.valueOf(chatItem.fileinfo.size);
                String formattedSize = formatter.formatFileSize(context, sizeBytes);
                downloadProgressText.setText(formattedSize + " / " + chatItem.fileinfo.percentDownloaded + "%");
                filesize.setText(formattedSize);
                downloadProgress.setProgress(chatItem.fileinfo.percentDownloaded);

                if (chatItem.outgoing) {
                    downloadButton.setText(R.string.unshare);
                }
                else if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.IDLE) {
                    downloadButton.setText(R.string.download);
                }
                else if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.DOWNLOADING) {
                    downloadButton.setText(R.string.cancel);
                }
                else if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.COMPLETED) {
                    downloadButton.setText(R.string.open);
                    downloadLayout.setVisibility(View.GONE);
                    filesize.setVisibility(View.VISIBLE);
                }
                else if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.FAILED) {
                    downloadButton.setText(R.string.retry);
                    downloadLayout.setVisibility(View.GONE);
                    filesize.setVisibility(View.VISIBLE);
                    filesize.setText(chatItem.fileinfo.errorMessage);
                }
            }
        }
    }
}