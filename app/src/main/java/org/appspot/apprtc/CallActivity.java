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
import android.app.AlertDialog;
import android.app.FragmentTransaction;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.media.projection.MediaProjection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManager.LayoutParams;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.sharedresourceslib.BroadcastTypes;
import com.github.clans.fab.FloatingActionButton;

import org.appspot.apprtc.AppRTCAudioManager.AudioDevice;
import org.appspot.apprtc.AppRTCAudioManager.AudioManagerEvents;
import org.appspot.apprtc.AppRTCClient.RoomConnectionParameters;
import org.appspot.apprtc.AppRTCClient.SignalingParameters;
import org.appspot.apprtc.PeerConnectionClient.DataChannelParameters;
import org.appspot.apprtc.PeerConnectionClient.PeerConnectionParameters;
import org.appspot.apprtc.fragment.CallListFragment;
import org.appspot.apprtc.fragment.ChatFragment;
import org.appspot.apprtc.receiver.CustomPhoneStateListener;
import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.sound.SoundPlayer;

import org.appspot.apprtc.util.SessionIdentifierGenerator;
import org.appspot.apprtc.util.ThumbnailsCacheManager;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.FileVideoCapturer;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon.ScalingType;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFileRenderer;
import org.webrtc.VideoRenderer;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static com.example.sharedresourceslib.BroadcastTypes.ACTION_SEND_SESSION_DESCRIPTION;
import static com.example.sharedresourceslib.BroadcastTypes.EXTRA_CANDIDATES;
import static com.example.sharedresourceslib.BroadcastTypes.EXTRA_SIGNALING;
import static org.appspot.apprtc.RoomActivity.ACTION_SHARE_FILE;

/**
 * Activity for peer connection call setup, call waiting
 * and call view.
 */
public class CallActivity extends AppCompatActivity implements AppRTCClient.SignalingEvents,
                                                      PeerConnectionClient.PeerConnectionEvents,
                                                      CallFragment.OnCallEvents,
                                                      ChatFragment.OnChatEvents,
                                                      InitiateCallFragment.OnInitiateCallEvents, AdditionalPeerConnection.AdditionalPeerConnectionEvents, PeerConnectionClient.DataChannelCallback {
  public static final String ACTION_NEW_CALL = "org.appspot.apprtc.ACTION_NEW_CALL";
  public static final String ACTION_RESUME_CALL = "org.appspot.apprtc.ACTION_RESUME_CALL";
  public static final String EXTRA_PEER_ID = "org.appspot.apprtc.EXTRA_PEER_ID";
  public static final String ACTION_HANG_UP = "org.appspot.apprtc.ACTION_HANG_UP";
  public static final String ACTION_TOGGLE_VIDEO = "org.appspot.apprtc.ACTION_TOGGLE_VIDEO";
    public static final String ACTION_TOGGLE_MIC = "org.appspot.apprtc.ACTION_TOGGLE_MIC";
    public static final String ACTION_SEND_MESSAGE = "org.appspot.apprtc.ACTION_SEND_MESSAGE";
    public static final String EXTRA_MESSAGE = "org.appspot.apprtc.EXTRA_MESSAGE";
  public static final String EXTRA_MIC_ENABLED = "org.appspot.apprtc.EXTRA_MIC_ENABLED";
  public static final String EXTRA_ROOMID = "org.appspot.apprtc.ROOMID";
  public static final String EXTRA_LOOPBACK = "org.appspot.apprtc.LOOPBACK";
  public static final String EXTRA_VIDEO_CALL = "org.appspot.apprtc.VIDEO_CALL";
  public static final String EXTRA_SCREENCAPTURE = "org.appspot.apprtc.SCREENCAPTURE";
  public static final String EXTRA_CAMERA2 = "org.appspot.apprtc.CAMERA2";
  public static final String EXTRA_VIDEO_WIDTH = "org.appspot.apprtc.VIDEO_WIDTH";
  public static final String EXTRA_VIDEO_HEIGHT = "org.appspot.apprtc.VIDEO_HEIGHT";
  public static final String EXTRA_VIDEO_FPS = "org.appspot.apprtc.VIDEO_FPS";
  public static final String EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED =
      "org.appsopt.apprtc.VIDEO_CAPTUREQUALITYSLIDER";
  public static final String EXTRA_VIDEO_BITRATE = "org.appspot.apprtc.VIDEO_BITRATE";
  public static final String EXTRA_VIDEOCODEC = "org.appspot.apprtc.VIDEOCODEC";
  public static final String EXTRA_HWCODEC_ENABLED = "org.appspot.apprtc.HWCODEC";
  public static final String EXTRA_CAPTURETOTEXTURE_ENABLED = "org.appspot.apprtc.CAPTURETOTEXTURE";
  public static final String EXTRA_FLEXFEC_ENABLED = "org.appspot.apprtc.FLEXFEC";
  public static final String EXTRA_AUDIO_BITRATE = "org.appspot.apprtc.AUDIO_BITRATE";
  public static final String EXTRA_AUDIOCODEC = "org.appspot.apprtc.AUDIOCODEC";
  public static final String EXTRA_NOAUDIOPROCESSING_ENABLED =
      "org.appspot.apprtc.NOAUDIOPROCESSING";
  public static final String EXTRA_AECDUMP_ENABLED = "org.appspot.apprtc.AECDUMP";
  public static final String EXTRA_OPENSLES_ENABLED = "org.appspot.apprtc.OPENSLES";
  public static final String EXTRA_DISABLE_BUILT_IN_AEC = "org.appspot.apprtc.DISABLE_BUILT_IN_AEC";
  public static final String EXTRA_DISABLE_BUILT_IN_AGC = "org.appspot.apprtc.DISABLE_BUILT_IN_AGC";
  public static final String EXTRA_DISABLE_BUILT_IN_NS = "org.appspot.apprtc.DISABLE_BUILT_IN_NS";
  public static final String EXTRA_ENABLE_LEVEL_CONTROL = "org.appspot.apprtc.ENABLE_LEVEL_CONTROL";
  public static final String EXTRA_DISPLAY_HUD = "org.appspot.apprtc.DISPLAY_HUD";
  public static final String EXTRA_TRACING = "org.appspot.apprtc.TRACING";
  public static final String EXTRA_CMDLINE = "org.appspot.apprtc.CMDLINE";
  public static final String EXTRA_RUNTIME = "org.appspot.apprtc.RUNTIME";
  public static final String EXTRA_VIDEO_FILE_AS_CAMERA = "org.appspot.apprtc.VIDEO_FILE_AS_CAMERA";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE =
      "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH =
      "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_WIDTH";
  public static final String EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT =
      "org.appspot.apprtc.SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT";
  public static final String EXTRA_USE_VALUES_FROM_INTENT =
      "org.appspot.apprtc.USE_VALUES_FROM_INTENT";
  public static final String EXTRA_DATA_CHANNEL_ENABLED = "org.appspot.apprtc.DATA_CHANNEL_ENABLED";
  public static final String EXTRA_ORDERED = "org.appspot.apprtc.ORDERED";
  public static final String EXTRA_MAX_RETRANSMITS_MS = "org.appspot.apprtc.MAX_RETRANSMITS_MS";
  public static final String EXTRA_MAX_RETRANSMITS = "org.appspot.apprtc.MAX_RETRANSMITS";
  public static final String EXTRA_PROTOCOL = "org.appspot.apprtc.PROTOCOL";
  public static final String EXTRA_NEGOTIATED = "org.appspot.apprtc.NEGOTIATED";
  public static final String EXTRA_ID = "org.appspot.apprtc.ID";

  private static final int FILE_CODE = 1;

  private static final String TAG = "CallRTCClient";

  // Peer connection statistics callback period in ms.
  private static final int STAT_CALLBACK_PERIOD = 1000;
  // Local preview screen position before call is connected.
  private static final int LOCAL_X_CONNECTING = 0;
  private static final int LOCAL_Y_CONNECTING = 0;
  private static final int LOCAL_WIDTH_CONNECTING = 100;
  private static final int LOCAL_HEIGHT_CONNECTING = 100;
  // Local preview screen position after call is connected.
  private static final int LOCAL_X_CONNECTED = 72;
  private static final int LOCAL_Y_CONNECTED = 62;
  private static final int LOCAL_WIDTH_CONNECTED = 25;
  private static final int LOCAL_HEIGHT_CONNECTED = 25;
  // Remote video screen position
  private static final int REMOTE_X = 0;
  private static final int REMOTE_X2 = 50;
  private static final int REMOTE_Y = 0;
  private static final int REMOTE_WIDTH = 100;
  private static final int REMOTE_WIDTH2 = 50;
  private static final int REMOTE_HEIGHT = 100;
  private static final int REMOTE_HEIGHT2 = 50;
  private PeerConnectionClient peerConnectionClient = null;

  //private AppRTCClient appRtcClient;
  private String mPeerId = "";
  private String mPeerName = "";
  private String mOwnId = "";
  WebsocketService mService;
  boolean mWebsocketServiceBound = false;
  private SerializableSessionDescription mRemoteSdp = null;
  private SessionDescription mLocalSdp = null;
  private boolean initiator = false;
  private SignalingParameters signalingParameters;
  static private AppRTCAudioManager audioManager = null;
  static private EglBase rootEglBase;
  private SurfaceViewRenderer localRender;
  private SurfaceViewRenderer screenshareRender;
  private SurfaceViewRenderer remoteRenderScreen;
  private SurfaceViewRenderer remoteRenderScreen2;
  private SurfaceViewRenderer remoteRenderScreen3;
  private SurfaceViewRenderer remoteRenderScreen4;
  private TextView remoteVideoLabel;
  private TextView remoteVideoLabel2;
  private TextView remoteVideoLabel3;
  private TextView remoteVideoLabel4;
  private ImageView remoteUserImage;
  private ImageView remoteUserImage2;
  private ImageView remoteUserImage3;
  private ImageView remoteUserImage4;
  private ArrayList<RemoteConnectionViews> remoteViewsList = new ArrayList<RemoteConnectionViews>();
  private ArrayList<RemoteConnectionViews> remoteViewsInUseList = new ArrayList<RemoteConnectionViews>();
  private VideoFileRenderer videoFileRenderer;
  private final List<VideoRenderer.Callbacks> remoteRenderers =
      new ArrayList<VideoRenderer.Callbacks>();
  private PercentFrameLayout localRenderLayout;
  private PercentFrameLayout screenshareRenderLayout;
  private PercentFrameLayout remoteRenderLayout;
  private PercentFrameLayout remoteRenderLayout2;
  private PercentFrameLayout remoteRenderLayout3;
  private PercentFrameLayout remoteRenderLayout4;
  private ScalingType scalingType;
  private Toast logToast;
  private boolean commandLineRun;
  private int runTimeMs;
  private boolean activityRunning;
  private RoomConnectionParameters roomConnectionParameters;
  private PeerConnectionParameters peerConnectionParameters;
  private boolean iceConnected;
  private boolean isError;
  private boolean callControlFragmentVisible = true;
  private long callStartedTimeMs = 0;
  private boolean micEnabled = true;
  private boolean videoEnabled = true;
  private boolean screencaptureEnabled = false;
  private static Intent mediaProjectionPermissionResultData;
  private static int mediaProjectionPermissionResultCode;
  private boolean disconnected = false;

  private String keyprefVideoCallEnabled;
  private String keyprefScreencapture;
  private String keyprefCamera2;
  private String keyprefResolution;
  private String keyprefFps;
  private String keyprefCaptureQualitySlider;
  private String keyprefVideoBitrateType;
  private String keyprefVideoBitrateValue;
  private String keyprefVideoCodec;
  private String keyprefAudioBitrateType;
  private String keyprefAudioBitrateValue;
  private String keyprefAudioCodec;
  private String keyprefHwCodecAcceleration;
  private String keyprefCaptureToTexture;
  private String keyprefFlexfec;
  private String keyprefNoAudioProcessingPipeline;
  private String keyprefAecDump;
  private String keyprefOpenSLES;
  private String keyprefDisableBuiltInAec;
  private String keyprefDisableBuiltInAgc;
  private String keyprefDisableBuiltInNs;
  private String keyprefEnableLevelControl;
  private String keyprefDisplayHud;
  private String keyprefTracing;
  private String keyprefRoomServerUrl;
  private String keyprefEnableDataChannel;
  private String keyprefOrdered;
  private String keyprefMaxRetransmitTimeMs;
  private String keyprefMaxRetransmits;
  private String keyprefDataProtocol;
  private String keyprefNegotiated;
  private String keyprefDataId;

  // Controls
  private InitiateCallFragment initiateCallFragment;
  private CallFragment callFragment;
  private CallListFragment callListFragment;
  private HudFragment hudFragment;
  //private CpuMonitor cpuMonitor;

  private SharedPreferences sharedPref;

  private IntentFilter mIntentFilter;

  String mConferenceId = null;
  ArrayList<String> connectedUserIds = new ArrayList<String>();
  private boolean mSentAnswer;
    private boolean mAnswerPressed;
  private boolean speakerPhoneEnabled = true;
  private boolean userListShown;
  private ImageView remoteUserHoldStatus;
  private ImageView remoteUserHoldStatus2;
  private ImageView remoteUserHoldStatus3;
  private ImageView remoteUserHoldStatus4;
  private RemoteConnectionViews screenshareRemoteView;
  private boolean maximizeScreenshare = true;
  private int screenshareFrontIndex;
  private String mSignaling = "spreed";
  private String mOwnJid;
  private String mSid;
  private ChatFragment chatFragment;
  private String mFileRecipient;

  @Override
  public void onBinaryMessage(DataChannel.Buffer buffer) {

  }

  @Override
  public void onTextMessage(String text) {
      try {
          JSONObject json = new JSONObject(text);
          String type = json.optString("Type");

          if (type.equals("Screenshare")) {
              String userId = json.optString("Id");
              String screenshareTxt = json.optString("Screenshare");
              JSONObject screnshareJson = null;
              screnshareJson = new JSONObject(screenshareTxt);

              String id = screnshareJson.optString("id");
              onScreenShare(mPeerId, id, peerConnectionClient);
          }
          else if (type.equals("Chat")) {
            String chatTxt = json.optString("Chat");
            JSONObject chatJson = new JSONObject(chatTxt);
            String message = chatJson.optString("Message");
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
            String time = fmt.format(new Date());
            String status = "";
            String statusTxt = chatJson.optString("Status");
            if (statusTxt != null && !statusTxt.equals("null") && statusTxt.length() > 0) {
              JSONObject jsonStatus = new JSONObject(statusTxt);
              if (jsonStatus.has("Typing")) {
                status = " is Typing";
              }
              else if (jsonStatus.has("FileInfo")) {
                // {"FileInfo":{"id":"511832825.560452.PNG_2X0jvV2T8nN4c5ymaxa0cmkDgE5rvJB0upD3KB+h","chunks":22,"name":"511832825.560452.PNG","size":1296472,"type":"image\/png"}}
                JSONObject jsonFileInfo = new JSONObject(jsonStatus.getString("FileInfo"));
                String id = jsonFileInfo.getString("id");
                String chunks = jsonFileInfo.getString("chunks");
                String name = jsonFileInfo.getString("name");
                String size = jsonFileInfo.getString("size");
                String filetype = jsonFileInfo.getString("type");
                mService.onFileMessage(time, id, chunks, name, size, filetype, mPeerId, mService.getCurrentRoomName());
              }
            }
            else {
              mService.onChatMessage(message, time, status, mOwnId, mPeerId, mService.getCurrentRoomName());
            }
          }
          else if (type.equals("Answer")) {
            String answerTxt = json.optString("Answer");
            JSONObject answerJson = new JSONObject(answerTxt);
            SessionDescription sdp = new SessionDescription(
                    SessionDescription.Type.fromCanonicalForm(type), answerJson.getString("sdp"));
            String token = answerJson.optString("_token");
            String id = answerJson.optString("_id");
            String conferenceId = answerJson.optString("_conference");
            AdditionalPeerConnection peer = mTokenPeers.get(mPeerId);
            peer.setRemoteDescription(sdp);
          }
          else if (type.equals("Candidate")) {
            String candidateTxt = json.optString("Candidate");
            JSONObject candidateJson = new JSONObject(candidateTxt);
            String id = candidateJson.optString("_id");
            String token = candidateJson.optString("_token");
            IceCandidate candidate = new IceCandidate(
                    candidateJson.getString("sdpMid"), candidateJson.getInt("sdpMLineIndex"), candidateJson.getString("candidate"));
            AdditionalPeerConnection peer = mTokenPeers.get(mPeerId);
            peer.addRemoteIceCandidate(candidate);

          }
          else if (type.equals("Hold")) {
              String holdTxt = json.optString("Hold");
              JSONObject holdJson = null;
              holdJson = new JSONObject(holdTxt);

              boolean hold = holdJson.getBoolean("state");

              showHoldMessage(hold);
        }
      } catch (JSONException e) {
          e.printStackTrace();
      }
  }

  @Override
  public void onStateChange(DataChannel.State state) {

  }

    @Override
    public void onSendChatMessage(String time, String displayName, String buddyPicture, String message, String to) {
        if (mService != null) {
            mService.sendChatMessage(time, displayName, buddyPicture, message, to, mService.getCurrentRoomName());
        }
    }

    @Override
    public void onMessageRead() {

    }

    @Override
    public void onSendFile(String time, String displayName, String buddyPicture, String message, long size, String name, String mime, String to) {

    }

  @Override
  public void onDownload(int position, String id, FileInfo fileinfo) {

  }

  public class RemoteConnection {
    SerializableSessionDescription sdp;
    User user;

    public RemoteConnection(SerializableSessionDescription sdp, User user) {
      this.sdp = sdp;
      this.user = user;
    }
  }

  public class RemoteConnectionViews {
    String id = "";
    String mName = "";
    PercentFrameLayout frameLayout;
    SurfaceViewRenderer surfaceViewRenderer;
    ImageView imageView;
    TextView textView;
    ImageView remoteUserHoldStatus;

    public RemoteConnectionViews(String name, PercentFrameLayout frameLayout, SurfaceViewRenderer surfaceViewRenderer, ImageView imageView, TextView textView, ImageView remoteUserHoldStatus) {
      this.mName = name;
      this.frameLayout = frameLayout;
      this.surfaceViewRenderer = surfaceViewRenderer;
      this.imageView = imageView;
      this.textView = textView;
      this.remoteUserHoldStatus = remoteUserHoldStatus;
    }

    public PercentFrameLayout getFrameLayout() {
      return frameLayout;
    }

    public SurfaceViewRenderer getSurfaceViewRenderer() {
      return this.surfaceViewRenderer;
    }

    public ImageView getImageView() {
      return imageView;
    }

    public TextView getTextView() {
      return textView;
    }

    public void setPosition(int remoteX, int remoteY, int remoteWidth, int remoteHeight) {
      getFrameLayout().setPosition(remoteX, remoteY, remoteWidth, remoteHeight);
    }

    public String getName() {
      return mName;
    }

    public void setHoldStatus(boolean on) {
      if (on) {
        remoteUserHoldStatus.setVisibility(View.VISIBLE);
      }
      else {
        remoteUserHoldStatus.setVisibility(View.GONE);
      }
    }

    public String getId() {
      return id;
    }

    public void setId(String remoteId) {
      id = remoteId;
    }
  }

  /** Defines callbacks for service binding, passed to bindService() */
  private ServiceConnection mConnection = new ServiceConnection() {

    @Override
    public void onServiceConnected(ComponentName className,
                                   IBinder service) {
      // We've bound to LocalService, cast the IBinder and get LocalService instance
      WebsocketService.WebsocketBinder binder = (WebsocketService.WebsocketBinder) service;
      mService = binder.getService();
      mWebsocketServiceBound = true;
      mOwnId = mService.getId();
      signalingParameters = new SignalingParameters(WebsocketService.getIceServers(), initiator, "", "", "", null, null);
      callListFragment.init(mService.getUsersInRoom(mService.getCurrentRoomName()));
    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      mWebsocketServiceBound = false;
    }
  };
  private HashMap<String, SerializableIceCandidate> mIceCandidates = new HashMap<String, SerializableIceCandidate>();
  private boolean mWaitingToStartCall;
  private boolean mCaptureToTexture;
  private SoundPlayer mSoundPlayer;

  private boolean mVideoCallEnabled;
  private boolean mUseCamera2;
  private String mProtocol;
  private int mVideoWidth = 0;
  private int mVideoHeight = 0;
  private boolean mLoopback = false;
  private String mVideoCodec;
  private boolean mHwCodecEnabled = false;
  private boolean mFlexfecEnabled;
  private boolean mDisableBuiltInAGC;
  private boolean mEnableLevelControl;
  private boolean mAecDump;
  private String mAudioCodec;
  private int mId = -1;
  private boolean mDisableBuiltInNS;
  private boolean mNoAudioProcessing;
  private boolean mUseOpenSLES;
  private int mMaxRetr;
  private boolean mDisableBuiltInAEC;
  private int mCameraFps;
  private boolean mCaptureQualitySlider;
  private int mVideoStartBitrate;
  private int mAudioStartBitrate;
  private boolean mDisplayHud;
  private boolean mTracing;
  private boolean mDataChannelEnabled;
  private boolean mOrdered;
  private boolean mNegotiated;
  private int mMaxRetrMs = -1;
  private SerializableIceCandidate mRemoteIceCandidate;
  private boolean mFinishCalled;

  private HashMap<String, AdditionalPeerConnection> mAdditionalPeers = new HashMap<String, AdditionalPeerConnection>();
  private HashMap<String, AdditionalPeerConnection> mTokenPeers = new HashMap<String, AdditionalPeerConnection>();

  private BroadcastReceiver mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getAction().equals(CustomPhoneStateListener.ACTION_HOLD_ON)) {
        putCallOnHold(true);
      }
      else if (intent.getAction().equals(CustomPhoneStateListener.ACTION_HOLD_OFF)) {
        putCallOnHold(false);
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_SCREENSHARE)) {
        User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
        String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);
        addToCall(user, token, false, null);
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_BYE)) {

        String reason = intent.getStringExtra(WebsocketService.EXTRA_REASON);
        User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
        String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
        if (user != null) {
            user.setCallState(User.CallState.NONE);
        }

        if (mAdditionalPeers.containsKey(id)) {
          updateRemoteViewList(mAdditionalPeers.get(id).getRemoteViews());
          mAdditionalPeers.get(id).close();
          mAdditionalPeers.remove(id);
        }
        else if (reason.equals("ringertimeout")) {
          if (!mFinishCalled) {
            mFinishCalled = true;
            finish();
          }
        }
        else if (reason.equals("pickuptimeout")) {
          initiateCallFragment.showPickupTimeout(user);
          if (!mFinishCalled) {
            mFinishCalled = true;
            finish();
          }
        }
        else if (user != null && user.Id.equals(mPeerId)) {
          // normal call clearing
          if (!mFinishCalled) {
            mFinishCalled = true;
            finish();
          }
        }
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_USER_ENTERED)) {
        User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
          if (callFragment != null) {
              callFragment.onUserEntered(user);
          }
          if (callListFragment != null) {
              callListFragment.onUserEntered(user);
          }
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_USER_LEFT)) {
        User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
          if (callFragment != null) {
              callFragment.onUserLeft(user);
          }
          if (callListFragment != null) {
              callListFragment.onUserLeft(user);
          }
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_CHAT_MESSAGE)) {
        // pass the intent to the callFragment and callListFragment
        if (callFragment != null) {
          callFragment.onChatMessage(intent, mServer);
        }
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_FILE_MESSAGE)) {
        if (callFragment != null) {
          callFragment.onFileMessage(intent, mServer);
        }
      }

    }
  };

  private void updateRemoteViewList(RemoteConnectionViews remoteViews) {
    remoteViewsInUseList.remove(remoteViews);
    remoteViewsList.add(remoteViews);

    updateVideoView();
    Log.i(TAG, "remove view " + remoteViews.getName());
  }

  private String mToken;
  private String mSdpId = "";
  private boolean mForceTurn;
  private ArrayList<User> mQueuedPeers = new ArrayList<User>();
  private ArrayList<RemoteConnection> mQueuedRemoteConnections = new ArrayList<RemoteConnection>();
  private String mServer;


  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Thread.setDefaultUncaughtExceptionHandler(new UnhandledExceptionHandler(this));

    // Set window styles for fullscreen-window size. Needs to be done before
    // adding content.
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    getWindow().addFlags(LayoutParams.FLAG_KEEP_SCREEN_ON
        | LayoutParams.FLAG_DISMISS_KEYGUARD | LayoutParams.FLAG_SHOW_WHEN_LOCKED
        | LayoutParams.FLAG_TURN_SCREEN_ON);
    getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    setContentView(R.layout.activity_call);

    mIntentFilter = new IntentFilter();
    mIntentFilter.addAction(WebsocketService.ACTION_BYE);
    mIntentFilter.addAction(WebsocketService.ACTION_USER_ENTERED);
    mIntentFilter.addAction(WebsocketService.ACTION_USER_LEFT);
    mIntentFilter.addAction(WebsocketService.ACTION_SCREENSHARE);
    mIntentFilter.addAction(CustomPhoneStateListener.ACTION_HOLD_ON);
    mIntentFilter.addAction(CustomPhoneStateListener.ACTION_HOLD_OFF);
    mIntentFilter.addAction(WebsocketService.ACTION_CHAT_MESSAGE);
    mIntentFilter.addAction(WebsocketService.ACTION_FILE_MESSAGE);
    registerReceiver(mReceiver, mIntentFilter);

    // Get setting keys.
    PreferenceManager.setDefaultValues(this, R.xml.webrtc_preferences, false);
    sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

    keyprefVideoCallEnabled = getString(R.string.pref_videocall_key);
    keyprefScreencapture = getString(R.string.pref_screencapture_key);
    keyprefCamera2 = getString(R.string.pref_camera2_key);
    keyprefResolution = getString(R.string.pref_resolution_key);
    keyprefFps = getString(R.string.pref_fps_key);
    keyprefCaptureQualitySlider = getString(R.string.pref_capturequalityslider_key);
    keyprefVideoBitrateType = getString(R.string.pref_maxvideobitrate_key);
    keyprefVideoBitrateValue = getString(R.string.pref_maxvideobitratevalue_key);
    keyprefVideoCodec = getString(R.string.pref_videocodec_key);
    keyprefHwCodecAcceleration = getString(R.string.pref_hwcodec_key);
    keyprefCaptureToTexture = getString(R.string.pref_capturetotexture_key);
    keyprefFlexfec = getString(R.string.pref_flexfec_key);
    keyprefAudioBitrateType = getString(R.string.pref_startaudiobitrate_key);
    keyprefAudioBitrateValue = getString(R.string.pref_startaudiobitratevalue_key);
    keyprefAudioCodec = getString(R.string.pref_audiocodec_key);
    keyprefNoAudioProcessingPipeline = getString(R.string.pref_noaudioprocessing_key);
    keyprefAecDump = getString(R.string.pref_aecdump_key);
    keyprefOpenSLES = getString(R.string.pref_opensles_key);
    keyprefDisableBuiltInAec = getString(R.string.pref_disable_built_in_aec_key);
    keyprefDisableBuiltInAgc = getString(R.string.pref_disable_built_in_agc_key);
    keyprefDisableBuiltInNs = getString(R.string.pref_disable_built_in_ns_key);
    keyprefEnableLevelControl = getString(R.string.pref_enable_level_control_key);
    keyprefDisplayHud = getString(R.string.pref_displayhud_key);
    keyprefTracing = getString(R.string.pref_tracing_key);
    keyprefRoomServerUrl = getString(R.string.pref_room_server_url_key);
    keyprefEnableDataChannel = getString(R.string.pref_enable_datachannel_key);
    keyprefOrdered = getString(R.string.pref_ordered_key);
    keyprefMaxRetransmitTimeMs = getString(R.string.pref_max_retransmit_time_ms_key);
    keyprefMaxRetransmits = getString(R.string.pref_max_retransmits_key);
    keyprefDataProtocol = getString(R.string.pref_data_protocol_key);
    keyprefNegotiated = getString(R.string.pref_negotiated_key);
    keyprefDataId = getString(R.string.pref_data_id_key);

    readPrefs();

    final Intent intent = getIntent();

    if (intent.getAction().equals(WebsocketService.ACTION_REMOTE_ICE_CANDIDATE)) {
      finish(); // don't start the activity on this intent
    }
    else if (intent.getAction().equals(WebsocketService.ACTION_REMOTE_DESCRIPTION)) {
      SerializableSessionDescription sdp = (SerializableSessionDescription) intent.getSerializableExtra(WebsocketService.EXTRA_REMOTE_DESCRIPTION);
      mVideoCallEnabled = sdp.description.contains("m=video");
    }

    iceConnected = false;
    signalingParameters = new SignalingParameters(WebsocketService.getIceServers(), initiator, "", "", "", null, null);
    scalingType = ScalingType.SCALE_ASPECT_FILL;

    // Create UI controls.

    localRender = (SurfaceViewRenderer) findViewById(R.id.local_video_view);
    screenshareRender = (SurfaceViewRenderer) findViewById(R.id.screenshare_video_view);
    remoteRenderScreen = (SurfaceViewRenderer) findViewById(R.id.remote_video_view);

    remoteRenderScreen2 = (SurfaceViewRenderer) findViewById(R.id.remote_video_view2);
    remoteRenderScreen3 = (SurfaceViewRenderer) findViewById(R.id.remote_video_view3);
    remoteRenderScreen4 = (SurfaceViewRenderer) findViewById(R.id.remote_video_view4);
    remoteVideoLabel = (TextView) findViewById(R.id.remote_video_label);
    remoteVideoLabel2 = (TextView) findViewById(R.id.remote_video_label2);
    remoteVideoLabel3 = (TextView) findViewById(R.id.remote_video_label3);
    remoteVideoLabel4 = (TextView) findViewById(R.id.remote_video_label4);
    remoteUserImage = (ImageView) findViewById(R.id.remote_user_image1);
    remoteUserImage2 = (ImageView) findViewById(R.id.remote_user_image2);
    remoteUserImage3 = (ImageView) findViewById(R.id.remote_user_image3);
    remoteUserImage4 = (ImageView) findViewById(R.id.remote_user_image4);
    remoteUserHoldStatus = (ImageView) findViewById(R.id.remote_user_hold_status);
    remoteUserHoldStatus2 = (ImageView) findViewById(R.id.remote_user_hold_status2);
    remoteUserHoldStatus3 = (ImageView) findViewById(R.id.remote_user_hold_status3);
    remoteUserHoldStatus4 = (ImageView) findViewById(R.id.remote_user_hold_status4);
    localRenderLayout = (PercentFrameLayout) findViewById(R.id.local_video_layout);
    screenshareRenderLayout = (PercentFrameLayout) findViewById(R.id.screenshare_video_layout);
    remoteRenderLayout = (PercentFrameLayout) findViewById(R.id.remote_video_layout);
    remoteRenderLayout2 = (PercentFrameLayout) findViewById(R.id.remote_video_layout2);
    remoteRenderLayout3 = (PercentFrameLayout) findViewById(R.id.remote_video_layout3);
    remoteRenderLayout4 = (PercentFrameLayout) findViewById(R.id.remote_video_layout4);
    initiateCallFragment = new InitiateCallFragment();
    callListFragment = new CallListFragment();
    callFragment = new CallFragment();
    hudFragment = new HudFragment();
    chatFragment = new ChatFragment();

    screenshareRemoteView = new RemoteConnectionViews("screenshare", screenshareRenderLayout, screenshareRender, null, null, null);

    remoteViewsInUseList.add(new RemoteConnectionViews("remote 1", remoteRenderLayout, remoteRenderScreen, remoteUserImage, remoteVideoLabel, remoteUserHoldStatus)); // first one is always in use
    remoteViewsList.add(new RemoteConnectionViews("remote 2", remoteRenderLayout2, remoteRenderScreen2, remoteUserImage2, remoteVideoLabel2, remoteUserHoldStatus2));
    remoteViewsList.add(new RemoteConnectionViews("remote 3", remoteRenderLayout3, remoteRenderScreen3, remoteUserImage3, remoteVideoLabel3, remoteUserHoldStatus3));
    remoteViewsList.add(new RemoteConnectionViews("remote 4", remoteRenderLayout4, remoteRenderScreen4, remoteUserImage4, remoteVideoLabel4, remoteUserHoldStatus4));

    // Show/hide call control fragment on view click.
    View.OnClickListener listener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        toggleCallControlFragmentVisibility();
      }
    };

    View.OnClickListener screenshareListener = new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        maximizeScreenshare = !maximizeScreenshare;
        updateVideoView();
      }
    };

    localRender.setOnClickListener(listener);
    screenshareRender.setOnClickListener(screenshareListener);
    remoteRenderScreen.setOnClickListener(listener);
    remoteRenderScreen2.setOnClickListener(listener);
    remoteRenderScreen3.setOnClickListener(listener);
    remoteRenderScreen4.setOnClickListener(listener);
    remoteRenderers.add(remoteRenderScreen);


    // Create video renderers.
    if (rootEglBase == null) {
      rootEglBase = EglBase.create();
    }
    localRender.init(rootEglBase.getEglBaseContext(), null);
    screenshareRender.init(rootEglBase.getEglBaseContext(), null);
    String saveRemoteVideoToFile = intent.getStringExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);

    // When saveRemoteVideoToFile is set we save the video from the remote to a file.
    if (saveRemoteVideoToFile != null) {
      int videoOutWidth = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
      int videoOutHeight = intent.getIntExtra(EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
      try {
        videoFileRenderer = new VideoFileRenderer(
            saveRemoteVideoToFile, videoOutWidth, videoOutHeight, rootEglBase.getEglBaseContext());
        remoteRenderers.add(videoFileRenderer);
      } catch (IOException e) {
        throw new RuntimeException(
            "Failed to open video file for output: " + saveRemoteVideoToFile, e);
      }
    }
    remoteRenderScreen.init(rootEglBase.getEglBaseContext(), null);
    remoteRenderScreen2.init(rootEglBase.getEglBaseContext(), null);
    remoteRenderScreen3.init(rootEglBase.getEglBaseContext(), null);
    remoteRenderScreen4.init(rootEglBase.getEglBaseContext(), null);

    localRender.setZOrderMediaOverlay(true);
    localRender.setEnableHardwareScaler(true /* enabled */);

    screenshareRender.getHolder().setFormat(PixelFormat.TRANSLUCENT);
    screenshareRenderLayout.setVisibility(View.GONE);
    screenshareRender.setVisibility(View.GONE);

    if (intent.getAction().equals(ACTION_RESUME_CALL)) {
      iceConnected = true;
    }

    updateVideoView();



    // If capturing format is not specified for screencapture, use screen resolution.
    if (screencaptureEnabled && mVideoWidth == 0 && mVideoHeight == 0) {
      DisplayMetrics displayMetrics = new DisplayMetrics();
      WindowManager windowManager =
          (WindowManager) getApplication().getSystemService(Context.WINDOW_SERVICE);
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
        windowManager.getDefaultDisplay().getRealMetrics(displayMetrics);
      }
      mVideoWidth = displayMetrics.widthPixels;
      mVideoHeight = displayMetrics.heightPixels;
    }
    DataChannelParameters dataChannelParameters = null;
    if (mDataChannelEnabled) {
      dataChannelParameters = new DataChannelParameters(mOrdered,
              mMaxRetrMs,
              mMaxRetr, mProtocol,
              mNegotiated, mId);
    }
    peerConnectionParameters =
        new PeerConnectionParameters(mVideoCallEnabled, false,
            false, mVideoWidth, mVideoHeight, mCameraFps,
                mVideoStartBitrate, mVideoCodec,
            mHwCodecEnabled,
                mFlexfecEnabled,
                mAudioStartBitrate, mAudioCodec,
                mNoAudioProcessing,
                mAecDump,
                mUseOpenSLES,
                mDisableBuiltInAEC,
                mDisableBuiltInAGC,
                mDisableBuiltInNS,
                mEnableLevelControl, dataChannelParameters);
    commandLineRun = intent.getBooleanExtra(EXTRA_CMDLINE, false);
    runTimeMs = intent.getIntExtra(EXTRA_RUNTIME, 0);

    Log.d(TAG, "VIDEO_FILE: '" + intent.getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA) + "'");

    // Create connection client. Use DirectRTCClient if room name is an IP otherwise use the
    // standard WebSocketRTCClient.
    //if (loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
   //   appRtcClient = new WebSocketRTCClient(this);
   // } else {
   //   Log.i(TAG, "Using DirectRTCClient because room name looks like an IP.");
   //   appRtcClient = new DirectRTCClient(this);
   // }
    // Create connection parameters.
    //roomConnectionParameters = new RoomConnectionParameters(roomUri.toString(), roomId, loopback);

    // Create CPU monitor
    //cpuMonitor = new CpuMonitor(this);
    //hudFragment.setCpuMonitor(cpuMonitor);
    Bundle extras = intent.getExtras();
    extras.putBoolean(CallActivity.EXTRA_VIDEO_CALL, mVideoCallEnabled);

    // Send intent arguments to fragments.
    chatFragment.setArguments(getIntent().getExtras());
    initiateCallFragment.setArguments(extras);
    callFragment.setArguments(extras);
    callListFragment.setArguments(extras);
    hudFragment.setArguments(intent.getExtras());
    // Activate call and HUD fragments and start the call.
    remoteUserImage.setVisibility(View.INVISIBLE);
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (intent.getAction().equals(ACTION_RESUME_CALL)) {
      ft.add(R.id.call_fragment_container, callFragment);
    }
    else {
      ft.add(R.id.call_fragment_container, initiateCallFragment);
    }
    ft.add(R.id.hud_fragment_container, hudFragment);
    ft.commit();

    // For command line execution run connection for <runTimeMs> and exit.
    if (commandLineRun && runTimeMs > 0) {
      (new Handler()).postDelayed(new Runnable() {
        @Override
        public void run() {
          disconnect();
        }
      }, runTimeMs);
    }

    if (!intent.getAction().equals(ACTION_RESUME_CALL)) {
      peerConnectionClient = PeerConnectionClient.getInstance();

      PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
      options.disableNetworkMonitor = true;
      peerConnectionClient.setPeerConnectionFactoryOptions(options);


      if (mForceTurn) {
        peerConnectionParameters.forceTurn = true;
      }

      peerConnectionClient.createPeerConnectionFactory(
              CallActivity.this, peerConnectionParameters, CallActivity.this);

      peerConnectionClient.setDataChannelCallback(this);

      ThumbnailsCacheManager.ThumbnailsCacheManagerInit(this);
      handleIntent(intent);

      if (screencaptureEnabled) {
        /*MediaProjectionManager mediaProjectionManager =
            (MediaProjectionManager) getApplication().getSystemService(
                Context.MEDIA_PROJECTION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          startActivityForResult(
              mediaProjectionManager.createScreenCaptureIntent(), CAPTURE_PERMISSION_REQUEST_CODE);
        }*/
      } else {
        startCall();
      }
    }
    else {
      mPeerId = intent.getStringExtra(EXTRA_PEER_ID);
      iceConnected = true;
      if (peerConnectionClient != null) {
        peerConnectionClient.updateActivity(this, this);
        peerConnectionClient.updateLocalRenderer(localRender);
        peerConnectionClient.updateRemoteRenderers(remoteRenderers);
      }
    }

    AddRunningIntent();
  }

  private void AddRunningIntent() {
    String notificationText = String.format(getString(R.string.in_call_with), mPeerName);
    NotificationCompat.Builder mBuilder =
            new NotificationCompat.Builder(this)
                    .setSmallIcon(mVideoCallEnabled ? R.drawable.ic_videocam_white_24dp : R.drawable.ic_call_white_48dp)
                    .setContentTitle(getString(R.string.drawer_video_chat))
                    .setOngoing(true)
                    .setContentText(notificationText);

    Intent callIntent = new Intent(getApplicationContext(), CallActivity.class);
    callIntent.setAction(ACTION_RESUME_CALL);
    callIntent.putExtra(CallActivity.EXTRA_VIDEO_CALL, mVideoCallEnabled);
    callIntent.putExtra(CallActivity.EXTRA_PEER_ID, mPeerId);
    callIntent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);

    PendingIntent resultPendingIntent =
            PendingIntent.getActivity(
                    getApplicationContext(),
                    0,
                    callIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT
            );

    mBuilder.setContentIntent(resultPendingIntent);


    // Gets an instance of the NotificationManager service
    NotificationManager mNotifyMgr =
            (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    // Builds the notification and issues it.
    mNotifyMgr.notify(1001, mBuilder.build());
  }

  RemoteConnectionViews getRemoteRenderScreen(String remoteId, String name, String url) {
    if (remoteViewsList.size() != 0) {
      RemoteConnectionViews remoteConnectionViews = remoteViewsList.get(0);
      remoteViewsList.remove(0);
      remoteConnectionViews.setId(remoteId);
      remoteViewsInUseList.add(remoteConnectionViews);

      if (remoteConnectionViews.getTextView() != null) {
        remoteConnectionViews.getTextView().setText(name);
      }
      ThumbnailsCacheManager.LoadImage(url, remoteConnectionViews.getImageView(), name, true, true);
      return remoteConnectionViews;
    }
    return null;
  }

  protected void callUser(User user) {
      mPeerId = user.Id;
      mPeerName = user.displayName;

      ThumbnailsCacheManager.LoadImage(getUrl(user.buddyPicture), remoteUserImage, user.displayName, true, true);
      initiator = true;
      signalingParameters = new SignalingParameters(WebsocketService.getIceServers(), initiator, "", "", "", null, null);
  }


  protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
    if (requestCode == FILE_CODE && resultCode == Activity.RESULT_OK) {
      Uri path = intent.getData();
      long size = 0;
      String name = "";
      ContentResolver cr = this.getContentResolver();
      String mime = cr.getType(path);

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
        size = TokenPeerConnection.getContentSize(path, this);
        name = TokenPeerConnection.getContentName(path, this);

        final int takeFlags = intent.getFlags()
                & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        // Check for the freshest data.
        getContentResolver().takePersistableUriPermission(path, Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      }
      else {
        size = TokenPeerConnection.getContentSize(path, this);
        name = TokenPeerConnection.getContentName(path, this);
      }

      // Do something with the result...
      if (mService != null) {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
        String time = fmt.format(new Date());
        FileInfo fileInfo = new FileInfo("", "", name, String.valueOf(size), mime);
        mService.sendFileMessage(time, mService.getAccountName(), "self", fileInfo, path.toString(), size, name, mime, mFileRecipient, mService.getCurrentRoomName());

        ChatItem item = new ChatItem(time, mService.getAccountName(), fileInfo, "self", mFileRecipient);
        item.setOutgoing();

        chatFragment.addOutgoingMessage(item);
      }

    }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    handleIntent(intent);
  }

  void handleIntent(Intent intent) {
    if (intent == null || intent.getAction() == null) {
      return;
    }


    if (intent.hasExtra(WebsocketService.EXTRA_ADDRESS)) {
      mServer = intent.getStringExtra(WebsocketService.EXTRA_ADDRESS);
    }

    if (intent.getAction().equals(ACTION_SHARE_FILE)) {
      User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
      mFileRecipient = user.Id;
      Intent i;
      if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
        i = new Intent();
        i.setAction(Intent.ACTION_GET_CONTENT);
        i.setType("*/*");
      } else {
        i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        i.setType("*/*");
      }
      startActivityForResult(i, FILE_CODE);
    }
    else if (intent.getAction().equals(ACTION_HANG_UP)) {
      User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
      if (mAdditionalPeers.containsKey(user.Id)) {
        mAdditionalPeers.get(user.Id).getRemoteViews().setId("");
        updateRemoteViewList(mAdditionalPeers.get(user.Id).getRemoteViews());
        mAdditionalPeers.get(user.Id).close();
        mAdditionalPeers.remove(user.Id);
      }
      else if (mPeerId.equals(user.Id)) {
        onCallHangUp();
      }
    }
    else if (intent.getAction().equals(ACTION_TOGGLE_VIDEO)) {
      User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
      boolean enabled = intent.getBooleanExtra(CallActivity.EXTRA_VIDEO_CALL, true);

      if (mAdditionalPeers.containsKey(user.Id)) {
        mAdditionalPeers.get(user.Id).setVideoEnabled(enabled);
      }
      else if (mPeerId.equals(user.Id)) {
        onToggleVideo();
      }
    }
    else if (intent.getAction().equals(ACTION_TOGGLE_MIC)) {
        User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
        boolean enabled = intent.getBooleanExtra(CallActivity.EXTRA_MIC_ENABLED, true);

        if (mAdditionalPeers.containsKey(user.Id)) {
            mAdditionalPeers.get(user.Id).setAudioEnabled(enabled);
        }
        else if (mPeerId.equals(user.Id)) {
            onToggleMic();
        }
    }
    else if (intent.getAction().equals(ACTION_SEND_MESSAGE)) {
        User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
        String messageText = intent.getStringExtra(CallActivity.EXTRA_MESSAGE);

        if (mService != null) {

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
            String time = fmt.format(new Date());
            mService.sendChatMessage(time, mService.getAccountName(), "Self", messageText, user.Id, mService.getCurrentRoomName());
        }
    }
    else if (intent.getAction().equals(ACTION_NEW_CALL)) {

        String signaling = intent.getStringExtra(EXTRA_SIGNALING);
        if (signaling != null) {
          mSignaling = signaling;
        }

        if (mSignaling.equals("xmpp")) {
          mOwnJid = intent.getStringExtra(BroadcastTypes.EXTRA_ACCOUNT_JID);
          mPeerId = intent.getStringExtra(BroadcastTypes.EXTRA_JID);
          mPeerName = mPeerId;

          initiator = true;
          signalingParameters = new SignalingParameters(WebsocketService.getIceServers(), initiator, "", "", "", null, null);
        }
        else {
          User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
          if (intent.hasExtra(WebsocketService.EXTRA_USERACTION)) {
            if (mConferenceId == null) {
              SessionIdentifierGenerator gen = new SessionIdentifierGenerator();
              mConferenceId = mOwnId + "_" + gen.nextSessionId();
            }
          }

          if (mPeerId.length() == 0) {
            callUser(user);
          } else if (!mPeerId.equals(user.Id)) { // don't call if already in call
            AdditionalPeerConnection additionalPeerConnection = new AdditionalPeerConnection(this, this, this, true, user.Id, WebsocketService.getIceServers(), peerConnectionParameters, rootEglBase,
                    localRender, getRemoteRenderScreen(user.Id, user.displayName, getUrl(user.buddyPicture)), peerConnectionClient.getMediaStream(), "", peerConnectionClient.getPeerConnectionFactory());

            mAdditionalPeers.put(user.Id, additionalPeerConnection);
            updateVideoView();
          }
        }
    }
    else if (intent.getAction().equals(WebsocketService.ACTION_ADD_ALL_CONFERENCE)) {
      ArrayList<User> users = getUsers();

      for (User user: users) {
        boolean added = false;
        if (!mPeerId.equals(user.Id) && !mAdditionalPeers.containsKey(user.Id) && (mOwnId.compareTo(user.Id) < 0)) {
          AdditionalPeerConnection additionalPeerConnection = new AdditionalPeerConnection(this, this, this, true, user.Id, WebsocketService.getIceServers(), peerConnectionParameters, rootEglBase,
                  localRender, getRemoteRenderScreen(user.Id, user.displayName, getUrl(user.buddyPicture)), peerConnectionClient.getMediaStream(), mConferenceId, peerConnectionClient.getPeerConnectionFactory());

          mAdditionalPeers.put(user.Id, additionalPeerConnection);
          updateVideoView();
          added = true;
        }

      }

      if (mConferenceId == null) {
        SessionIdentifierGenerator gen = new SessionIdentifierGenerator();
        mConferenceId = mOwnId + "_" + gen.nextSessionId();
      }
    }
    else if (intent.getAction().equals(WebsocketService.ACTION_ADD_CONFERENCE_USER)) {
      User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
      String conferenceId = intent.getStringExtra(WebsocketService.EXTRA_CONFERENCE_ID);
      mOwnId = intent.getStringExtra(WebsocketService.EXTRA_OWN_ID);
      String userId = intent.getStringExtra(WebsocketService.EXTRA_ID);

      if (mConferenceId == null) {
        mConferenceId = conferenceId;
      }
      boolean added = false;
      if ((!mPeerId.equals(userId) && !mAdditionalPeers.containsKey(userId) && (mOwnId.compareTo(userId) < 0))) {
        if (user != null) {
          Log.d(TAG, "Calling conference: " + user.displayName);
        }

        if (peerConnectionClient.getMediaStream() != null) {
          String displayName = getString(R.string.unknown);
          String imgUrl = "";

          if (user != null) {
            displayName = user.displayName;
            imgUrl = getUrl(user.buddyPicture);
          }

          AdditionalPeerConnection additionalPeerConnection = new AdditionalPeerConnection(this, this, this, true, userId, WebsocketService.getIceServers(), peerConnectionParameters, rootEglBase,
                  localRender, getRemoteRenderScreen(userId, displayName, imgUrl), peerConnectionClient.getMediaStream(), mConferenceId, peerConnectionClient.getPeerConnectionFactory());
          mAdditionalPeers.put(userId, additionalPeerConnection);
          updateVideoView();
          added = true;
        }
        else if (mPeerId.length() == 0) {
            // show the call as incoming
          String displayName = getString(R.string.unknown);
          String imgUrl = "";

          if (user != null) {
            displayName = user.displayName;
            imgUrl = getUrl(user.buddyPicture);
          }

          mPeerId = userId;
          mPeerName = displayName;

          ThumbnailsCacheManager.LoadImage(imgUrl, remoteUserImage, displayName, true, true);
          initiator = true;
          signalingParameters = new SignalingParameters(WebsocketService.getIceServers(), initiator, "", "", "", null, null);
        }
        else {
          mQueuedPeers.add(user);
        }
      }

    }
    else if (intent.getAction().equals(WebsocketService.ACTION_REMOTE_ICE_CANDIDATE)) {
     SerializableIceCandidate candidate = (SerializableIceCandidate)intent.getParcelableExtra(WebsocketService.EXTRA_CANDIDATE);
      String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
      String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);

      if (token != null && token.length() != 0) {
        if (mTokenPeers.containsKey(candidate.from)) {
          IceCandidate ic = new IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
          mTokenPeers.get(candidate.from).addRemoteIceCandidate(ic);
        }
      }
      else if (mAdditionalPeers.containsKey(candidate.from)) {
        IceCandidate ic = new IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
        mAdditionalPeers.get(candidate.from).addRemoteIceCandidate(ic);
      }
      else if (id.equals(mSdpId)) {
        onRemoteIceCandidate(candidate, id, candidate.from);
      }

    }
    else if (intent.getAction().equals(WebsocketService.ACTION_REMOTE_DESCRIPTION)) {
      SerializableSessionDescription sdp = (SerializableSessionDescription) intent.getSerializableExtra(WebsocketService.EXTRA_REMOTE_DESCRIPTION);
      String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);
      String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
      String conferenceId = intent.getStringExtra(WebsocketService.EXTRA_CONFERENCE_ID);
      String signaling = intent.getStringExtra(EXTRA_SIGNALING);
      String ownJid = intent.getStringExtra(BroadcastTypes.EXTRA_ACCOUNT_JID);
      mSid = intent.getStringExtra(BroadcastTypes.EXTRA_SID);
      User user = (User)intent.getSerializableExtra(WebsocketService.EXTRA_USER);
      if (mConferenceId == null && conferenceId != null && conferenceId.length() != 0) {
        mConferenceId = conferenceId;
      }
      /*else if (conferenceId != null && mConferenceId != null && !mConferenceId.equals(conferenceId)) {
        // already in a conference, reject the invite
        return;
      }*/

      if (ownJid != null) {
        mOwnJid = ownJid;
      }

      if (signaling != null && signaling.equals("xmpp")) {
        mSignaling = signaling;
      }

      if (token != null && token.length() != 0) {
        if (mTokenPeers.containsKey(sdp.from)) {
          mTokenPeers.get(sdp.from).setRemoteDescription(new SessionDescription(sdp.type, sdp.description));
        }
      }
      else if (mPeerId.length() == 0 || mPeerId.equals(sdp.from)) {

        mSdpId = id;
        mPeerId = sdp.from;
          String imgUrl = "";
        if (user != null) {
            mPeerName = user.displayName;
            imgUrl = getUrl(user.buddyPicture);
        }
        else {
            mPeerName = getString(R.string.unknown);
        }
        ThumbnailsCacheManager.LoadImage(imgUrl, remoteUserImage, mPeerName, true, true);

        if (peerConnectionClient.isConnected()) {
          onRemoteDescription(sdp, token, id, conferenceId, "", "", "");
        } else {
          mRemoteSdp = sdp;
          mToken = token;
        }
      }
      else  {
        if (mAdditionalPeers.containsKey(sdp.from)) {
          mAdditionalPeers.get(sdp.from).setRemoteDescription(new SessionDescription(sdp.type, sdp.description));
        }
        else {
          if (iceConnected && peerConnectionClient.getMediaStream() != null) {
            addToCall(sdp, user);
          }
          else {
            mQueuedRemoteConnections.add(new RemoteConnection(sdp, user));
          }
        }
      }
    }

  }

  String getUrl(String buddyPic) {

    if (buddyPic.length() != 0) {
      String path = buddyPic.substring(4);
      String url = "https://" + getServerAddress() + RoomActivity.BUDDY_IMG_PATH + path;
      return url;
    }
    return "";
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Bind to LocalService
    Intent intent = new Intent(this, WebsocketService.class);
    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
  }

  @Override
  protected void onStop() {
    super.onStop();
    // Unbind from the service
    if (mWebsocketServiceBound) {
      unbindService(mConnection);
      mWebsocketServiceBound = false;
    }
  }

  private boolean useCamera2() {
    return Camera2Enumerator.isSupported(this) && mUseCamera2;
  }

  private boolean captureToTexture() {
    return mCaptureToTexture;
  }

  private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
    final String[] deviceNames = enumerator.getDeviceNames();

    // First, try to find front facing camera
    Logging.d(TAG, "Looking for front facing cameras.");
    for (String deviceName : deviceNames) {
      if (enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating front facing camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    // Front facing camera not found, try something else
    Logging.d(TAG, "Looking for other cameras.");
    for (String deviceName : deviceNames) {
      if (!enumerator.isFrontFacing(deviceName)) {
        Logging.d(TAG, "Creating other camera capturer.");
        VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

        if (videoCapturer != null) {
          return videoCapturer;
        }
      }
    }

    return null;
  }

  // Activity interfaces
  @Override
  public void onPause() {
    super.onPause();
    activityRunning = false;
    // Don't stop the video when using screencapture to allow user to show other apps to the remote
    // end.
    if (peerConnectionClient != null && !screencaptureEnabled) {
      peerConnectionClient.stopVideoSource();
    }
    //cpuMonitor.pause();
  }

  @Override
  public void onResume() {
    super.onResume();
    activityRunning = true;
    // Video is not paused for screencapture. See onPause.
    if (peerConnectionClient != null && !screencaptureEnabled && !disconnected) {
      peerConnectionClient.startVideoSource();
    }
    //cpuMonitor.resume();
  }

  @Override
  protected void onDestroy() {
    disconnect();
    unregisterReceiver(mReceiver);

    if (logToast != null) {
      logToast.cancel();
    }
    activityRunning = false;
    if (disconnected || mFinishCalled) {
      rootEglBase.release();
      rootEglBase = null;
    }



    super.onDestroy();
  }

  // CallFragment.OnCallEvents interface implementation.
  @Override
  public void onCallHangUp() {

    mSoundPlayer = new SoundPlayer(this, R.raw.end1);
    mSoundPlayer.Play(false);

    disconnect();
  }

  @Override
  public void onCameraSwitch() {
    if (peerConnectionClient != null) {
      peerConnectionClient.switchCamera();
    }
  }

  @Override
  public void onVideoScalingSwitch(ScalingType scalingType) {
    this.scalingType = scalingType;
    updateVideoView();
  }

  @Override
  public void onCaptureFormatChange(int width, int height, int framerate) {
    if (peerConnectionClient != null) {
      peerConnectionClient.changeCaptureFormat(width, height, framerate);
    }
  }

  @Override
  public boolean onToggleMic() {
    if (peerConnectionClient != null) {
      micEnabled = !micEnabled;
      peerConnectionClient.setAudioEnabled(micEnabled);
      // mute additionals
      for (HashMap.Entry<String, AdditionalPeerConnection> entry: mAdditionalPeers.entrySet()) {
        AdditionalPeerConnection peer = entry.getValue();
        peer.setAudioEnabled(micEnabled);
      }
    }
    return micEnabled;
  }

  @Override
  public boolean onToggleVideo() {
    if (peerConnectionClient != null) {
      videoEnabled = !videoEnabled;
      peerConnectionClient.setVideoEnabled(videoEnabled);
      // mute additionals
      for (HashMap.Entry<String, AdditionalPeerConnection> entry: mAdditionalPeers.entrySet()) {
        AdditionalPeerConnection peer = entry.getValue();
        peer.setVideoEnabled(videoEnabled);
      }
    }
    return videoEnabled;
  }

  @Override
  public boolean onToggleSpeakerPhone() {
    if (audioManager != null) {
      speakerPhoneEnabled = !speakerPhoneEnabled;
      if (speakerPhoneEnabled) {
        audioManager.selectAudioDevice(AudioDevice.SPEAKER_PHONE);
      }
      else {
        audioManager.selectAudioDevice(AudioDevice.EARPIECE);
      }
    }
    return speakerPhoneEnabled;
  }

  @Override
  public boolean showUserList() {

    userListShown = !userListShown;
    
    if (userListShown) {
      // Show in call fragment
      FragmentTransaction ft = getFragmentManager().beginTransaction();
      ft.add(R.id.call_list_fragment_container, callListFragment);
      ft.commit();
    }
    else {

      // Show in call fragment
      FragmentTransaction ft = getFragmentManager().beginTransaction();
      ft.remove(callListFragment);
      ft.commit();
    }
    return userListShown;
  }

  public void showChatMessages(User user) {

    if (!chatFragment.isAdded()) {
      // Show in call fragment
      android.support.v4.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
      ft.add(R.id.chat_fragment_container, chatFragment);
      ft.commit();
    }

    chatFragment.setAvatarUrl(mService.getAvatarUrl());
    chatFragment.setUser(user);
    chatFragment.viewChat(user.Id);

  }

    @Override
    public void showChatMessages(String roomName, String fromId, User user) {

      if (!chatFragment.isAdded()) {
        // Show in call fragment
        android.support.v4.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
        ft.add(R.id.chat_fragment_container, chatFragment);
        ft.commit();
      }

    }

  // Helper functions.
  private void toggleCallControlFragmentVisibility() {

    updateVideoView();

    if (!iceConnected || !callFragment.isAdded()) {
      return;
    }
    // Show/hide call control fragment
    callControlFragmentVisible = !callControlFragmentVisible;
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (callControlFragmentVisible) {
      ft.show(callFragment);
      ft.show(hudFragment);
    } else {
      ft.hide(callFragment);
      ft.hide(hudFragment);
    }
    ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    ft.commit();
  }

  private void updateVideoView() {

    if (disconnected || mFinishCalled) {
      return;
    }

    int remoteHeight = REMOTE_HEIGHT;
    boolean layoutVideos = true;

    if (screenshareRenderLayout.getVisibility() == View.VISIBLE) {
      if (maximizeScreenshare) {
        layoutVideos = false;
        if (remoteViewsInUseList.size() == 1) {
          remoteViewsInUseList.get(0).setPosition(REMOTE_X, REMOTE_Y, 20, 20);
        }
        else if (remoteViewsInUseList.size() == 2) {
          remoteViewsInUseList.get(0).setPosition(REMOTE_X, REMOTE_Y, 20, 20);
          remoteViewsInUseList.get(1).setPosition(80, 0, 20, 20);
        }
        else if (remoteViewsInUseList.size() == 3) {
          remoteViewsInUseList.get(0).setPosition(REMOTE_X, REMOTE_Y, 20, 20);
          remoteViewsInUseList.get(1).setPosition(80, REMOTE_Y, 20, 20);
          remoteViewsInUseList.get(2).setPosition(REMOTE_X, 80, 20, 20);
        }
        else if (remoteViewsInUseList.size() == 4) {
          remoteViewsInUseList.get(0).setPosition(REMOTE_X, REMOTE_Y, 20, 20);
          remoteViewsInUseList.get(1).setPosition(80, REMOTE_Y, 20, 20);
          remoteViewsInUseList.get(2).setPosition(REMOTE_X, 80, 20, 20);
          remoteViewsInUseList.get(3).setPosition(80, 80, 20, 20);
        }

        screenshareRenderLayout.setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT);
          if (screenshareFrontIndex == 0) {
              screenshareFrontIndex = ((ViewGroup) screenshareRenderLayout.getParent()).indexOfChild(screenshareRenderLayout);
          }
        sendToBack(screenshareRenderLayout);
      }
      else {
        layoutVideos = true;
        bringToFront(screenshareRenderLayout);
        screenshareRenderLayout.setPosition(
                REMOTE_X, LOCAL_Y_CONNECTED, LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED);
      }

      for (RemoteConnectionViews remoteConnectionViews : remoteViewsList) {
        remoteConnectionViews.getFrameLayout().setVisibility(View.INVISIBLE);
      }

      screenshareRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
      screenshareRender.requestLayout();
    }

    if (layoutVideos) {
      if (remoteViewsInUseList.size() == 2) {
        remoteHeight = REMOTE_HEIGHT2;
        remoteViewsInUseList.get(0).setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, remoteHeight);
        remoteViewsInUseList.get(1).setPosition(REMOTE_X, remoteHeight, REMOTE_WIDTH, remoteHeight);
        remoteViewsInUseList.get(1).getFrameLayout().setVisibility(View.VISIBLE);
        remoteViewsInUseList.get(1).getSurfaceViewRenderer().setVisibility(View.VISIBLE);

        for (RemoteConnectionViews remoteConnectionViews : remoteViewsList) {
          remoteConnectionViews.getFrameLayout().setVisibility(View.INVISIBLE);
        }
        Log.i(TAG, "Showing 2 video windows");
      } else if (remoteViewsInUseList.size() == 3) {
        remoteHeight = REMOTE_HEIGHT2;
        remoteViewsInUseList.get(0).setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, remoteHeight);
        remoteViewsInUseList.get(1).setPosition(REMOTE_X, remoteHeight, REMOTE_WIDTH2, remoteHeight);
        remoteViewsInUseList.get(2).setPosition(REMOTE_X2, remoteHeight, REMOTE_WIDTH2, remoteHeight);

        for (RemoteConnectionViews remoteConnectionViews : remoteViewsInUseList) {
          remoteConnectionViews.getFrameLayout().setVisibility(View.VISIBLE);
          remoteConnectionViews.getSurfaceViewRenderer().setVisibility(View.VISIBLE);
        }

        for (RemoteConnectionViews remoteConnectionViews : remoteViewsList) {
          remoteConnectionViews.getFrameLayout().setVisibility(View.INVISIBLE);
        }
        Log.i(TAG, "Showing 3 video windows");
      } else if (remoteViewsInUseList.size() == 4) {
        remoteHeight = REMOTE_HEIGHT2;
        remoteViewsInUseList.get(0).setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH2, remoteHeight);
        remoteViewsInUseList.get(1).setPosition(REMOTE_X2, REMOTE_Y, REMOTE_WIDTH2, remoteHeight);
        remoteViewsInUseList.get(2).setPosition(REMOTE_X, remoteHeight, REMOTE_WIDTH2, remoteHeight);
        remoteViewsInUseList.get(3).setPosition(REMOTE_X2, remoteHeight, REMOTE_WIDTH2, remoteHeight);

        for (RemoteConnectionViews remoteConnectionViews : remoteViewsInUseList) {
          remoteConnectionViews.getFrameLayout().setVisibility(View.VISIBLE);
          remoteConnectionViews.getSurfaceViewRenderer().setVisibility(View.VISIBLE);
        }
        Log.i(TAG, "Showing 4 video windows");
      } else {
        remoteViewsInUseList.get(0).setPosition(REMOTE_X, REMOTE_Y, REMOTE_WIDTH, REMOTE_HEIGHT);
        for (RemoteConnectionViews remoteConnectionViews : remoteViewsList) {
          remoteConnectionViews.getFrameLayout().setVisibility(View.INVISIBLE);
          remoteConnectionViews.getSurfaceViewRenderer().setVisibility(View.INVISIBLE);
        }
        Log.i(TAG, "Showing 1 video windows");
      }
    }

    for (RemoteConnectionViews remoteConnectionViews: remoteViewsInUseList) {
      remoteConnectionViews.getSurfaceViewRenderer().setScalingType(scalingType);
      remoteConnectionViews.getSurfaceViewRenderer().setMirror(false);
    }

    if (mVideoCallEnabled) {
      if (iceConnected) {
        //localRenderLayout.setVisibility(View.GONE);
        localRenderLayout.setPosition(
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED, LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED);
        localRender.setScalingType(ScalingType.SCALE_ASPECT_FIT);
        Log.i(TAG, "Showing small local video window");
      } else {
        localRenderLayout.setVisibility(View.VISIBLE);
        localRenderLayout.setPosition(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING, LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING);
        localRender.setScalingType(scalingType);
        Log.i(TAG, "Showing large local video window");
      }
      localRender.setMirror(true);

      localRender.requestLayout();
    }
    else {
      localRenderLayout.setVisibility(View.GONE);
    }


    for (RemoteConnectionViews remoteConnectionViews: remoteViewsInUseList) {
      remoteConnectionViews.getSurfaceViewRenderer().requestLayout();
    }

  }

  void sendToBack(View child) {
    final ViewGroup parent = (ViewGroup) child.getParent();
    if (null != parent) {
      parent.removeView(child);
      parent.addView(child, 0);
    }
  }

  void bringToFront(View child) {
    final ViewGroup parent = (ViewGroup) child.getParent();
    if (null != parent) {
      parent.removeView(child);
      parent.addView(child, screenshareFrontIndex);
    }
  }

  private void startCall() {

    callStartedTimeMs = System.currentTimeMillis();

    // Start room connection.
    //logAndToast(getString(R.string.connecting_to, roomConnectionParameters.roomUrl));
    //mService.connectToRoom(roomConnectionParameters);

    // Create and audio manager that will take care of audio routing,
    // audio modes, audio device enumeration etc.
    audioManager = AppRTCAudioManager.create(getApplicationContext());
    // Store existing audio settings and change audio mode to
    // MODE_IN_COMMUNICATION for best possible VoIP performance.
    Log.d(TAG, "Starting the audio manager...");
    audioManager.start(new AudioManagerEvents() {
      // This method will be called each time the number of available audio
      // devices has changed.
      @Override
      public void onAudioDeviceChanged(
          AudioDevice audioDevice, Set<AudioDevice> availableAudioDevices) {
        onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
      }
    });
  }

  // Should be called from UI thread
  private void callConnected() {
      connectedUserIds.add(mPeerId);
      connectedUserIds.add(mOwnId);

    LinearLayout connecting_layout = (LinearLayout) findViewById(R.id.connecting_progress_layout);
    if (connecting_layout != null) {
      connecting_layout.setVisibility(View.GONE);
    }

    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    Log.i(TAG, "Call connected: delay=" + delta + "ms");
    if (peerConnectionClient == null || isError) {
      Log.w(TAG, "Call is connected in closed or error state");
      return;
    }
    // Update video view.
    updateVideoView();

    // incoming calls pending
    for (RemoteConnection remoteConnection: mQueuedRemoteConnections) {
      addToCall(remoteConnection.sdp, remoteConnection.user);
    }
    mQueuedRemoteConnections.clear();

    for (User user: mQueuedPeers) {
      addToCall(user, "", true, null);
    }
    mQueuedPeers.clear();

    // Enable statistics callback.
    //peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
  }

  // This method is called when the audio manager reports audio device change,
  // e.g. from wired headset to speakerphone.
  private void onAudioManagerDevicesChanged(
      final AudioDevice device, final Set<AudioDevice> availableDevices) {
    Log.d(TAG, "onAudioManagerDevicesChanged: " + availableDevices + ", "
            + "selected: " + device);
    // TODO(henrika): add callback handler.
  }

  // Disconnect from remote resources, dispose of local resources, and exit.
  private void disconnect() {

    if (!disconnected) {
      // Gets an instance of the NotificationManager service
      NotificationManager mNotifyMgr =
              (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
      mNotifyMgr.cancel(1001);

      disconnected = true; // only call disconnect once per activity
      activityRunning = false;
      if (mService != null) {
        mService.sendBye(mPeerId);
      }

      for (HashMap.Entry<String, AdditionalPeerConnection> entry: mTokenPeers.entrySet()) {
        AdditionalPeerConnection additionalPeerConnection = entry.getValue();
        additionalPeerConnection.close();

        if (mService != null) {
          mService.sendBye(entry.getKey());
        }
      }

      for (HashMap.Entry<String, AdditionalPeerConnection> entry: mAdditionalPeers.entrySet()) {
        AdditionalPeerConnection additionalPeerConnection = entry.getValue();
        additionalPeerConnection.close();

        if (mService != null) {
          mService.sendBye(entry.getKey());
        }
      }

      if (peerConnectionClient != null) {
        peerConnectionClient.close();
        peerConnectionClient = null;
      }

      remoteViewsList.clear();
      remoteViewsInUseList.clear();

      if (localRender != null) {
        localRender.release();
        localRender = null;
      }
      if (screenshareRender != null) {
        screenshareRender.release();
        screenshareRender = null;
      }
      if (videoFileRenderer != null) {
        videoFileRenderer.release();
        videoFileRenderer = null;
      }
      if (remoteRenderScreen != null) {
        remoteRenderScreen.release();
        remoteRenderScreen = null;
      }
      if (remoteRenderScreen2 != null) {
        remoteRenderScreen2.release();
        remoteRenderScreen2 = null;
      }
      if (remoteRenderScreen3 != null) {
        remoteRenderScreen3.release();
        remoteRenderScreen3 = null;
      }
      if (remoteRenderScreen4 != null) {
        remoteRenderScreen4.release();
        remoteRenderScreen4 = null;
      }
      if (audioManager != null) {
        audioManager.stop();
        audioManager = null;
      }
      if (iceConnected && !isError) {
        setResult(RESULT_OK);
      } else {
        setResult(RESULT_CANCELED);
      }
    }
  }

  private void disconnectWithErrorMessage(final String errorMessage) {
    if (commandLineRun || !activityRunning) {
      Log.e(TAG, "Critical error: " + errorMessage);
      disconnect();
    } else {
      new AlertDialog.Builder(this)
          .setTitle(getText(R.string.channel_error_title))
          .setMessage(errorMessage)
          .setCancelable(false)
          .setNeutralButton(R.string.ok,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int id) {
                  dialog.cancel();
                  //disconnect();
                }
              })
          .create()
          .show();
    }
  }

  // Log |msg| and Toast about it.
  private void logAndToast(String msg) {
    Log.d(TAG, msg);
    if (logToast != null) {
      logToast.cancel();
    }
    logToast = Toast.makeText(this, msg, Toast.LENGTH_SHORT);
    logToast.show();
  }

  private void showHoldMessage(final boolean on) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        remoteViewsInUseList.get(0).setHoldStatus(on);
        if (on) {
          callListFragment.updateUserState(User.CallState.HOLD, mPeerId);

        } else {
          callListFragment.updateUserState(User.CallState.CONNECTED, mPeerId);
        }
      }
    });
  }

  private void reportError(final String description) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        /*if (!isError) {
          isError = true;
          disconnectWithErrorMessage(description);
        }*/
        logAndToast(description);
      }
    });
  }

  protected VideoCapturer createVideoCapturer() {
    VideoCapturer videoCapturer = null;
    String videoFileAsCamera = getIntent().getStringExtra(EXTRA_VIDEO_FILE_AS_CAMERA);
    if (videoFileAsCamera != null) {
      try {
        videoCapturer = new FileVideoCapturer(videoFileAsCamera);
      } catch (IOException e) {
        reportError("Failed to open video file for emulated camera");
        return null;
      }
    } else if (screencaptureEnabled) {
      if (mediaProjectionPermissionResultCode != Activity.RESULT_OK) {
        reportError("User didn't give permission to capture the screen.");
        return null;
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        return new ScreenCapturerAndroid(
            mediaProjectionPermissionResultData, new MediaProjection.Callback() {
              @Override
              public void onStop() {
                reportError("User revoked permission to capture the screen.");
              }
            });
      }
    } else if (useCamera2()) {
      if (!captureToTexture()) {
        mCaptureToTexture = true;
      }

      Logging.d(TAG, "Creating capturer using camera2 API.");
      videoCapturer = createCameraCapturer(new Camera2Enumerator(this));
    } else {
      Logging.d(TAG, "Creating capturer using camera1 API.");
      videoCapturer = createCameraCapturer(new Camera1Enumerator(captureToTexture()));
    }
    if (videoCapturer == null) {
      reportError("Failed to open camera");
      return null;
    }
    return videoCapturer;
  }

  private void setupCall(SignalingParameters params) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;

    signalingParameters = params;
   // logAndToast("Creating peer connection, delay=" + delta + "ms");
    VideoCapturer videoCapturer = null;
    if (peerConnectionParameters.videoCallEnabled) {
      videoCapturer = createVideoCapturer();
    }

    if (peerConnectionClient != null) {
      peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(), localRender,
              remoteRenderers, videoCapturer, signalingParameters);

      remoteVideoLabel.setText(mPeerName);

      if (signalingParameters.initiator) {
        // logAndToast("Creating OFFER...");
        // Create offer. Offer SDP will be sent to answering client in
        // PeerConnectionEvents.onLocalDescription event.
        peerConnectionClient.createOffer();
      } else {
        if (params.offerSdp != null) {
          peerConnectionClient.setRemoteDescription(params.offerSdp, params.token);
          //  logAndToast("Creating ANSWER...");
          // Create answer. Answer SDP will be sent to offering client in
          // PeerConnectionEvents.onLocalDescription event.
          peerConnectionClient.createAnswer();
        }
        if (params.iceCandidates != null) {
          // Add remote ICE candidates from room.
          for (IceCandidate iceCandidate : params.iceCandidates) {
            peerConnectionClient.addRemoteIceCandidate(iceCandidate);
          }
        }
      }
    }
  }

  // -----Implementation of AppRTCClient.AppRTCSignalingEvents ---------------
  // All callbacks are invoked from websocket signaling looper thread and
  // are routed to UI thread.
  private void onConnectedToRoomInternal(final String roomName) {

  }

  @Override
  public void onConnectedToRoom(final String roomName) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        onConnectedToRoomInternal(roomName);
      }
    });
  }

  @Override
  public void onRemoteDescription(final SerializableSessionDescription sdp, final String token, String id, String conferenceId, final String fromId, final String roomName, String type) {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
          return;
        }
        //logAndToast("Received remote " + sdp.type + ", delay=" + delta + "ms");
        SessionDescription sd = new SessionDescription(sdp.type, sdp.description);
        peerConnectionClient.setRemoteDescription(sd, token);
        if (!initiator) {
          //logAndToast("Creating ANSWER...");
          // Create answer. Answer SDP will be sent to offering client in
          // PeerConnectionEvents.onLocalDescription event.
          peerConnectionClient.createAnswer();
        }
        else if (sd.type == SessionDescription.Type.OFFER) {
          // tried to call
          SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
          fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
          String time = fmt.format(new Date());
          String missedCall = String.format(getString(R.string.missed_call), mPeerName);

          mService.onChatMessage(missedCall, time, "", mOwnId, mPeerId, roomName);
          sendBye(fromId, "busy");
          onCallHangUp();
        }
        else {

          // Show in call fragment
          FragmentTransaction ft = getFragmentManager().beginTransaction();
          if (!mVideoCallEnabled) {
            ft.add(R.id.call_list_fragment_container, callListFragment);
          }
          ft.replace(R.id.call_fragment_container, callFragment);
          ft.commit();

          if (!mVideoCallEnabled) {
            remoteUserImage.setVisibility(View.VISIBLE);
          }
        }
      }
    });
  }

  @Override
  public void onRemoteIceCandidate(final SerializableIceCandidate candidate, String id, String from) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
          return;
        }

        IceCandidate ic = new IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
        peerConnectionClient.addRemoteIceCandidate(ic);
      }
    });
  }

  @Override
  public void onRemoteIceCandidatesRemoved(final SerializableIceCandidate[] candidates) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (peerConnectionClient == null) {
          Log.e(TAG, "Received ICE candidate removals for a non-initialized peer connection.");
          return;
        }
        IceCandidate[] ic = new IceCandidate[candidates.length];
        for (int candidate = 0; candidate < candidates.length; candidate++) {
          ic[candidate] = new IceCandidate(candidates[candidate].sdpMid, candidates[candidate].sdpMLineIndex, candidates[candidate].sdp);
        }
        peerConnectionClient.removeRemoteIceCandidates(ic);
      }
    });
  }

  @Override
  public void onChannelOpen() {

  }

  @Override
  public void onChannelClose() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        //logAndToast("Remote end hung up; dropping PeerConnection");
        disconnect();
      }
    });
  }

  @Override
  public void onChannelError(final String description) {
    reportError(description);
  }

  @Override
  public void clearRoomUsers(String room) {

  }

  @Override
  public void onUserEnteredRoom(User user, String room) {

  }

  @Override
  public void onUserLeftRoom(User user, String room) {

  }

  @Override
  public void onBackPressed() {
    if (chatFragment.isAdded()) {
      // Show in call fragment
      android.support.v4.app.FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
      ft.remove(chatFragment);
      ft.commit();

    }
    else {
      super.onBackPressed();
    }
  }

  @Override
  public void onBye(final String reason, String fromId, String roomName) {

    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast(reason);
        disconnect();
      }
    });
  }

  @Override
  public void sendBye(String mPeerId) {

  }

  @Override
  public void sendBye(String mPeerId, String reason) {

  }

  @Override
  public void onPatchResponse(String response) {

  }

  @Override
  public void onPostResponse(String response) {

  }

  @Override
  public void onConfigResponse(String response) {

  }

  @Override
  public void onChatMessage(String message, String time, String status, String to, String fromId, String roomName) {

  }

  @Override
  public void onFileMessage(String time, String id, String chunks, String name, String size, String filetype, String mIdFrom, String mRoomName) {

  }

  @Override
  public void onAddTurnServer(String url, String username, String password) {

  }

  @Override
  public void onAddStunServer(String url, String username, String password) {

  }

  @Override
  public void onSelf() {

  }

  @Override
  public void onTurnTtl(int ttl) {

  }

  @Override
  public void onConferenceUser(String roomName, String conferenceId, String id) {

  }

  @Override
  public void onError(String code, String message, String roomName) {

  }

  @Override
  public void onScreenShare(final String userId, final String id, String roomName) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        ArrayList<User> users = getUsers();

        if (users != null) {
          for (User user: users) {
            if (user.Id.equals(userId)) {
              String label = "";
              label = String.format(getString(R.string.screenshare_offer), user.displayName);

              final User screenshareUser = user;
              Snackbar snackbar = Snackbar
                      .make(findViewById(R.id.snackbar_container), label, Snackbar.LENGTH_INDEFINITE)
                      .setAction(getString(R.string.accept), new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                          addToCall(screenshareUser, id, false, null);
                        }
                      });

              snackbar.show();

              break;
            }
          }
        }
      }
    });

  }

  public void onScreenShare(final String userId, final String id, final PeerConnectionClient signalingClient) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        ArrayList<User> users = getUsers();

        if (users != null) {
          for (User user: users) {
            if (user.Id.equals(userId)) {
              String label = "";
              label = String.format(getString(R.string.screenshare_offer), user.displayName);

              final User screenshareUser = user;
              Snackbar snackbar = Snackbar
                      .make(findViewById(R.id.snackbar_container), label, Snackbar.LENGTH_INDEFINITE)
                      .setAction(getString(R.string.accept), new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                          addToCall(screenshareUser, id, false, null);
                        }
                      });

              snackbar.show();

              break;
            }
          }
        }
      }
    });

  }

  // -----Implementation of PeerConnectionClient.PeerConnectionEvents.---------
  // Send local peer connection SDP and ICE candidates to remote party.
  // All callbacks are invoked from peer connection client looper thread and
  // are routed to UI thread.
  @Override
  public void onLocalDescription(final SessionDescription sdp) {
    mLocalSdp = sdp;
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (mService != null) {
          //logAndToast("Sending " + sdp.type + ", delay=" + delta + "ms");
          if (initiator) {
           // mService.sendOfferSdp(sdp);
          } else {
            if (mAnswerPressed && !mSentAnswer) {
                AnswerIncomingCall(mPeerId);
            }
          }
        }

        if (mWaitingToStartCall) {
          mWaitingToStartCall = false;
          StartCall(mPeerId);
        }

        if (initiateCallFragment != null) {
          initiateCallFragment.enableCallButtons();
        }



        if (peerConnectionParameters.videoMaxBitrate > 0) {
          Log.d(TAG, "Set video maximum bitrate: " + peerConnectionParameters.videoMaxBitrate);
          peerConnectionClient.setVideoMaxBitrate(peerConnectionParameters.videoMaxBitrate);
        }
      }
    });
  }

  @Override
  public void onIceCandidate(final SerializableIceCandidate candidate) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        mIceCandidates.put(candidate.sdp, candidate);
        if (!mWaitingToStartCall && mService != null) {
          mService.sendLocalIceCandidate(candidate, "", "", mPeerId);
        }
        if (initiateCallFragment != null) {
          initiateCallFragment.enableCallButtons();
        }

        if (mWaitingToStartCall) {
          mWaitingToStartCall = false;
          StartCall(mPeerId);
        }
      }
    });
  }

  @Override
  public void onIceCandidatesRemoved(final SerializableIceCandidate[] candidates) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (mService != null) {
          mService.sendLocalIceCandidateRemovals(candidates, mPeerId);
        }
      }
    });
  }

  @Override
  public void onVideoEnabled() {
      remoteUserImage.setVisibility(View.INVISIBLE);
  }

  @Override
  public void onVideoDisabled() {
      remoteUserImage.setVisibility(View.VISIBLE);
  }

  @Override
  public void onIceConnected() {
    final long delta = System.currentTimeMillis() - callStartedTimeMs;
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        //logAndToast("ICE connected, delay=" + delta + "ms");
        iceConnected = true;
        callConnected();
        callListFragment.updateUserState(User.CallState.CONNECTED, mPeerId);
      }
    });




  }

  private void addToCall(SerializableSessionDescription sdp, User user) {
    String id = sdp.from;
    String displayName = getString(R.string.unknown);
    String imgUrl = "";

    if (user != null) {
        displayName = user.displayName;
        imgUrl = getUrl(user.buddyPicture);
    }

    AdditionalPeerConnection additionalPeerConnection = new AdditionalPeerConnection(this, this, this, false, sdp.from, WebsocketService.getIceServers(), peerConnectionParameters, rootEglBase,
            localRender, getRemoteRenderScreen(id, displayName, imgUrl), peerConnectionClient.getMediaStream(), mConferenceId, peerConnectionClient.getPeerConnectionFactory());

    additionalPeerConnection.setRemoteDescription(new SessionDescription(sdp.type, sdp.description));
    mAdditionalPeers.put(sdp.from, additionalPeerConnection);
    updateVideoView();
  }

  private void addToCall(User user, String token, boolean sendMedia, PeerConnectionClient signalingClient) {

    AdditionalPeerConnection additionalPeerConnection = new AdditionalPeerConnection(this, this, this, true, user.Id, WebsocketService.getIceServers(), peerConnectionParameters, rootEglBase,
            localRender, screenshareRemoteView, token, mConferenceId, peerConnectionClient.getPeerConnectionFactory());
    additionalPeerConnection.setToken(token, signalingClient);
    if (token.length() != 0) {
      mTokenPeers.put(user.Id, additionalPeerConnection);
      screenshareRenderLayout.setVisibility(View.VISIBLE);
      screenshareRender.setVisibility(View.VISIBLE);
    }
    else {
      mAdditionalPeers.put(user.Id, additionalPeerConnection);
    }
    updateVideoView();
  }

  @Override
  public void onIceDisconnected() {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        logAndToast("ICE disconnected");
        iceConnected = false;
        //disconnect();
      }
    });
  }

  @Override
  public void onPeerConnectionClosed() {
    if (!mFinishCalled) {
      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          finish();
        }
      });
      mFinishCalled = true;
    }
  }

  @Override
  public void onPeerConnectionStatsReady(final StatsReport[] reports) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (!isError && iceConnected) {
          hudFragment.updateEncoderStatistics(reports);
        }
      }
    });
  }

  @Override
  public void onPeerConnectionError(final String description) {
    reportError(description);
  }

  @Override
  public void onPeerConnectionFactoryCreated() {

    runOnUiThread(new Runnable() {

      @Override
      public void run() {
        if (mRemoteSdp != null) {
          SessionDescription sdp = new SessionDescription(mRemoteSdp.type, mRemoteSdp.description);
          signalingParameters.offerSdp = sdp;
        }
        if (mToken != null) {
          signalingParameters.token = mToken;
        }
        setupCall(signalingParameters);
      }
    });

  }

  @Override
  public void onPeerConnectionCreated() {

  }

  @Override
  public void onRemoteSdpSet() {

  }

  void AnswerIncomingCall(String to) {
    mAnswerPressed = true;

    if (mLocalSdp != null) {
      if (mService != null && mSignaling.equals("spreed")) {
        mService.sendAnswerSdp(mLocalSdp, to);
        for (HashMap.Entry<String, SerializableIceCandidate> entry: mIceCandidates.entrySet()) {
          SerializableIceCandidate candidate = entry.getValue();
          mService.sendLocalIceCandidate(candidate, mToken, mSdpId, mPeerId);
        }
        mIceCandidates.clear();
        mSentAnswer = true;
      }
      else if (mSignaling.equals("xmpp")) {
        SerializableSessionDescription sdp = new SerializableSessionDescription(mLocalSdp.type, mLocalSdp.description, "");
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_SEND_SESSION_DESCRIPTION);
        broadcastIntent.putExtra(BroadcastTypes.EXTRA_REMOTE_DESCRIPTION, sdp);
        broadcastIntent.putExtra(BroadcastTypes.EXTRA_JID, mPeerId);
        broadcastIntent.putExtra(BroadcastTypes.EXTRA_ACCOUNT_JID, mOwnJid);
        broadcastIntent.putExtra(BroadcastTypes.EXTRA_SID, mSid);
        broadcastIntent.putExtra(EXTRA_SIGNALING, "xmpp");
        ArrayList<SerializableIceCandidate> candidates = new ArrayList<SerializableIceCandidate>();
        for (HashMap.Entry<String, SerializableIceCandidate> entry: mIceCandidates.entrySet()) {
          SerializableIceCandidate candidate = entry.getValue();
          candidates.add(candidate);
        }
        broadcastIntent.putExtra(EXTRA_CANDIDATES, candidates);
        sendBroadcast(broadcastIntent);
        mIceCandidates.clear();
        mSentAnswer = true;
      }
    }
  }

  void StartCall(String to) {
    if (mSignaling.equals("spreed")) {
      if (mService == null) {
        return;
      }


      if (mConferenceId != null) {
        // send the conference invites
        mService.sendConferenceOfferSdp(mLocalSdp, to, mConferenceId);
      } else {
        mService.sendOfferSdp(mLocalSdp, to);
      }

      for (HashMap.Entry<String, SerializableIceCandidate> entry : mIceCandidates.entrySet()) {
        SerializableIceCandidate candidate = entry.getValue();
        mService.sendLocalIceCandidate(candidate, mToken, mSdpId, mPeerId);
      }
      mIceCandidates.clear();
    }
    else if (mSignaling.equals("xmpp")) {
      SerializableSessionDescription sdp = new SerializableSessionDescription(mLocalSdp.type, mLocalSdp.description, "");
      Intent broadcastIntent = new Intent();
      broadcastIntent.setAction(ACTION_SEND_SESSION_DESCRIPTION);
      broadcastIntent.putExtra(BroadcastTypes.EXTRA_REMOTE_DESCRIPTION, sdp);
      broadcastIntent.putExtra(BroadcastTypes.EXTRA_JID, mPeerId);
      broadcastIntent.putExtra(BroadcastTypes.EXTRA_ACCOUNT_JID, mOwnJid);
      broadcastIntent.putExtra(BroadcastTypes.EXTRA_SID, mSid);
      broadcastIntent.putExtra(EXTRA_SIGNALING, "xmpp");
      ArrayList<SerializableIceCandidate> candidates = new ArrayList<SerializableIceCandidate>();
      for (HashMap.Entry<String, SerializableIceCandidate> entry: mIceCandidates.entrySet()) {
        SerializableIceCandidate candidate = entry.getValue();
        candidates.add(candidate);
      }
      broadcastIntent.putExtra(EXTRA_CANDIDATES, candidates);
      sendBroadcast(broadcastIntent);
      mIceCandidates.clear();
      mSentAnswer = true;
    }
  }

  public String getPeerId() {
    return mPeerId;
  }

  public void onRejectCall() {
    mService.sendBye(mPeerId);
    if (!mFinishCalled) {
      mFinishCalled = true;
      disconnect();
      finish();
    }
  }

  @Override
  public void onAnswerCall() {
    AnswerIncomingCall(mPeerId);
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    if (!mVideoCallEnabled) {
      ft.add(R.id.call_list_fragment_container, callListFragment);
    }
    ft.replace(R.id.call_fragment_container, callFragment);
    ft.commit();

    if (!mVideoCallEnabled) {
      remoteUserImage.setVisibility(View.VISIBLE);
    }
  }

  @Override
  public void onStartCall() {
    mWaitingToStartCall = true;
  }

  @Override
  public void onStartConferenceCall(User user) {
    FragmentTransaction ft = getFragmentManager().beginTransaction();
    ft.replace(R.id.call_fragment_container, callFragment);
    ft.commit();

    if (!mVideoCallEnabled) {
      remoteUserImage.setVisibility(View.VISIBLE);
    }

    callUser(user);
    updateVideoView();

    if (mLocalSdp == null) {
      mWaitingToStartCall = true;
    }
    else {
      StartCall(mPeerId);
    }
  }


  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  private boolean sharedPrefGetBoolean(
          int attributeId, String intentName, int defaultId) {
    boolean defaultValue = Boolean.valueOf(getString(defaultId));

  Intent intent = getIntent();
    if (intent != null && intent.hasExtra(intentName)) {
      return intent.getBooleanExtra(intentName, defaultValue);
    }

    String attributeName = getString(attributeId);
    return sharedPref.getBoolean(attributeName, defaultValue);
  
  }

  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  private String sharedPrefGetString(
          int attributeId, String intentName, int defaultId) {
    String defaultValue = getString(defaultId);
    String attributeName = getString(attributeId);
    return sharedPref.getString(attributeName, defaultValue);

  }

  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  private int sharedPrefGetInteger(
          int attributeId, String intentName, int defaultId) {
    String defaultString = getString(defaultId);
    int defaultValue = Integer.parseInt(defaultString);

    String attributeName = getString(attributeId);
    String value = sharedPref.getString(attributeName, defaultString);
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      Log.e(TAG, "Wrong setting for: " + attributeName + ":" + value);
      return defaultValue;
    }

  }

  private void readPrefs() {

    speakerPhoneEnabled = !sharedPrefGetString(R.string.pref_speakerphone_key, "", R.string.pref_speakerphone_default).equals("false");

    // for testing
    mForceTurn = sharedPrefGetBoolean(R.string.pref_force_relay_key, "", R.string.pref_force_relay_default);

    // Video call enabled flag.
    mVideoCallEnabled = sharedPrefGetBoolean(R.string.pref_videocall_key,
            CallActivity.EXTRA_VIDEO_CALL, R.string.pref_videocall_default);

    // Use screencapture option.
    screencaptureEnabled = sharedPrefGetBoolean(R.string.pref_screencapture_key,
            CallActivity.EXTRA_SCREENCAPTURE, R.string.pref_screencapture_default);

    // Use Camera2 option.
    mUseCamera2 = sharedPrefGetBoolean(R.string.pref_camera2_key, CallActivity.EXTRA_CAMERA2,
            R.string.pref_camera2_default);

    // Get default codecs.
    mVideoCodec = sharedPrefGetString(R.string.pref_videocodec_key,
            CallActivity.EXTRA_VIDEOCODEC, R.string.pref_videocodec_default);
    mAudioCodec = sharedPrefGetString(R.string.pref_audiocodec_key,
            CallActivity.EXTRA_AUDIOCODEC, R.string.pref_audiocodec_default);

    // Check HW codec flag.
    mHwCodecEnabled = sharedPrefGetBoolean(R.string.pref_hwcodec_key,
            CallActivity.EXTRA_HWCODEC_ENABLED, R.string.pref_hwcodec_default);

    // Check Capture to texture.
    mCaptureToTexture = sharedPrefGetBoolean(R.string.pref_capturetotexture_key,
            CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, R.string.pref_capturetotexture_default);

    // Check FlexFEC.
    mFlexfecEnabled = sharedPrefGetBoolean(R.string.pref_flexfec_key,
            CallActivity.EXTRA_FLEXFEC_ENABLED, R.string.pref_flexfec_default);

    // Check Disable Audio Processing flag.
    mNoAudioProcessing = sharedPrefGetBoolean(R.string.pref_noaudioprocessing_key,
            CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, R.string.pref_noaudioprocessing_default);

    // Check Disable Audio Processing flag.
    mAecDump = sharedPrefGetBoolean(R.string.pref_aecdump_key,
            CallActivity.EXTRA_AECDUMP_ENABLED, R.string.pref_aecdump_default);

    // Check OpenSL ES enabled flag.
    mUseOpenSLES = sharedPrefGetBoolean(R.string.pref_opensles_key,
            CallActivity.EXTRA_OPENSLES_ENABLED, R.string.pref_opensles_default);

    // Check Disable built-in AEC flag.
    mDisableBuiltInAEC = sharedPrefGetBoolean(R.string.pref_disable_built_in_aec_key,
            CallActivity.EXTRA_DISABLE_BUILT_IN_AEC, R.string.pref_disable_built_in_aec_default);

    // Check Disable built-in AGC flag.
    mDisableBuiltInAGC = sharedPrefGetBoolean(R.string.pref_disable_built_in_agc_key,
            CallActivity.EXTRA_DISABLE_BUILT_IN_AGC, R.string.pref_disable_built_in_agc_default);

    // Check Disable built-in NS flag.
    mDisableBuiltInNS = sharedPrefGetBoolean(R.string.pref_disable_built_in_ns_key,
            CallActivity.EXTRA_DISABLE_BUILT_IN_NS, R.string.pref_disable_built_in_ns_default);

    // Check Enable level control.
    mEnableLevelControl = sharedPrefGetBoolean(R.string.pref_enable_level_control_key,
            CallActivity.EXTRA_ENABLE_LEVEL_CONTROL, R.string.pref_enable_level_control_key);

    // Get video resolution from settings.
    if (mVideoWidth == 0 && mVideoHeight == 0) {
      String resolution =
              sharedPref.getString(keyprefResolution, getString(R.string.pref_resolution_default));

      if (resolution.equals("Default")) {

        mVideoWidth = 320;
        mVideoHeight = 240;
      }
      else {
        String[] dimensions = resolution.split("[ x]+");
        if (dimensions.length == 2) {
          try {
            mVideoWidth = Integer.parseInt(dimensions[0]);
            mVideoHeight = Integer.parseInt(dimensions[1]);
          } catch (NumberFormatException e) {
            mVideoWidth = 0;
            mVideoHeight = 0;
            Log.e(TAG, "Wrong video resolution setting: " + resolution);
          }
        }
      }
    }

    // Get camera fps from settings.
    if (mCameraFps == 0) {
      String fps = sharedPref.getString(keyprefFps, getString(R.string.pref_fps_default));
      String[] fpsValues = fps.split("[ x]+");
      if (fpsValues.length == 2) {
        try {
          mCameraFps = Integer.parseInt(fpsValues[0]);
        } catch (NumberFormatException e) {
          mCameraFps = 0;
          Log.e(TAG, "Wrong camera fps setting: " + fps);
        }
      }
    }

    // Check capture quality slider flag.
    mCaptureQualitySlider = sharedPrefGetBoolean(R.string.pref_capturequalityslider_key,
            CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED,
            R.string.pref_capturequalityslider_default);

    // Get video and audio start bitrate.

    if (mVideoStartBitrate == 0) {
      String bitrateTypeDefault = getString(R.string.pref_maxvideobitrate_default);
      String bitrateType = sharedPref.getString(keyprefVideoBitrateType, bitrateTypeDefault);
      if (!bitrateType.equals(bitrateTypeDefault)) {
        String bitrateValue = sharedPref.getString(
                keyprefVideoBitrateValue, getString(R.string.pref_maxvideobitratevalue_default));
        mVideoStartBitrate = Integer.parseInt(bitrateValue);
      }
    }


    if (mAudioStartBitrate == 0) {
      String bitrateTypeDefault = getString(R.string.pref_startaudiobitrate_default);
      String bitrateType = sharedPref.getString(keyprefAudioBitrateType, bitrateTypeDefault);
      if (!bitrateType.equals(bitrateTypeDefault)) {
        String bitrateValue = sharedPref.getString(
                keyprefAudioBitrateValue, getString(R.string.pref_startaudiobitratevalue_default));
        mAudioStartBitrate = Integer.parseInt(bitrateValue);
      }
    }

    // Check statistics display option.
    mDisplayHud = sharedPrefGetBoolean(R.string.pref_displayhud_key,
            CallActivity.EXTRA_DISPLAY_HUD, R.string.pref_displayhud_default);

    mTracing = sharedPrefGetBoolean(R.string.pref_tracing_key, CallActivity.EXTRA_TRACING,
            R.string.pref_tracing_default);

    // Get datachannel options
    mDataChannelEnabled = sharedPrefGetBoolean(R.string.pref_enable_datachannel_key,
            CallActivity.EXTRA_DATA_CHANNEL_ENABLED, R.string.pref_enable_datachannel_default);
    mOrdered = sharedPrefGetBoolean(R.string.pref_ordered_key, CallActivity.EXTRA_ORDERED,
            R.string.pref_ordered_default);
    mNegotiated = sharedPrefGetBoolean(R.string.pref_negotiated_key,
            CallActivity.EXTRA_NEGOTIATED, R.string.pref_negotiated_default);
    mMaxRetrMs = sharedPrefGetInteger(R.string.pref_max_retransmit_time_ms_key,
            CallActivity.EXTRA_MAX_RETRANSMITS_MS, R.string.pref_max_retransmit_time_ms_default);
    mMaxRetr =
            sharedPrefGetInteger(R.string.pref_max_retransmits_key, CallActivity.EXTRA_MAX_RETRANSMITS,
                    R.string.pref_max_retransmits_default);
    mId = sharedPrefGetInteger(R.string.pref_data_id_key, CallActivity.EXTRA_ID,
            R.string.pref_data_id_default);
    mProtocol = sharedPrefGetString(R.string.pref_data_protocol_key,
            CallActivity.EXTRA_PROTOCOL, R.string.pref_data_protocol_default);

  }

  @Override
  public void sendOfferSdp(SessionDescription localSdp, final String remoteId, String conferenceId, String token) {
    if (mService != null) {
      if (token != null && token.length() != 0) {
        mService.sendTokenOfferSdp(localSdp, token, "1", remoteId);
      }
      else if (conferenceId != null && conferenceId.length() != 0) {
        mService.sendConferenceOfferSdp(localSdp, remoteId, conferenceId);
      }
      else {
        mService.sendOfferSdp(localSdp, remoteId);
      }
    }

    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        callListFragment.updateUserState(User.CallState.CALLING, remoteId);
      }
    });
  }

  @Override
  public void sendAnswerSdp(SessionDescription localSdp, String remoteId) {
    if (mService != null) {
      mService.sendAnswerSdp(localSdp, remoteId);
    }
    callListFragment.updateUserState(User.CallState.CALLING, remoteId);
  }

  @Override
  public void sendLocalIceCandidate(SerializableIceCandidate candidate, String remoteId, String token, String id) {
    if (mService != null) {
      mService.sendLocalIceCandidate(candidate, token != null ? token : "", id != null ? id : "", remoteId);
    }
  }

  @Override
  public void onError(final String description, final String remoteId) {
    connectedUserIds.remove(remoteId);

    runOnUiThread(new Runnable() {

      @Override
      public void run() {
        callListFragment.updateUserState(User.CallState.NONE, remoteId);
        logAndToast(description);
        updateVideoView();
      }
    });
  }

  @Override
  public void onConnected(final String remoteId, final String token) {
    if (token == null || token.length() == 0) {
      connectedUserIds.add(remoteId);

      runOnUiThread(new Runnable() {
        @Override
        public void run() {
          callListFragment.updateUserState(User.CallState.CONNECTED, remoteId);
        }
      });

      if (mConferenceId != null) {
          // send the conference invites
          mService.sendConference(mConferenceId, connectedUserIds);
      }
    }
  }

  @Override
  public void onConnectionClosed(final String remoteId, final String token) {
    if (token == null || token.length() == 0) {
       // normal peer call
      connectedUserIds.remove(remoteId);
    }
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        if (token == null || token.length() == 0) {
          callListFragment.updateUserState(User.CallState.NONE, remoteId);
        }

        if (mTokenPeers.containsKey(remoteId)) {
          mTokenPeers.get(remoteId).close();
          mTokenPeers.remove(remoteId);
          screenshareRenderLayout.setVisibility(View.GONE);
          if (screenshareRender != null) {
            screenshareRender.setVisibility(View.GONE);
          }
          updateVideoView();
        }
      }
    });
    runOnUiThread(new Runnable() {

      @Override
      public void run() {
        updateVideoView();
      }
    });
  }

  @Override
  public void showHoldMessage(final boolean on, final String remoteId) {
    runOnUiThread(new Runnable() {
      @Override
      public void run() {
        for (RemoteConnectionViews remoteView: remoteViewsInUseList) {
          if (remoteView.getId().equals(remoteId)) {
            remoteView.setHoldStatus(on);
            break;
          }
        }
        if (on) {
          callListFragment.updateUserState(User.CallState.HOLD, remoteId);

        } else {
          callListFragment.updateUserState(User.CallState.CONNECTED, remoteId);
        }
      }
    });
  }

  public ArrayList<User> getUsers() {
    ArrayList<User> users = null;
    if (mService != null) {
      String roomName = mService.getCurrentRoomName();
      if (roomName != null) {
        users = mService.getUsersInRoom(roomName);
      }
    }

    return users;
  }

  public String getServerAddress() {
    return mServer;
  }

  public boolean callHasId(String id) {
    if (mPeerId.equals(id) || mAdditionalPeers.containsKey(id)) {
      return true;
    }

    return false;
  }

  private void putCallOnHold(boolean on) {
    if (peerConnectionClient != null) {
        try {
            JSONObject json = new JSONObject();
            JSONObject jsonHold = new JSONObject();
            json.put("state", on);

            jsonHold.put("Hold", json);
            jsonHold.put("Type", "Hold");

            peerConnectionClient.sendDataChannelMessage(jsonHold.toString());
        }
        catch (JSONException e) {
            e.printStackTrace();
        }
    }
  }
}
