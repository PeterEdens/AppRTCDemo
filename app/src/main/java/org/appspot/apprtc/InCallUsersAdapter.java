package org.appspot.apprtc;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
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
import android.widget.EditText;
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
    private final InCallUsersAdapterEvents mEvents;
    private final boolean videoCallEnabled;
    String mServer = "";
    ArrayList<User> userList;
    Context mContext;

    private final int IDLE = 0;
    private final int INCALL = 1;

    /**
     * Call control interface for container activity.
     */
    public interface InCallUsersAdapterEvents {
        void onSendMessage(User user);
    }

    public InCallUsersAdapter(ArrayList<User> userList, Context context, String server, String ownId, InCallUsersAdapterEvents events, boolean videoCallEnabled) {
        this.userList = userList;
        mServer = server;
        mContext = context;
        mOwnId = ownId;
        mEvents = events;
        this.videoCallEnabled = videoCallEnabled;
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
            if (holder.hangup_button != null) {
                holder.hangup_button.setVisibility(View.INVISIBLE);
            }
            holder.actionImage.setVisibility(View.VISIBLE);
            holder.connecting.setVisibility(View.INVISIBLE);
            if (holder.time != null) {
                holder.time.setVisibility(View.INVISIBLE);
            }
            holder.toggleTimer(false);
        }
        else if (user.getCallState() == User.CallState.CALLING) {
            holder.actionImage.setVisibility(View.INVISIBLE);
            if (holder.hangup_button != null) {
                holder.hangup_button.setVisibility(View.INVISIBLE);
            }
            holder.connecting.setVisibility(View.VISIBLE);
            holder.time.setVisibility(View.INVISIBLE);
            holder.toggleTimer(false);
        }
        else if (user.getCallState() == User.CallState.CONNECTED) {
            if (holder.hangup_button != null) {
                holder.hangup_button.setVisibility(View.VISIBLE);
            }
            holder.actionImage.setVisibility(View.INVISIBLE);
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

        if (holder.toggleVideo_button != null) {
            if (!videoCallEnabled) {
                holder.toggleVideo_button.setVisibility(View.GONE);
            } else {
                holder.toggleVideo_button.setVisibility(View.VISIBLE);
            }
        }

        holder.user = user;
        holder.mEvents = mEvents;
    }


    @Override
    public int getItemCount() {
        return userList.size();
    }

    public void setData(ArrayList<User> userList) {
        this.userList = userList;
    }

    public static class UsersViewHolder extends RecyclerView.ViewHolder {

        InCallUsersAdapterEvents mEvents;
        private final ImageView actionImage;
        private SpinKitView connecting;
        /*ImageButton callButton;
                ImageButton chatButton;
                ImageButton shareFileButton;*/
        protected ImageView image;
        protected TextView text;
        protected TextView message;
        protected TextView time;
        ImageView hangup_button;
        boolean videoEnabled = true;
        ImageView toggleVideo_button;
        boolean micEnabled = true;
        ImageView toggleMic_button;
        ImageView sendMessage_button;
        ImageView shareFile_button;
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
            hangup_button = (ImageView) itemView.findViewById(R.id.button_hangup);
            toggleVideo_button = (ImageView) itemView.findViewById(R.id.button_call_toggle_video);
            toggleMic_button = (ImageView) itemView.findViewById(R.id.button_call_toggle_mic);
            sendMessage_button = (ImageView) itemView.findViewById(R.id.button_send_message);
            shareFile_button = (ImageView) itemView.findViewById(R.id.button_send_file);

            if (shareFile_button != null) {
                shareFile_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Intent intent = new Intent(view.getContext(), CallActivity.class);
                        intent.setAction(RoomActivity.ACTION_SHARE_FILE);
                        intent.putExtra(WebsocketService.EXTRA_USER, user);
                        view.getContext().startActivity(intent);
                    }
                });
            }

            if (toggleVideo_button != null) {
                toggleVideo_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        videoEnabled = !videoEnabled;

                        // toggle video
                        Intent intent = new Intent(view.getContext(), CallActivity.class);
                        intent.setAction(CallActivity.ACTION_TOGGLE_VIDEO);
                        intent.putExtra(WebsocketService.EXTRA_USER, user);
                        intent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);
                        intent.putExtra(WebsocketService.EXTRA_ID, user.Id);
                        intent.putExtra(CallActivity.EXTRA_VIDEO_CALL, videoEnabled);
                        intent.putExtra(WebsocketService.EXTRA_USERACTION, true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        view.getContext().startActivity(intent);
                        toggleVideo_button.setImageResource(videoEnabled ? R.drawable.ic_visibility_white_24dp : R.drawable.ic_visibility_off_white_24dp);
                    }
                });
            }

            if (toggleMic_button != null) {
                toggleMic_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        micEnabled = !micEnabled;

                        // toggle video
                        Intent intent = new Intent(view.getContext(), CallActivity.class);
                        intent.setAction(CallActivity.ACTION_TOGGLE_MIC);
                        intent.putExtra(WebsocketService.EXTRA_USER, user);
                        intent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);
                        intent.putExtra(WebsocketService.EXTRA_ID, user.Id);
                        intent.putExtra(CallActivity.EXTRA_MIC_ENABLED, micEnabled);
                        intent.putExtra(WebsocketService.EXTRA_USERACTION, true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        view.getContext().startActivity(intent);
                        toggleMic_button.setImageResource(micEnabled ? R.drawable.ic_mic_white_24dp : R.drawable.ic_mic_off_white_24dp);
                    }
                });
            }

            if (hangup_button != null) {
                hangup_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {

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
                });
            }

            if (sendMessage_button != null) {
                sendMessage_button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        mEvents.onSendMessage(user);

                    }
                });
            }

            actionImage.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(view.getContext(), CallActivity.class);
                    intent.setAction(CallActivity.ACTION_NEW_CALL);
                    intent.putExtra(WebsocketService.EXTRA_USER, user);
                    intent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);
                    intent.putExtra(WebsocketService.EXTRA_ID, user.Id);
                    intent.putExtra(WebsocketService.EXTRA_USERACTION, true);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    view.getContext().startActivity(intent);

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