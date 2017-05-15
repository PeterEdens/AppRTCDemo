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
import android.os.Bundle;
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

import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.sound.SoundPlayer;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.webrtc.RendererCommon.ScalingType;

/**
 * Fragment for call control.
 */
public class InitiateCallFragment extends Fragment {
    private View controlView;
    private TextView contactView;
    private Button disconnectButton;
    private Button connectButton;
    private ScalingType scalingType;
    private boolean videoCallEnabled = true;
    private ImageView contactImageView;
    private boolean incomingCall = true;
    private SoundPlayer mSoundPlayer;

    private OnInitiateCallEvents callEvents;
    private Context mContext;

    public void enableCallButtons() {
        if (incomingCall) {
            connectButton.setVisibility(View.VISIBLE);
        }
        disconnectButton.setVisibility(View.VISIBLE);
    }

    public void showPickupTimeout(User user) {
        Toast.makeText(mContext, user.displayName + " does not pickup", Toast.LENGTH_LONG).show();
    }

    public interface OnInitiateCallEvents {
        void onRejectCall();
        void onAnswerCall();
        void onStartCall();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        controlView = inflater.inflate(R.layout.fragment_initiate_call, container, false);

        mContext = container.getContext();
        
        // Create UI controls.
        contactImageView = (ImageView) controlView.findViewById(R.id.contact_image);
        contactView = (TextView) controlView.findViewById(R.id.contact_name_call);
        connectButton = (Button) controlView.findViewById(R.id.button_call_connect);
        disconnectButton = (Button) controlView.findViewById(R.id.button_call_disconnect);

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
            }
        });

        connectButton.setVisibility(View.GONE);
        disconnectButton.setVisibility(View.GONE);


        return controlView;
    }

    @Override
    public void onStart() {
        super.onStart();

        boolean captureSliderEnabled = false;
        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(WebsocketService.EXTRA_USER)) {
                User user = (User) args.getSerializable(WebsocketService.EXTRA_USER);
                String server = args.getString(WebsocketService.EXTRA_ADDRESS);
                String contactText = "";

                incomingCall = args.containsKey(WebsocketService.EXTRA_REMOTE_DESCRIPTION);

                if (incomingCall) {
                    contactText = getString(R.string.incoming_call_from) + " " + user.displayName;
                }
                else {
                    contactText = getString(R.string.calling) + " " + user.displayName;
                }

                contactView.setText(contactText);

                String buddyPic = user.buddyPicture;
                if (buddyPic.length() != 0) {
                    String path = buddyPic.substring(4);
                    String url = "https://" + server + RoomActivity.BUDDY_IMG_PATH + path;
                    ThumbnailsCacheManager.LoadImage(url, contactImageView, user.displayName, true, true);
                }
                else {
                    contactImageView.setImageResource(R.drawable.user_icon);
                }
                enableCallButtons();
            }
        }

        if (mSoundPlayer != null) {
            mSoundPlayer.Stop();
        }

        if (!incomingCall) {
            // initiate call when not incoming
            callEvents.onStartCall();
            mSoundPlayer = new SoundPlayer(mContext, R.raw.ringtone1);
            mSoundPlayer.Play(true);
            callEvents.onStartCall();
        }
        else {

            mSoundPlayer = new SoundPlayer(mContext, R.raw.whistle1);
            mSoundPlayer.Play(true);
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
        super.onDestroy();
    }
}
