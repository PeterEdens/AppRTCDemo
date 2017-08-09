/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.res.Configuration;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.telecom.Call;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.example.sharedresourceslib.BroadcastTypes;
import com.github.ybq.android.spinkit.SpinKitView;

import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.sound.SoundPlayer;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.webrtc.RendererCommon.ScalingType;

import static org.appspot.apprtc.CallActivity.EXTRA_VIDEO_CALL;

/**
 * Fragment for call control.
 */
public class InitiateCallFragment extends Fragment {
    private View controlView;
    private TextView contactView;
    private ImageView disconnectButton;
    private ImageView connectButton;
    private ScalingType scalingType;
    private boolean videoCallEnabled = true;
    private ImageView contactImageView;
    private boolean incomingCall = true;
    private SoundPlayer mSoundPlayer;
    private Vibrator mVibrator;
    private OnInitiateCallEvents callEvents;
    private Context mContext;
    private boolean conferenceCall;
    private User mUser;
    private SpinKitView connectingProgress;
    private ImageView audioCallBackground;

    public void enableCallButtons() {
        if (incomingCall || conferenceCall) {
            connectButton.setVisibility(View.VISIBLE);
        }
        disconnectButton.setVisibility(View.VISIBLE);
        connectingProgress.setVisibility(View.GONE);
    }

    public void showPickupTimeout(User user) {
        Toast.makeText(mContext, user.displayName + " does not pickup", Toast.LENGTH_LONG).show();
    }

    public interface OnInitiateCallEvents {
        void onRejectCall();
        void onAnswerCall();
        void onStartCall();
        void onStartConferenceCall(User user);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewGroup container = (ViewGroup)getView();
        container.removeAllViews();
        LayoutInflater inflater = (LayoutInflater)getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View newView = inflater.inflate(R.layout.fragment_initiate_call, container, false);
        setupViews(newView);
        container.addView(newView);
        populateViews();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        controlView = inflater.inflate(R.layout.fragment_initiate_call, container, false);

        mContext = container.getContext();
        setupViews(controlView);
        return controlView;
    }

    void setupViews(View controlView) {

        // Create UI controls.
        contactImageView = (ImageView) controlView.findViewById(R.id.contact_image);
        contactView = (TextView) controlView.findViewById(R.id.contact_name_call);
        connectButton = (ImageView) controlView.findViewById(R.id.button_call_connect);
        disconnectButton = (ImageView) controlView.findViewById(R.id.button_call_disconnect);
        connectingProgress = (SpinKitView) controlView.findViewById(R.id.connecting_progress);
        audioCallBackground = (ImageView) controlView.findViewById(R.id.audioCallBackground);

        // Add buttons click events.
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                callEvents.onRejectCall();
            }
        });

        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (incomingCall) {
                    callEvents.onAnswerCall();
                }
                else if (conferenceCall) {
                    callEvents.onStartConferenceCall(mUser);
                }
            }
        });

        connectButton.setVisibility(View.GONE);
        disconnectButton.setVisibility(View.GONE);

    }

    void populateViews() {
        boolean captureSliderEnabled = false;
        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(WebsocketService.EXTRA_USER)) {
                mUser = (User) args.getSerializable(WebsocketService.EXTRA_USER);
                String server = args.getString(WebsocketService.EXTRA_ADDRESS);
                String contactText = "";

                incomingCall = args.containsKey(WebsocketService.EXTRA_REMOTE_DESCRIPTION);
                conferenceCall = args.containsKey(WebsocketService.EXTRA_CONFERENCE_ID) && args.getString(WebsocketService.EXTRA_CONFERENCE_ID, "").length() != 0;

                // conference call can be incoming or outgoing
                if (conferenceCall) {
                    String ownId = args.getString(WebsocketService.EXTRA_OWN_ID);
                    if ((ownId.compareTo(mUser.Id) < 0)) {
                        incomingCall = false;
                    }
                    else {
                        incomingCall = true;
                    }
                }

                if (incomingCall) {
                    contactText = getString(R.string.incoming_call_from) + " " + mUser.displayName;
                }
                else {
                    contactText = getString(R.string.calling) + " " + mUser.displayName;
                }

                contactView.setText(contactText);

                String buddyPic = mUser.buddyPicture;
                if (buddyPic.length() != 0) {
                    String path = buddyPic.substring(4);
                    String url = "https://" + server + RoomActivity.BUDDY_IMG_PATH + path;
                    ThumbnailsCacheManager.LoadImage(url, contactImageView, mUser.displayName, true, true);
                }
                else {
                    contactImageView.setImageResource(R.drawable.ic_person_white_48dp);
                }
                enableCallButtons();
            }
            else if (args.containsKey(BroadcastTypes.EXTRA_JID)) {
                String contactText = "";
                incomingCall = args.containsKey(WebsocketService.EXTRA_REMOTE_DESCRIPTION);

                if (incomingCall) {
                    contactText = getString(R.string.incoming_call_from) + " " + args.getString(BroadcastTypes.EXTRA_JID);
                }
                else {
                    contactText = getString(R.string.calling) + " " + args.getString(BroadcastTypes.EXTRA_JID);
                }

                contactView.setText(contactText);
                enableCallButtons();
            }

            if (args.containsKey(EXTRA_VIDEO_CALL)) {
                boolean video = args.getBoolean(EXTRA_VIDEO_CALL);
                audioCallBackground.setVisibility(video ? View.INVISIBLE : View.VISIBLE);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        populateViews();

        if (mSoundPlayer != null) {
            mSoundPlayer.Stop();
        }

        if (mVibrator != null) {
            mVibrator.cancel();
        }

        if (!incomingCall && !conferenceCall) {
            // initiate call when not incoming
            callEvents.onStartCall();
            mSoundPlayer = new SoundPlayer(mContext, R.raw.ringtone1);
            mSoundPlayer.Play(true);
        }
        else {
            AudioManager am = (AudioManager) getActivity().getSystemService(Context.AUDIO_SERVICE);
            if (am != null && am.getRingerMode() != AudioManager.RINGER_MODE_SILENT) {
                if (am.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE) {
                    long[] pattern = {0, 100, 1000};
                    mVibrator = (Vibrator) getActivity().getSystemService(Context.VIBRATOR_SERVICE);
                    mVibrator.vibrate(pattern, 0);
                }
                else {
                    mSoundPlayer = new SoundPlayer(mContext, R.raw.whistle1);
                    mSoundPlayer.Play(true);
                }
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mSoundPlayer != null) {
            mSoundPlayer.Stop();
        }
        if (mVibrator != null) {
            mVibrator.cancel();
        }
    }

    // TODO(sakal): Replace with onAttach(Context) once we only support API level 23+.
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        callEvents = (OnInitiateCallEvents) activity;
    }

    @Override
    public void onDestroy() {
        if (mSoundPlayer != null) {
            mSoundPlayer.Stop();
        }
        if (mVibrator != null) {
            mVibrator.cancel();
        }
        super.onDestroy();
    }
}
