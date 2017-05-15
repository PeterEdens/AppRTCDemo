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
import android.content.Intent;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.sound.SoundPlayer;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.webrtc.RendererCommon.ScalingType;

import java.util.ArrayList;

/**
 * Fragment for call control.
 */
public class CallFragment extends Fragment {
  private View controlView;
  private TextView contactView;
  private FloatingActionButton disconnectButton;
  private FloatingActionButton cameraSwitchButton;
  private FloatingActionButton videoScalingButton;
  private FloatingActionButton toggleMuteButton;

  private OnCallEvents callEvents;
  private ScalingType scalingType;
  private boolean videoCallEnabled = true;
  private SoundPlayer mSoundPlayer;
  private Context mContext;
  private FloatingActionMenu addToCallButton;
  private CallActivity parentActivity;
  private FloatingActionButton addAllButton;
    private String mOwnId;
    private FloatingActionButton toggleVideoButton;

    /**
   * Call control interface for container activity.
   */
  public interface OnCallEvents {
    void onCallHangUp();
    void onCameraSwitch();
    void onVideoScalingSwitch(ScalingType scalingType);
    void onCaptureFormatChange(int width, int height, int framerate);
    boolean onToggleMic();
    boolean onToggleVideo();
  }

  public void onUserEntered(User user) {
      addCallButtonForUser(user);
  }

  public void onUserLeft(User user) {
      View view = addToCallButton.findViewWithTag(user.Id);
      if (view != null) {
          addToCallButton.removeMenuButton((FloatingActionButton) view);
      }
  }

  void addUsers() {
    ArrayList<User> users = parentActivity.getUsers();
    for (final User user: users) {
      addCallButtonForUser(user);
    }
  }

    private void addCallButtonForUser(final User user) {
        final ContextThemeWrapper context = new ContextThemeWrapper(getActivity(), R.style.MenuButtonsStyle);
        if (!parentActivity.callHasId(user.Id)) {
            com.github.clans.fab.FloatingActionButton programFab2 = new com.github.clans.fab.FloatingActionButton(context);
            programFab2.setLabelText(user.displayName);

            if (user.buddyPicture.length() != 0) {
                String path = user.buddyPicture.substring(4);
                String url = "https://" + parentActivity.getServerAddress() + RoomActivity.BUDDY_IMG_PATH + path;
                ThumbnailsCacheManager.LoadImage(url, programFab2, user.displayName, true, true);
            } else {
                programFab2.setImageResource(R.drawable.user_icon_round);
            }

            programFab2.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(mContext, CallActivity.class);
                    intent.setAction(WebsocketService.ACTION_ADD_CONFERENCE_USER);
                    intent.putExtra(WebsocketService.EXTRA_USER, user);
                    intent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);
                    intent.putExtra(WebsocketService.EXTRA_ID, user.Id);
                    intent.putExtra(WebsocketService.EXTRA_USERACTION, true);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                    addToCallButton.close(true);
                }
            });

            programFab2.setTag(user.Id);

            addToCallButton.addMenuButton(programFab2);
        }
    }

    @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    controlView = inflater.inflate(R.layout.fragment_call, container, false);
    mContext = container.getContext();

    // Create UI controls.
    contactView = (TextView) controlView.findViewById(R.id.contact_name_call);
    disconnectButton = (FloatingActionButton) controlView.findViewById(R.id.button_call_disconnect);
    cameraSwitchButton = (FloatingActionButton) controlView.findViewById(R.id.button_call_switch_camera);
    toggleVideoButton = (FloatingActionButton) controlView.findViewById(R.id.button_call_toggle_video);
    //videoScalingButton = (FloatingActionButton) controlView.findViewById(R.id.button_call_scaling_mode);
    toggleMuteButton = (FloatingActionButton) controlView.findViewById(R.id.button_call_toggle_mic);

    addToCallButton = (FloatingActionMenu)  controlView.findViewById(R.id.add_to_call);
    addAllButton = (FloatingActionButton) controlView.findViewById(R.id.add_all);

    addAllButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Intent intent = new Intent(mContext, CallActivity.class);
        intent.setAction(WebsocketService.ACTION_ADD_ALL_CONFERENCE);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
        addToCallButton.close(true);
      }
    });
    // Add buttons click events.
    disconnectButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        callEvents.onCallHangUp();
      }
    });

    cameraSwitchButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        callEvents.onCameraSwitch();
      }
    });

    /*videoScalingButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        if (scalingType == ScalingType.SCALE_ASPECT_FILL) {
          videoScalingButton.setBackgroundResource(R.drawable.ic_action_full_screen);
          scalingType = ScalingType.SCALE_ASPECT_FIT;
        } else {
          videoScalingButton.setBackgroundResource(R.drawable.ic_action_return_from_full_screen);
          scalingType = ScalingType.SCALE_ASPECT_FILL;
        }
        callEvents.onVideoScalingSwitch(scalingType);
      }
    });*/
    scalingType = ScalingType.SCALE_ASPECT_FILL;

    toggleMuteButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        boolean enabled = callEvents.onToggleMic();
        toggleMuteButton.setAlpha(enabled ? 1.0f : 0.3f);
      }
    });

    toggleVideoButton.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            boolean enabled = callEvents.onToggleVideo();
            toggleVideoButton.setImageResource(enabled ? R.drawable.ic_visibility_white_24dp :R.drawable.ic_visibility_off_white_24dp);
        }
    });

    return controlView;
  }

  @Override
  public void onStart() {
    super.onStart();

    boolean captureSliderEnabled = false;
    Bundle args = getArguments();
    if (args != null) {
        mOwnId = args.getString(WebsocketService.EXTRA_OWN_ID);
      String contactName = args.getString(CallActivity.EXTRA_ROOMID);
      contactView.setText(contactName);
      videoCallEnabled = args.getBoolean(CallActivity.EXTRA_VIDEO_CALL, true);
      captureSliderEnabled = videoCallEnabled
          && args.getBoolean(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, false);
    }
    if (!videoCallEnabled) {
      cameraSwitchButton.setVisibility(View.INVISIBLE);
    }


    if (mSoundPlayer != null) {
      mSoundPlayer.Stop();
    }
    mSoundPlayer = new SoundPlayer(mContext, R.raw.connect1);
    mSoundPlayer.Play(false);
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
    parentActivity = (CallActivity)activity;
    callEvents = (OnCallEvents) activity;
  }
}
