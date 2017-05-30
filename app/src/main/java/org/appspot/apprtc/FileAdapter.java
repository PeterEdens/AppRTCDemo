package org.appspot.apprtc;

import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
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
import android.widget.Toast;


import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.w3c.dom.Text;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

import static android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION;

public class FileAdapter extends RecyclerView.Adapter<FileAdapter.FileViewHolder> {

    File[] files;
    Context mContext;

    public FileAdapter(File[] files, Context context) {
        this.files = files;
        mContext = context;
    }

    @Override
    public FileAdapter.FileViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;

        v = LayoutInflater.from(parent.getContext()).inflate(R.layout.file_row, parent, false);

        FileViewHolder viewHolder=new FileViewHolder(v);
        return viewHolder;
    }

    @Override
    public void onBindViewHolder(FileAdapter.FileViewHolder holder, int position) {

        holder.file = files[position];
       holder.displayName.setText(files[position].getName());
    }

    @Override
    public int getItemCount() {
        return files != null ? files.length : 0;
    }

    public static class FileViewHolder extends RecyclerView.ViewHolder {

        protected ImageView image;
        protected TextView time;
        protected TextView displayName;
        File file;
        Context context;

        public FileViewHolder(View itemView) {
            super(itemView);
            context = itemView.getContext();
            image= (ImageView) itemView.findViewById(R.id.image_id);
            displayName= (TextView) itemView.findViewById(R.id.text_id);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Open the file
                    String path = file.getPath();
                    ContentResolver cr = context.getContentResolver();
                    String mime = cr.getType(Uri.fromFile(file));
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
                        Toast.makeText(view.getContext(), view.getContext().getString(R.string.no_activity), Toast.LENGTH_LONG);
                    }
                }
            });

        }
    }
}