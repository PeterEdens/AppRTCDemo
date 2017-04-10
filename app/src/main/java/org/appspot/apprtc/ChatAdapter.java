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

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

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
            ThumbnailsCacheManager.LoadImage(url, holder.image);
        }
        else {
            holder.image.setImageResource(R.drawable.user_icon);
        }
        holder.time.setText(chatItem.time);
        holder.displayName.setText(chatItem.displayName);
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
        protected TextView displayName;
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
            displayName= (TextView) itemView.findViewById(R.id.displayName);
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
                    if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.IDLE) {
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
                        context.startActivity(intent);
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
                filesize.setText(chatItem.fileinfo.size);
                Formatter formatter = new Formatter();
                long sizeBytes = Long.valueOf(chatItem.fileinfo.size);
                downloadProgressText.setText(formatter.formatFileSize(context, sizeBytes) + " / " + chatItem.fileinfo.percentDownloaded + "%");
                downloadProgress.setProgress(chatItem.fileinfo.percentDownloaded);

                if (chatItem.fileinfo.getDownloadState() == FileInfo.DownloadState.IDLE) {
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