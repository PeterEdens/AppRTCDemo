/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.graphics.drawable.RoundedBitmapDrawable;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.Editable;
import android.util.Base64;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.URLUtil;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;
import com.github.ybq.android.spinkit.SpinKitView;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;

import org.appspot.apprtc.entities.Presence;
import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.net.ssl.HttpsURLConnection;

import static org.appspot.apprtc.R.id.roomName;


/**
 * Handles the initial setup where the user selects which room to join.
 */
public class ConnectActivity extends DrawerActivity {
  private static final String TAG = "ConnectActivity";
  private static final int CONNECTION_REQUEST = 1;
  private static final int REMOVE_FAVORITE_INDEX = 0;
  private static final int STATUS_PIXEL_SIZE = 256;
  private static boolean commandLineRun = false;

  private static final int PERMISSIONS_REQUEST = 1;

  // List of mandatory application permissions.
  private static final String[] MANDATORY_PERMISSIONS = {"android.permission.MODIFY_AUDIO_SETTINGS",
          "android.permission.RECORD_AUDIO", "android.permission.INTERNET", "android.permission.CAMERA",
          "android.permission.WRITE_EXTERNAL_STORAGE"};

  WebsocketService mService;
  boolean mWebsocketServiceBound = false;
  private IntentFilter mIntentFilter;
  String mServerName;
  String mDisplayName;
  ConnectionState mConnectionState = ConnectionState.DISCONNECTED;
  Handler mReconnectHandler = new Handler() {
    @Override
    public void handleMessage(Message msg) {
      reconnect();
    }
  };
  RelativeLayout mRoomListLayout;

  FloatingActionButton mAddRoom;
  EditText mAddRoomEditText;
  TextView mConnectionTextView;
  SpinKitView mConnectingProgress;
  private ListView roomListView;
  private SharedPreferences sharedPref;
  private String keyprefRoom;
  private String keyprefRoomList;
  private ArrayList<String> roomList;
  private RoomAdapter adapter;

  private boolean mWaitingToEnterRoom;
  private boolean mStatusSent = false;
  private String mCurrentRoom = "";
  private Toolbar toolbar;
  private String mOwnId;
  private ImageView mConnectedImage;
  private boolean mConnectManual;
  private boolean mPromptDisplayed;
  private boolean mRoomLocked;

  private enum ConnectionState {
    DISCONNECTED,
    CONNECTED,
    LOGGED_IN
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

      Presence.Status presence = mService.getPresence();
      updateStatus(presence);


      if (mService.getIsConnected() && !mServerName.equals(mService.getServerAddress())) {
          mService.disconnectFromServer();
      }

      if (!mService.getIsConnected()) {
        if (!mConnectManual) {
          mService.connectToServer(mServerName);
        }
      }
      else {
        mCurrentRoom = mService.getCurrentRoomName();
        onConnected();
      }

    }

    @Override
    public void onServiceDisconnected(ComponentName arg0) {
      mWebsocketServiceBound = false;
    }
  };

  private void onConnected() {

    adapter.setCurrentRoom(mCurrentRoom);
    adapter.notifyDataSetChanged();
    mReconnectHandler.removeMessages(1);
    mConnectionTextView.setText(getString(R.string.connected));
    mConnectionState = ConnectionState.CONNECTED;
    mConnectingProgress.setVisibility(View.INVISIBLE);
    mRoomListLayout.setVisibility(View.VISIBLE);
    mConnectedImage.setVisibility(View.VISIBLE);

    if (!mStatusSent) {
      sendAccountBitmap();
      mStatusSent = true;
    }

    if (mWaitingToEnterRoom) {
      mService.connectToRoom(mCurrentRoom);
    }
  }

  void sendAccountBitmap() {
    Account account = getCurrentOwnCloudAccount(this);
    if (account != null) {
      AccountManager accountMgr = AccountManager.get(this);
      String serverUrl = accountMgr.getUserData(account, "oc_base_url");
      mDisplayName = accountMgr.getUserData(account, "oc_display_name");

      String name = account.name.substring(0, account.name.indexOf('@'));
      int size = STATUS_PIXEL_SIZE;
      String url = serverUrl + "/index.php/avatar/" + name + "/" + size;
      Bitmap avatar = ThumbnailsCacheManager.getBitmapFromDiskCache(url);


      String encodedImage = "";
      ThumbnailsCacheManager.LoadImage(url, new ThumbnailsCacheManager.LoadImageCallback() {

        @Override
        public void onImageLoaded(Bitmap bitmap) {
          String encodedImage = getEncodedImage(bitmap);
          mService.sendStatus(mDisplayName, encodedImage, getStatusText());
        }
      }, getResources(), mDisplayName, true, true);
    }
  }

  String getEncodedImage(Bitmap bitmap) {

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    bitmap.compress(Bitmap.CompressFormat.PNG, 100, baos); //bm is the bitmap object
    byte[] byteArrayImage = baos.toByteArray();

    String encodedImage = Base64.encodeToString(byteArrayImage, Base64.DEFAULT);

    return encodedImage;

  }

  private void reconnect() {

    // try to reconnect
    if (!mConnectManual && mConnectionState == ConnectionState.DISCONNECTED) {
      mService.connectToServer(mServerName);
    }

  }

  private BroadcastReceiver mReceiver = new BroadcastReceiver() {
    @Override
    public void onReceive(Context context, Intent intent) {

      if (intent.getAction().equals(WebsocketService.ACTION_CONNECTED)) {
        onConnected();
      } else if (intent.getAction().equals(WebsocketService.ACTION_DISCONNECTED)) {
        mStatusSent = false;
        mConnectionTextView.setText(getString(R.string.disconnected));
        mConnectionState = ConnectionState.DISCONNECTED;
        mConnectingProgress.setVisibility(View.VISIBLE);
        mConnectedImage.setVisibility(View.INVISIBLE);

        mReconnectHandler.sendEmptyMessageDelayed(1, 5000);
      } else if (intent.getAction().equals(WebsocketService.ACTION_CONNECTED_TO_ROOM)) {
        String roomName = intent.getStringExtra(WebsocketService.EXTRA_ROOM_NAME);

        mOwnId = mService.getId();
        if (!roomList.contains(roomName)) {
            adapter.add(roomName);
            adapter.notifyDataSetChanged();

        }

        if (mWaitingToEnterRoom) {
          mWaitingToEnterRoom = false;

          Intent roomIntent = new Intent(getApplicationContext(), RoomActivity.class);
          roomIntent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);
          roomIntent.putExtra(RoomActivity.EXTRA_ROOM_NAME, roomName);
          roomIntent.putExtra(RoomActivity.EXTRA_SERVER_NAME, mServerName);
          roomIntent.putExtra(RoomActivity.EXTRA_ROOM_LOCKED, mRoomLocked);
          Account account = getCurrentOwnCloudAccount(getApplicationContext());
          if (account != null) {
            AccountManager accountMgr = AccountManager.get(getApplicationContext());
            String serverUrl = accountMgr.getUserData(account, "oc_base_url");
            mDisplayName = accountMgr.getUserData(account, "oc_display_name");

            String name = account.name.substring(0, account.name.indexOf('@'));
            int size = getResources().getDimensionPixelSize(R.dimen.file_avatar_size);
            String url = serverUrl + "/index.php/avatar/" + name + "/" + size;
            roomIntent.putExtra(RoomActivity.EXTRA_AVATAR_URL, url);
          }
          startActivity(roomIntent);
        }
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_POST_RESPONSE)) {
        String response = intent.getStringExtra(WebsocketService.EXTRA_RESPONSE);
        if (response.length() != 0) {

        }
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_PATCH_RESPONSE)) {
        String response = intent.getStringExtra(WebsocketService.EXTRA_RESPONSE);
        if (response.length() != 0) {
          JSONObject json = null;
          try {
            json = new JSONObject(response);
            if (json.optBoolean("success")) {
              String userid = json.optString("userid");
              String nonce = json.optString("nonce");
              mService.sendAuthentication(userid, nonce);
            }
          } catch (JSONException e) {
            e.printStackTrace();
          }
        }
      } else if (intent.getAction().equals(WebsocketService.ACTION_CHAT_MESSAGE)) {
        String message = intent.getStringExtra(WebsocketService.EXTRA_MESSAGE);
        String time = intent.getStringExtra(WebsocketService.EXTRA_TIME);
        String status = intent.getStringExtra(WebsocketService.EXTRA_STATUS);
        User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);

        if (user != null && message.length() == 0) {
          // status message
          message = user.displayName + status;
        }
        else if (user != null) {
          String roomName = intent.getStringExtra(WebsocketService.EXTRA_ROOM_NAME);
          adapter.addNewMessage(roomName);
          adapter.notifyDataSetChanged();
        }
      } else if (intent.getAction().equals(WebsocketService.ACTION_FILE_MESSAGE)) {
        FileInfo fileinfo = (FileInfo)intent.getSerializableExtra(WebsocketService.EXTRA_FILEINFO);
        String time = intent.getStringExtra(WebsocketService.EXTRA_TIME);
        User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);

        if (user != null) {
          String roomName = intent.getStringExtra(WebsocketService.EXTRA_ROOM_NAME);
          adapter.addNewFileMessage(roomName);
          adapter.notifyDataSetChanged();
        }
      }
      else if (intent.getAction().equals(WebsocketService.ACTION_ERROR)) {
        String code = intent.getStringExtra(WebsocketService.EXTRA_CODE);
        String roomName = intent.getStringExtra(WebsocketService.EXTRA_ROOM_NAME);

        if (code.equals("authorization_required")) {
          PromptPin();
        }
        else if (code.equals("invalid_credentials")) {
          Snackbar snackbar = Snackbar
                  .make(roomListView, getString(R.string.invalid_credentials), Snackbar.LENGTH_INDEFINITE)
                  .setAction(getString(R.string.retry), new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                      PromptPin();
                    }
                  });

          snackbar.show();
        }
      }
    }
  };

  void PromptPin() {
    if (mPromptDisplayed) {
      return;
    }
    mPromptDisplayed = true;
    AlertDialog.Builder alert = new AlertDialog.Builder(this);
    final EditText edittext = new EditText(this);
    alert.setMessage(String.format(getString(R.string.enter_pin), mCurrentRoom));
    alert.setTitle(R.string.pin);

    alert.setView(edittext);

    alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int whichButton) {
        String pin = edittext.getText().toString();
        if (mService != null) {
          mService.connectToRoom(mCurrentRoom, pin);
          mRoomLocked = true;
          mPromptDisplayed = false;
        }
      }
    });

    alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
      public void onClick(DialogInterface dialog, int whichButton) {
        // what ever you want to do with No option.
        mPromptDisplayed = false;
      }
    });

    alert.show();
  }

  @Override
  protected void restart() {
    Intent connectActivity = new Intent(this, ConnectActivity.class);
    connectActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
    startActivity(connectActivity);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);


    // Get setting keys.
    PreferenceManager.setDefaultValues(this, R.xml.webrtc_preferences, false);
    sharedPref = PreferenceManager.getDefaultSharedPreferences(this);

    keyprefRoom = getString(R.string.pref_room_key);
    keyprefRoomList = getString(R.string.pref_room_list_key);

    setContentView(R.layout.activity_connect);
    toolbar = (Toolbar) findViewById(R.id.toolbar);
    setSupportActionBar(toolbar);

    if (getSupportActionBar() != null) {
      getSupportActionBar().setDisplayHomeAsUpEnabled(true);
      getSupportActionBar().setHomeButtonEnabled(true);
      setupDrawer();
      getSupportActionBar().setTitle(R.string.spreed_talk);
    }


    roomListView = (ListView) findViewById(R.id.room_listview);
    roomListView.setEmptyView(findViewById(android.R.id.empty));
    roomListView.setOnItemClickListener(roomListClickListener);
    registerForContextMenu(roomListView);
    mConnectionTextView = (TextView) findViewById(R.id.connected_state);
    mConnectingProgress = (SpinKitView) findViewById(R.id.connecting_progress);
    mConnectedImage = (ImageView) findViewById(R.id.connected_image);
    mAddRoom = (FloatingActionButton) findViewById(R.id.add_room_button);
    mAddRoom.setOnClickListener(addRoomListener);
    mAddRoomEditText = (EditText) findViewById(R.id.addroom_edittext);
    mRoomListLayout = (RelativeLayout) findViewById(R.id.room_list_layout);

    mConnectedImage.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View view) {
        mConnectManual = true;
        mReconnectHandler.removeMessages(1);

        if (mService.getIsConnected()) {
          mService.disconnectFromServer();
          mStatusSent = false;
          mConnectionTextView.setText(getString(R.string.disconnected));
          mConnectionState = ConnectionState.DISCONNECTED;
          mConnectedImage.setImageResource(R.drawable.ic_cancel_white_48dp);
        }
        else {
          mService.connectToServer(mServerName);
          mConnectedImage.setImageResource(R.drawable.ic_lock_white_48dp);
          mConnectedImage.setVisibility(View.INVISIBLE);
          mConnectingProgress.setVisibility(View.VISIBLE);
        }
      }
    });

    mIntentFilter = new IntentFilter();
    mIntentFilter.addAction(WebsocketService.ACTION_CONNECTED);
    mIntentFilter.addAction(WebsocketService.ACTION_DISCONNECTED);
    mIntentFilter.addAction(WebsocketService.ACTION_CONNECTED_TO_ROOM);
    mIntentFilter.addAction(WebsocketService.ACTION_PATCH_RESPONSE);
    mIntentFilter.addAction(WebsocketService.ACTION_POST_RESPONSE);
    mIntentFilter.addAction(WebsocketService.ACTION_CHAT_MESSAGE);
    mIntentFilter.addAction(WebsocketService.ACTION_FILE_MESSAGE);
    mIntentFilter.addAction(WebsocketService.ACTION_ERROR);

    // If an implicit VIEW intent is launching the app, go directly to that URL.
    final Intent intent = getIntent();

    Account account = getCurrentOwnCloudAccount(this);

    if (account != null) {
      AccountManager accountMgr = AccountManager.get(this);
      String serverUrl = accountMgr.getUserData(account, "oc_base_url");
      mDisplayName = accountMgr.getUserData(account, "oc_display_name");

      String name = account.name.substring(0, account.name.indexOf('@'));
      int size = getResources().getDimensionPixelSize(R.dimen.file_avatar_size);
      String url = serverUrl + "/index.php/avatar/" + name + "/" + size;
      Bitmap avatar = ThumbnailsCacheManager.getBitmapFromDiskCache(url);

      if (intent.hasExtra(RoomActivity.EXTRA_SERVER_NAME) && intent.hasExtra(RoomActivity.EXTRA_ROOM_NAME) &&
              savedInstanceState == null) {
        mServerName = intent.getStringExtra(RoomActivity.EXTRA_SERVER_NAME);
        mCurrentRoom = intent.getStringExtra(RoomActivity.EXTRA_ROOM_NAME);
        mRoomLocked = false;
        mWaitingToEnterRoom = true;
      }
      else {
        mServerName = serverUrl;

        if (mServerName.startsWith("https://")) {
          mServerName = mServerName.substring(8);
        }
      }
    }


    if ("android.intent.action.VIEW".equals(intent.getAction()) && !commandLineRun) {
      boolean loopback = intent.getBooleanExtra(CallActivity.EXTRA_LOOPBACK, false);
      int runTimeMs = intent.getIntExtra(CallActivity.EXTRA_RUNTIME, 0);
      boolean useValuesFromIntent =
          intent.getBooleanExtra(CallActivity.EXTRA_USE_VALUES_FROM_INTENT, false);
      String room = sharedPref.getString(keyprefRoom, "");

      connectToRoom(room, true, loopback, useValuesFromIntent, runTimeMs);
    }


    // Check for mandatory permissions.
    for (String permission : MANDATORY_PERMISSIONS) {
      if (checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
        requestPermission(permission);
      }
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    // Bind to LocalService
    Intent intent = new Intent(this, WebsocketService.class);
    startService(intent);
    bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

    registerReceiver(mReceiver, mIntentFilter);
    mStatusSent = false; // just in case we lost connection
  }

  @Override
  protected void onStop() {
    super.onStop();
    // Unbind from the service
    if (mWebsocketServiceBound) {
      unbindService(mConnection);
      mWebsocketServiceBound = false;
    }
    unregisterReceiver(mReceiver);
  }


  @Override
  public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
    if (v.getId() == R.id.room_listview) {
      AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
      if (roomList.get(info.position).length() != 0) { // cannot remove default room ""
        menu.setHeaderTitle(roomList.get(info.position));
        String[] menuItems = getResources().getStringArray(R.array.roomListContextMenu);
        for (int i = 0; i < menuItems.length; i++) {
          menu.add(Menu.NONE, i, i, menuItems[i]);
        }
      }
    } else {
      super.onCreateContextMenu(menu, v, menuInfo);
    }
  }

  @Override
  public boolean onContextItemSelected(MenuItem item) {
    if (item.getItemId() == REMOVE_FAVORITE_INDEX) {
      AdapterView.AdapterContextMenuInfo info =
          (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
      roomList.remove(info.position);
      adapter.notifyDataSetChanged();
      return true;
    }

    return super.onContextItemSelected(item);
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    if (item.getItemId() == android.R.id.home) {

      if (isDrawerOpen()) {
        closeDrawer();
      } else {
        openDrawer();
      }

      return true;
    }
    return super.onOptionsItemSelected(item);
  }


  @Override
  public void onPause() {
    super.onPause();

    String roomListJson = new JSONArray(roomList).toString();
    SharedPreferences.Editor editor = sharedPref.edit();
    editor.putString(keyprefRoomList, roomListJson);
    editor.commit();
  }

  @Override
  public void onResume() {
    super.onResume();

    roomList = new ArrayList<String>();
    String roomListJson = sharedPref.getString(keyprefRoomList, null);
    if (roomListJson != null) {
      try {
        JSONArray jsonArray = new JSONArray(roomListJson);
        for (int i = 0; i < jsonArray.length(); i++) {
          roomList.add(jsonArray.get(i).toString());
        }
      } catch (JSONException e) {
        Log.e(TAG, "Failed to load room list: " + e.toString());
      }
    }
    adapter = new RoomAdapter(this, R.id.text_id, R.layout.rooms_list, roomList);
    if (mConnectionState == ConnectionState.CONNECTED) {
      adapter.setCurrentRoom(mCurrentRoom);
    }
    roomListView.setAdapter(adapter);
    if (adapter.getCount() > 0) {
      roomListView.requestFocus();
      roomListView.setItemChecked(0, true);
    }

    if (mService != null) {
      sendAccountBitmap();
    }
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    if (requestCode == CONNECTION_REQUEST && commandLineRun) {
      Log.d(TAG, "Return: " + resultCode);
      setResult(resultCode);
      commandLineRun = false;
      finish();
    }
  }

  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  private String sharedPrefGetString(
      int attributeId, String intentName, int defaultId, boolean useFromIntent) {
    String defaultValue = getString(defaultId);
    if (useFromIntent) {
      String value = getIntent().getStringExtra(intentName);
      if (value != null) {
        return value;
      }
      return defaultValue;
    } else {
      String attributeName = getString(attributeId);
      return sharedPref.getString(attributeName, defaultValue);
    }
  }

  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  private boolean sharedPrefGetBoolean(
      int attributeId, String intentName, int defaultId, boolean useFromIntent) {
    boolean defaultValue = Boolean.valueOf(getString(defaultId));
    if (useFromIntent) {
      return getIntent().getBooleanExtra(intentName, defaultValue);
    } else {
      String attributeName = getString(attributeId);
      return sharedPref.getBoolean(attributeName, defaultValue);
    }
  }

  /**
   * Get a value from the shared preference or from the intent, if it does not
   * exist the default is used.
   */
  private int sharedPrefGetInteger(
      int attributeId, String intentName, int defaultId, boolean useFromIntent) {
    String defaultString = getString(defaultId);
    int defaultValue = Integer.parseInt(defaultString);
    if (useFromIntent) {
      return getIntent().getIntExtra(intentName, defaultValue);
    } else {
      String attributeName = getString(attributeId);
      String value = sharedPref.getString(attributeName, defaultString);
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException e) {
        Log.e(TAG, "Wrong setting for: " + attributeName + ":" + value);
        return defaultValue;
      }
    }
  }

  private void connectToRoom(String roomId, boolean commandLineRun, boolean loopback,
      boolean useValuesFromIntent, int runTimeMs) {
    if (mService != null) {
      mService.connectToRoom(roomId);
    }

    adapter.clearFlags(roomId);
    mWaitingToEnterRoom = true;

    /*this.commandLineRun = commandLineRun;

    // roomId is random for loopback.
    if (loopback) {
      roomId = Integer.toString((new Random()).nextInt(100000000));
    }

    String roomUrl = sharedPref.getString(
        keyprefRoomServerUrl, getString(R.string.pref_room_server_url_default));

    // Video call enabled flag.
    boolean videoCallEnabled = sharedPrefGetBoolean(R.string.pref_videocall_key,
        CallActivity.EXTRA_VIDEO_CALL, R.string.pref_videocall_default, useValuesFromIntent);

    // Use screencapture option.
    boolean useScreencapture = sharedPrefGetBoolean(R.string.pref_screencapture_key,
        CallActivity.EXTRA_SCREENCAPTURE, R.string.pref_screencapture_default, useValuesFromIntent);

    // Use Camera2 option.
    boolean useCamera2 = sharedPrefGetBoolean(R.string.pref_camera2_key, CallActivity.EXTRA_CAMERA2,
        R.string.pref_camera2_default, useValuesFromIntent);

    // Get default codecs.
    String videoCodec = sharedPrefGetString(R.string.pref_videocodec_key,
        CallActivity.EXTRA_VIDEOCODEC, R.string.pref_videocodec_default, useValuesFromIntent);
    String audioCodec = sharedPrefGetString(R.string.pref_audiocodec_key,
        CallActivity.EXTRA_AUDIOCODEC, R.string.pref_audiocodec_default, useValuesFromIntent);

    // Check HW codec flag.
    boolean hwCodec = sharedPrefGetBoolean(R.string.pref_hwcodec_key,
        CallActivity.EXTRA_HWCODEC_ENABLED, R.string.pref_hwcodec_default, useValuesFromIntent);

    // Check Capture to texture.
    boolean captureToTexture = sharedPrefGetBoolean(R.string.pref_capturetotexture_key,
        CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, R.string.pref_capturetotexture_default,
        useValuesFromIntent);

    // Check FlexFEC.
    boolean flexfecEnabled = sharedPrefGetBoolean(R.string.pref_flexfec_key,
        CallActivity.EXTRA_FLEXFEC_ENABLED, R.string.pref_flexfec_default, useValuesFromIntent);

    // Check Disable Audio Processing flag.
    boolean noAudioProcessing = sharedPrefGetBoolean(R.string.pref_noaudioprocessing_key,
        CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, R.string.pref_noaudioprocessing_default,
        useValuesFromIntent);

    // Check Disable Audio Processing flag.
    boolean aecDump = sharedPrefGetBoolean(R.string.pref_aecdump_key,
        CallActivity.EXTRA_AECDUMP_ENABLED, R.string.pref_aecdump_default, useValuesFromIntent);

    // Check OpenSL ES enabled flag.
    boolean useOpenSLES = sharedPrefGetBoolean(R.string.pref_opensles_key,
        CallActivity.EXTRA_OPENSLES_ENABLED, R.string.pref_opensles_default, useValuesFromIntent);

    // Check Disable built-in AEC flag.
    boolean disableBuiltInAEC = sharedPrefGetBoolean(R.string.pref_disable_built_in_aec_key,
        CallActivity.EXTRA_DISABLE_BUILT_IN_AEC, R.string.pref_disable_built_in_aec_default,
        useValuesFromIntent);

    // Check Disable built-in AGC flag.
    boolean disableBuiltInAGC = sharedPrefGetBoolean(R.string.pref_disable_built_in_agc_key,
        CallActivity.EXTRA_DISABLE_BUILT_IN_AGC, R.string.pref_disable_built_in_agc_default,
        useValuesFromIntent);

    // Check Disable built-in NS flag.
    boolean disableBuiltInNS = sharedPrefGetBoolean(R.string.pref_disable_built_in_ns_key,
        CallActivity.EXTRA_DISABLE_BUILT_IN_NS, R.string.pref_disable_built_in_ns_default,
        useValuesFromIntent);

    // Check Enable level control.
    boolean enableLevelControl = sharedPrefGetBoolean(R.string.pref_enable_level_control_key,
        CallActivity.EXTRA_ENABLE_LEVEL_CONTROL, R.string.pref_enable_level_control_key,
        useValuesFromIntent);

    // Get video resolution from settings.
    int videoWidth = 0;
    int videoHeight = 0;
    if (useValuesFromIntent) {
      videoWidth = getIntent().getIntExtra(CallActivity.EXTRA_VIDEO_WIDTH, 0);
      videoHeight = getIntent().getIntExtra(CallActivity.EXTRA_VIDEO_HEIGHT, 0);
    }
    if (videoWidth == 0 && videoHeight == 0) {
      String resolution =
          sharedPref.getString(keyprefResolution, getString(R.string.pref_resolution_default));
      String[] dimensions = resolution.split("[ x]+");
      if (dimensions.length == 2) {
        try {
          videoWidth = Integer.parseInt(dimensions[0]);
          videoHeight = Integer.parseInt(dimensions[1]);
        } catch (NumberFormatException e) {
          videoWidth = 0;
          videoHeight = 0;
          Log.e(TAG, "Wrong video resolution setting: " + resolution);
        }
      }
    }

    // Get camera fps from settings.
    int cameraFps = 0;
    if (useValuesFromIntent) {
      cameraFps = getIntent().getIntExtra(CallActivity.EXTRA_VIDEO_FPS, 0);
    }
    if (cameraFps == 0) {
      String fps = sharedPref.getString(keyprefFps, getString(R.string.pref_fps_default));
      String[] fpsValues = fps.split("[ x]+");
      if (fpsValues.length == 2) {
        try {
          cameraFps = Integer.parseInt(fpsValues[0]);
        } catch (NumberFormatException e) {
          cameraFps = 0;
          Log.e(TAG, "Wrong camera fps setting: " + fps);
        }
      }
    }

    // Check capture quality slider flag.
    boolean captureQualitySlider = sharedPrefGetBoolean(R.string.pref_capturequalityslider_key,
        CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED,
        R.string.pref_capturequalityslider_default, useValuesFromIntent);

    // Get video and audio start bitrate.
    int videoStartBitrate = 0;
    if (useValuesFromIntent) {
      videoStartBitrate = getIntent().getIntExtra(CallActivity.EXTRA_VIDEO_BITRATE, 0);
    }
    if (videoStartBitrate == 0) {
      String bitrateTypeDefault = getString(R.string.pref_maxvideobitrate_default);
      String bitrateType = sharedPref.getString(keyprefVideoBitrateType, bitrateTypeDefault);
      if (!bitrateType.equals(bitrateTypeDefault)) {
        String bitrateValue = sharedPref.getString(
            keyprefVideoBitrateValue, getString(R.string.pref_maxvideobitratevalue_default));
        videoStartBitrate = Integer.parseInt(bitrateValue);
      }
    }

    int audioStartBitrate = 0;
    if (useValuesFromIntent) {
      audioStartBitrate = getIntent().getIntExtra(CallActivity.EXTRA_AUDIO_BITRATE, 0);
    }
    if (audioStartBitrate == 0) {
      String bitrateTypeDefault = getString(R.string.pref_startaudiobitrate_default);
      String bitrateType = sharedPref.getString(keyprefAudioBitrateType, bitrateTypeDefault);
      if (!bitrateType.equals(bitrateTypeDefault)) {
        String bitrateValue = sharedPref.getString(
            keyprefAudioBitrateValue, getString(R.string.pref_startaudiobitratevalue_default));
        audioStartBitrate = Integer.parseInt(bitrateValue);
      }
    }

    // Check statistics display option.
    boolean displayHud = sharedPrefGetBoolean(R.string.pref_displayhud_key,
        CallActivity.EXTRA_DISPLAY_HUD, R.string.pref_displayhud_default, useValuesFromIntent);

    boolean tracing = sharedPrefGetBoolean(R.string.pref_tracing_key, CallActivity.EXTRA_TRACING,
        R.string.pref_tracing_default, useValuesFromIntent);

    // Get datachannel options
    boolean dataChannelEnabled = sharedPrefGetBoolean(R.string.pref_enable_datachannel_key,
        CallActivity.EXTRA_DATA_CHANNEL_ENABLED, R.string.pref_enable_datachannel_default,
        useValuesFromIntent);
    boolean ordered = sharedPrefGetBoolean(R.string.pref_ordered_key, CallActivity.EXTRA_ORDERED,
        R.string.pref_ordered_default, useValuesFromIntent);
    boolean negotiated = sharedPrefGetBoolean(R.string.pref_negotiated_key,
        CallActivity.EXTRA_NEGOTIATED, R.string.pref_negotiated_default, useValuesFromIntent);
    int maxRetrMs = sharedPrefGetInteger(R.string.pref_max_retransmit_time_ms_key,
        CallActivity.EXTRA_MAX_RETRANSMITS_MS, R.string.pref_max_retransmit_time_ms_default,
        useValuesFromIntent);
    int maxRetr =
        sharedPrefGetInteger(R.string.pref_max_retransmits_key, CallActivity.EXTRA_MAX_RETRANSMITS,
            R.string.pref_max_retransmits_default, useValuesFromIntent);
    int id = sharedPrefGetInteger(R.string.pref_data_id_key, CallActivity.EXTRA_ID,
        R.string.pref_data_id_default, useValuesFromIntent);
    String protocol = sharedPrefGetString(R.string.pref_data_protocol_key,
        CallActivity.EXTRA_PROTOCOL, R.string.pref_data_protocol_default, useValuesFromIntent);

    // Start AppRTCMobile activity.
    Log.d(TAG, "Connecting to room " + roomId + " at URL " + roomUrl);
    if (validateUrl(roomUrl)) {
      Uri uri = Uri.parse(roomUrl);
      Intent intent = new Intent(this, CallActivity.class);
      intent.setData(uri);
      intent.putExtra(CallActivity.EXTRA_ROOMID, roomId);
      intent.putExtra(CallActivity.EXTRA_LOOPBACK, loopback);
      intent.putExtra(CallActivity.EXTRA_VIDEO_CALL, videoCallEnabled);
      intent.putExtra(CallActivity.EXTRA_SCREENCAPTURE, useScreencapture);
      intent.putExtra(CallActivity.EXTRA_CAMERA2, useCamera2);
      intent.putExtra(CallActivity.EXTRA_VIDEO_WIDTH, videoWidth);
      intent.putExtra(CallActivity.EXTRA_VIDEO_HEIGHT, videoHeight);
      intent.putExtra(CallActivity.EXTRA_VIDEO_FPS, cameraFps);
      intent.putExtra(CallActivity.EXTRA_VIDEO_CAPTUREQUALITYSLIDER_ENABLED, captureQualitySlider);
      intent.putExtra(CallActivity.EXTRA_VIDEO_BITRATE, videoStartBitrate);
      intent.putExtra(CallActivity.EXTRA_VIDEOCODEC, videoCodec);
      intent.putExtra(CallActivity.EXTRA_HWCODEC_ENABLED, hwCodec);
      intent.putExtra(CallActivity.EXTRA_CAPTURETOTEXTURE_ENABLED, captureToTexture);
      intent.putExtra(CallActivity.EXTRA_FLEXFEC_ENABLED, flexfecEnabled);
      intent.putExtra(CallActivity.EXTRA_NOAUDIOPROCESSING_ENABLED, noAudioProcessing);
      intent.putExtra(CallActivity.EXTRA_AECDUMP_ENABLED, aecDump);
      intent.putExtra(CallActivity.EXTRA_OPENSLES_ENABLED, useOpenSLES);
      intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_AEC, disableBuiltInAEC);
      intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_AGC, disableBuiltInAGC);
      intent.putExtra(CallActivity.EXTRA_DISABLE_BUILT_IN_NS, disableBuiltInNS);
      intent.putExtra(CallActivity.EXTRA_ENABLE_LEVEL_CONTROL, enableLevelControl);
      intent.putExtra(CallActivity.EXTRA_AUDIO_BITRATE, audioStartBitrate);
      intent.putExtra(CallActivity.EXTRA_AUDIOCODEC, audioCodec);
      intent.putExtra(CallActivity.EXTRA_DISPLAY_HUD, displayHud);
      intent.putExtra(CallActivity.EXTRA_TRACING, tracing);
      intent.putExtra(CallActivity.EXTRA_CMDLINE, commandLineRun);
      intent.putExtra(CallActivity.EXTRA_RUNTIME, runTimeMs);

      intent.putExtra(CallActivity.EXTRA_DATA_CHANNEL_ENABLED, dataChannelEnabled);

      if (dataChannelEnabled) {
        intent.putExtra(CallActivity.EXTRA_ORDERED, ordered);
        intent.putExtra(CallActivity.EXTRA_MAX_RETRANSMITS_MS, maxRetrMs);
        intent.putExtra(CallActivity.EXTRA_MAX_RETRANSMITS, maxRetr);
        intent.putExtra(CallActivity.EXTRA_PROTOCOL, protocol);
        intent.putExtra(CallActivity.EXTRA_NEGOTIATED, negotiated);
        intent.putExtra(CallActivity.EXTRA_ID, id);
      }

      if (useValuesFromIntent) {
        if (getIntent().hasExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA)) {
          String videoFileAsCamera =
              getIntent().getStringExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA);
          intent.putExtra(CallActivity.EXTRA_VIDEO_FILE_AS_CAMERA, videoFileAsCamera);
        }

        if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE)) {
          String saveRemoteVideoToFile =
              getIntent().getStringExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE);
          intent.putExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE, saveRemoteVideoToFile);
        }

        if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH)) {
          int videoOutWidth =
              getIntent().getIntExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, 0);
          intent.putExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_WIDTH, videoOutWidth);
        }

        if (getIntent().hasExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT)) {
          int videoOutHeight =
              getIntent().getIntExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, 0);
          intent.putExtra(CallActivity.EXTRA_SAVE_REMOTE_VIDEO_TO_FILE_HEIGHT, videoOutHeight);
        }
      }

      startActivityForResult(intent, CONNECTION_REQUEST);
    }*/
  }

  private final AdapterView.OnItemClickListener roomListClickListener =
      new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
          String roomId = roomList.get(i);
          mCurrentRoom = roomId;
          mRoomLocked = false;
          adapter.setCurrentRoom(roomId);
          adapter.notifyDataSetChanged();
          connectToRoom(roomId, false, false, false, 0);
        }
      };


  private final OnClickListener connectListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      mConnectionTextView.setText(getString(R.string.connecting));
    }
  };

  private final OnClickListener addRoomListener = new OnClickListener() {
    @Override
    public void onClick(View view) {
      if (mAddRoomEditText.getVisibility() == View.GONE) {
        mAddRoomEditText.setVisibility(View.VISIBLE);
        mAddRoomEditText.requestFocus();
        mAddRoomEditText.postDelayed(new Runnable() {
          @Override
          public void run() {
            InputMethodManager keyboard = (InputMethodManager)
                    getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.showSoftInput(mAddRoomEditText, 0);
          }
        },200);
        mAddRoom.setImageResource(R.drawable.ic_input_white_24dp);
      }
      else {
        mCurrentRoom = mAddRoomEditText.getText().toString();
        mRoomLocked = false;
        adapter.setCurrentRoom(mCurrentRoom);
        adapter.notifyDataSetChanged();
        mService.connectToRoom(mAddRoomEditText.getText().toString());
        mWaitingToEnterRoom = true;
        mAddRoomEditText.setVisibility(View.GONE);
        mAddRoom.setImageResource(R.drawable.ic_add_white_24dp);
      }
    }
  };



  @Override
  protected void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    outState.putString("currentRoom", adapter.mCurrentRoom);
  }

  @Override
  public void onRestoreInstanceState(Bundle savedInstanceState) {
    super.onRestoreInstanceState(savedInstanceState);
    String currentRoom = savedInstanceState.getString("currentRoom");
    mCurrentRoom = currentRoom;
    mRoomLocked = false;
  }


  private void requestPermission(String permission) {

      // No explanation needed, we can request the permission.
      ActivityCompat.requestPermissions(this,
              MANDATORY_PERMISSIONS,
              PERMISSIONS_REQUEST);

  }

  @Override
  public void onRequestPermissionsResult(int requestCode,
                                         String permissions[], int[] grantResults) {
    switch (requestCode) {
      case PERMISSIONS_REQUEST: {
        // If request is cancelled, the result arrays are empty.
        if (grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

          // permission was granted, yay! Do the
          // contacts-related task you need to do.

        } else {

          // permission denied, boo! Disable the
          // functionality that depends on this permission.
        }
        return;
      }

      // other 'case' lines to check for other
      // permissions this app might request
    }
  }
}
