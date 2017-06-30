package org.appspot.apprtc;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
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

import com.github.ybq.android.spinkit.SpinKitView;

import org.appspot.apprtc.service.WebsocketService;

import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import static org.appspot.apprtc.RoomActivity.EXTRA_SERVER_NAME;

public class InCallUsersAdapter extends RecyclerView.Adapter<InCallUsersAdapter.UsersViewHolder> {

    private final String mOwnId;
    String mServer = "";
    ArrayList<User> userList;
    Context mContext;

    private final int IDLE = 0;
    private final int INCALL = 1;

    public InCallUsersAdapter(ArrayList<User> userList, Context context, String server, String ownId) {
        this.userList = userList;
        mServer = server;
        mContext = context;
        mOwnId = ownId;
    }

    @Override
    public InCallUsersAdapter.UsersViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {

        int layoutId = R.layout.calllist_row;
        if (viewType == IDLE) {
            layoutId = R.layout.calllist_row;
        }
        else {
            layoutId = R.layout.calllist_active_row;
        }

        View v= LayoutInflater.from(parent.getContext()).inflate(layoutId,parent,false);
        UsersViewHolder viewHolder=new UsersViewHolder(v, mOwnId);
        return viewHolder;
    }

    @Override
    public int getItemViewType(int position) {
        User user = userList.get(position);
        if (user.getCallState() == User.CallState.NONE) {
            return IDLE;
        }

        return INCALL;
    }

    @Override
    public void onBindViewHolder(InCallUsersAdapter.UsersViewHolder holder, int position) {
        User user = userList.get(position);
        String buddyPic = user.buddyPicture;
        holder.mServer = mServer;

        if (buddyPic.length() != 0) {
            String path = buddyPic.substring(4);
            String url = "https://" + mServer + RoomActivity.BUDDY_IMG_PATH + path;

            holder.image.clearColorFilter();
            ThumbnailsCacheManager.LoadImage(url, holder.image, user.displayName, true, true);
        }
        else {
            holder.image.setImageResource(R.drawable.ic_person_white_48dp);
            holder.image.setColorFilter(Color.parseColor("#ff757575"));
        }
        holder.text.setText(user.displayName);
        if (user.message != null) {
            holder.message.setText(user.message);
        }
        else {
            holder.message.setText("");
        }

        if (user.getCallState() == User.CallState.NONE) {
            holder.actionImage.setImageResource(R.drawable.ic_add_white_24dp);
            holder.actionImage.setVisibility(View.VISIBLE);
            holder.connecting.setVisibility(View.INVISIBLE);
            if (holder.time != null) {
                holder.time.setVisibility(View.INVISIBLE);
            }
            holder.toggleTimer(false);
        }
        else if (user.getCallState() == User.CallState.CALLING) {
            holder.actionImage.setVisibility(View.INVISIBLE);
            holder.connecting.setVisibility(View.VISIBLE);
            holder.time.setVisibility(View.INVISIBLE);
            holder.toggleTimer(false);
        }
        else if (user.getCallState() == User.CallState.CONNECTED) {
            holder.actionImage.setImageResource(R.drawable.ic_call_end_white_24dp);
            holder.actionImage.setVisibility(View.VISIBLE);
            holder.connecting.setVisibility(View.INVISIBLE);
            holder.time.setVisibility(View.VISIBLE);
            holder.time.setText(holder.updateTimer(System.currentTimeMillis() - user.getConnectedTime()));
            holder.toggleTimer(true);
        }
        else if (user.getCallState() == User.CallState.HOLD) {
            holder.actionImage.setImageResource(R.drawable.ic_phone_paused_black_48dp);
            holder.actionImage.setVisibility(View.VISIBLE);
            holder.connecting.setVisibility(View.INVISIBLE);
            holder.toggleTimer(false);
        }

        holder.user = user;
    }


    @Override
    public int getItemCount() {
        return userList.size();
    }

    public void setData(ArrayList<User> userList) {
        this.userList = userList;
    }

    public static class UsersViewHolder extends RecyclerView.ViewHolder {

        private final ImageView actionImage;
        private SpinKitView connecting;
        /*ImageButton callButton;
                ImageButton chatButton;
                ImageButton shareFileButton;*/
        protected ImageView image;
        protected TextView text;
        protected TextView message;
        protected TextView time;
        public User user;
        Timer timer;

        public String mServer = "";
        public String mOwnId;
        TimerTask task;

        public void toggleTimer(boolean state) {
            if (state) {
                if (timer == null) {
                    timer = new Timer();
                }

                if (task == null) {
                    task = new TimerTask() {
                        Handler handler = new Handler();

                        @Override
                        public void run() {
                            handler.post(new Runnable() {
                                @Override
                                public void run() {
                                    time.setText(updateTimer(System.currentTimeMillis() - user.getConnectedTime()));
                                }
                            });

                        }
                    };
                }
                try {
                    timer.purge();
                    timer.schedule(task, 1000, 1000);
                }
                catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
            else if (timer != null) {
                timer.cancel();
                timer = null;
                task = null;
            }
        }

        public UsersViewHolder(View itemView, String ownId) {
            super(itemView);
            image= (ImageView) itemView.findViewById(R.id.image_id);
            text= (TextView) itemView.findViewById(R.id.text_id);
            message = (TextView) itemView.findViewById(R.id.message);
            mOwnId = ownId;
            actionImage = (ImageView) itemView.findViewById(R.id.action_image);
            connecting = (SpinKitView) itemView.findViewById(R.id.connecting_progress);
            time = (TextView) itemView.findViewById(R.id.connection_time);

            itemView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (user.getCallState() == User.CallState.NONE) {
                        Intent intent = new Intent(view.getContext(), CallActivity.class);
                        intent.setAction(CallActivity.ACTION_NEW_CALL);
                        intent.putExtra(WebsocketService.EXTRA_USER, user);
                        intent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);
                        intent.putExtra(WebsocketService.EXTRA_ID, user.Id);
                        intent.putExtra(WebsocketService.EXTRA_USERACTION, true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        view.getContext().startActivity(intent);
                    }
                    else {
                        // hang up
                        Intent intent = new Intent(view.getContext(), CallActivity.class);
                        intent.setAction(CallActivity.ACTION_HANG_UP);
                        intent.putExtra(WebsocketService.EXTRA_USER, user);
                        intent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);
                        intent.putExtra(WebsocketService.EXTRA_ID, user.Id);
                        intent.putExtra(WebsocketService.EXTRA_USERACTION, true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        view.getContext().startActivity(intent);
                    }
                }
            });

        }

        public String updateTimer(long millis) {
            return String.format("%02d:%02d:%02d",
                    TimeUnit.MICROSECONDS.toHours(millis),
                    TimeUnit.MILLISECONDS.toMinutes(millis),
                    TimeUnit.MILLISECONDS.toSeconds(millis) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
            );
        }

    }
}