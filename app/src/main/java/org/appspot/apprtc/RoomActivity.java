package org.appspot.apprtc;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.OpenableColumns;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.appspot.apprtc.entities.Presence;
import org.appspot.apprtc.fragment.ChatFragment;
import org.appspot.apprtc.fragment.FilesFragment;
import org.appspot.apprtc.fragment.RoomFragment;
import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import static android.R.id.message;
import static com.example.sharedresourceslib.BroadcastTypes.ACTION_PRESENCE_CHANGED;
import static org.appspot.apprtc.ConnectActivity.STATUS_PIXEL_SIZE;

public class RoomActivity extends DrawerActivity implements ChatFragment.OnChatEvents, PeerConnectionClient.PeerConnectionEvents, PeerConnectionClient.DataChannelCallback, TokenPeerConnection.TokenPeerConnectionEvents {
    public static final String EXTRA_ROOM_NAME = "org.appspot.apprtc.EXTRA_ROOM_NAME";
    public static final String EXTRA_SERVER_NAME = "org.appspot.apprtc.EXTRA_SERVER_NAME";
    public static final String ACTION_NEW_CHAT = "org.appspot.apprtc.ACTION_NEW_CHAT";
    public static final String ACTION_SHARE_FILE = "org.appspot.apprtc.ACTION_SHARE_FILE";
    public static final String ACTION_DOWNLOAD = "org.appspot.apprtc.ACTION_DOWNLOAD";
    public static final String ACTION_CANCEL_DOWNLOAD = "org.appspot.apprtc.ACTION_CANCEL_DOWNLOAD";
    public static final String EXTRA_TO = "org.appspot.apprtc.EXTRA_TO";
    public static final String EXTRA_INDEX = "org.appspot.apprtc.EXTRA_INDEX";
    public static final String ACTION_VIEW_CHAT = "org.appspot.apprtc.ACTION_VIEW_CHAT";
    public static final String EXTRA_CHAT_ID = "org.appspot.apprtc.EXTRA_CHAT_ID";
    public static final String EXTRA_AVATAR_URL = "org.appspot.apprtc.EXTRA_AVATAR_URL";
    public static final String EXTRA_ACTIVE_TAB = "org.appspot.apprtc.EXTRA_ACTIVE_TAB";
    public static final String EXTRA_ROOM_LOCKED = "org.appspot.apprtc.EXTRA_ROOM_LOCKED";
    private static final int FILE_CODE = 1;
    public static final int ROOM_INDEX = 0;
    public static final int CHAT_INDEX = 1;
    public static final int FILE_INDEX = 2;

    static final String BUDDY_IMG_PATH = "/webrtc/static/img/buddy/s46/";

    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<User> userList=new ArrayList();
    private String mRoomName = "";
   // private String mServerName = "";
    TextView mRoomNameTextView;

    WebsocketService mService;
    boolean mWebsocketServiceBound = false;
    private IntentFilter mIntentFilter;

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;
    private int mTokenId = 1;

    private HashMap<String, TokenPeerConnection> mPeerConnections = new HashMap<String, TokenPeerConnection>();
    //private PeerConnectionClient peerConnectionClient = null;

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

            ArrayList<User> users = mService.getUsersInRoom(mRoomName.equals(getString(R.string.default_room)) ? "" : mRoomName);


            ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
            RoomFragment roomFragment = (RoomFragment)adapter.getItem(ROOM_INDEX);
            roomFragment.addUsers(users);

            ArrayList<ChatItem> messages = mService.getMessages(mRoomName);

            if (messages != null && users != null) {
                HashMap<String, User> userMap = new HashMap<>();
                for (User user : users) {
                    userMap.put(user.Id, user);
                }

                ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
                chatFragment.clearMessages();
                for (ChatItem chatItem : messages) {
                    User user = userMap.get(chatItem.Id);
                    if (user != null) {
                        chatFragment.addMessage(chatItem, user, false);
                    }
                    else {
                        // self
                        user = userMap.get(chatItem.getRecipient());
                        chatFragment.addMessage(chatItem, user, false);
                    }

                }
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mWebsocketServiceBound = false;
        }
    };

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(WebsocketService.ACTION_CONNECTED)) {
                //mConnectionTextView.setText(getString(R.string.connected));
            } else if (intent.getAction().equals(WebsocketService.ACTION_DISCONNECTED)) {
                finish();
            } else if (intent.getAction().equals(WebsocketService.ACTION_USER_ENTERED)) {
                User userEntered = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
                AddUser(userEntered);
            } else if (intent.getAction().equals(WebsocketService.ACTION_USER_UPDATE)) {
                User userEntered = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
                UpdateUser(userEntered);
            } else if (intent.getAction().equals(WebsocketService.ACTION_USER_LEFT)) {
                User userLeft = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
                RemoveUser(userLeft);
            } else if (intent.getAction().equals(WebsocketService.ACTION_CHAT_MESSAGE)) {
               String message = intent.getStringExtra(WebsocketService.EXTRA_MESSAGE);
                String time = intent.getStringExtra(WebsocketService.EXTRA_TIME);
                String status = intent.getStringExtra(WebsocketService.EXTRA_STATUS);
                User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
                String to = intent.getStringExtra(EXTRA_TO);
                int notificationId = intent.getIntExtra(WebsocketService.EXTRA_NOTIFICATION_ID, 0);

                if (user != null && message.length() == 0) {
                    // status message
                    message = user.displayName + status;
                }
                else if (user != null) {
                    String toId = user.Id;
                    if (!to.equals(mOwnId)) {
                        toId = to;
                    }

                    ShowMessage(user.displayName, message, time, status, user.buddyPicture, toId, user, notificationId);
                }
            } else if (intent.getAction().equals(WebsocketService.ACTION_FILE_MESSAGE)) {
                FileInfo fileinfo = (FileInfo)intent.getSerializableExtra(WebsocketService.EXTRA_FILEINFO);
                String time = intent.getStringExtra(WebsocketService.EXTRA_TIME);
                User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);

                if (user != null) {
                    ShowMessage(user.displayName, time, fileinfo, user.buddyPicture, user.Id, user);
                }
            }
            else if (intent.getAction().equals(ACTION_PRESENCE_CHANGED)) {
                sendAccountBitmap();
            }
        }
    };

    private boolean mWaitingToEnterRoom;
    private String mFileRecipient;
    private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    private PeerConnectionClient.DataChannelParameters dataChannelParameters;
    private SessionDescription mLocalSdp;
    private AppRTCClient.SignalingParameters signalingParameters;
    private SerializableIceCandidate mIceCandidate;
    private String mPeerId;
    private String mSdpId = "";
    private boolean initiator;
    private String mRemoteId;
    private String mToken = "";
    private boolean mRemoteSdpSet;
    private FileInfo mDownloadFile;
    private FileOutputStream mDownloadStream;
    private int mChunkIndex = 0;
    private boolean mLocalSdpSent;
    private long mDownloadedBytes;
    private int mDownloadIndex;
    private InputStream inputStream;
    private long totalSize;
    private int mCurrentId = 0;
    private String mAvatar;
    private String mDisplayName;
    private String mServerName;
    private String mOwnId = "";
    private boolean mRoomLocked;

    private void ShowMessage(String displayName, String time, FileInfo fileinfo, String buddyPicture, String Id, User user) {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
        chatFragment.addMessage(new ChatItem(time, displayName, fileinfo, buddyPicture, Id), user, true);
        //tabLayout.getTabAt(1).setIcon(R.drawable.recent_chats_message);
    }

    private void ShowMessage(String displayName, String message, String time, String status, String buddyPicture, String Id, User user, int notificationId) {
        ChatItem chatItem = new ChatItem(time, displayName, message, buddyPicture, Id, Id);
        chatItem.setNotificationId(notificationId);
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
        chatFragment.addMessage(chatItem, user, true);
        //tabLayout.getTabAt(1).setIcon(R.drawable.recent_chats_message);
    }

    private void AddUser(User userEntered) {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        RoomFragment roomFragment = (RoomFragment)adapter.getItem(ROOM_INDEX);
        roomFragment.addUser(userEntered);
    }

    private void UpdateUser(User userEntered) {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        RoomFragment roomFragment = (RoomFragment)adapter.getItem(ROOM_INDEX);
        roomFragment.updateUser(userEntered);
    }

    private void RemoveUser(User userLeft) {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        RoomFragment roomFragment = (RoomFragment)adapter.getItem(ROOM_INDEX);
        roomFragment.removeUser(userLeft);
    }

    @Override
    protected void restart() {
        Intent connectActivity = new Intent(this, ConnectActivity.class);
        connectActivity.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(connectActivity);
    }

    void PromptPin(final MenuItem item) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        final EditText edittext = new EditText(this);
        alert.setMessage(String.format(getString(R.string.enter_pin), mRoomName));
        alert.setTitle(R.string.pin);

        alert.setView(edittext);

        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                String pin = edittext.getText().toString();
                if (mService != null) {
                    mService.lockRoom(mRoomName, pin);
                    mRoomLocked = true;
                    item.setIcon(R.drawable.ic_lock_outline_white_24dp);
                }
            }
        });

        alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // what ever you want to do with No option.
            }
        });

        alert.show();
    }

    void PromptUnlock(final MenuItem item) {
        AlertDialog.Builder alert = new AlertDialog.Builder(this);
        alert.setMessage(String.format(getString(R.string.unlock_room), mRoomName));
        alert.setTitle(R.string.unlock);

        alert.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                if (mService != null) {
                    mService.unlockRoom(mRoomName);
                    mRoomLocked = false;
                    item.setIcon(R.drawable.ic_lock_open_white_24dp);
                }
            }
        });

        alert.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // what ever you want to do with No option.
            }
        });

        alert.show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.room_menu, menu);
        if (mRoomLocked) {
            menu.findItem(R.id.action_lock_room).setIcon(R.drawable.ic_lock_outline_white_24dp);
        }
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WebsocketService.ACTION_CONNECTED);
        mIntentFilter.addAction(WebsocketService.ACTION_DISCONNECTED);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_ENTERED);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_UPDATE);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_LEFT);
        mIntentFilter.addAction(WebsocketService.ACTION_CHAT_MESSAGE);
        mIntentFilter.addAction(WebsocketService.ACTION_FILE_MESSAGE);
        mIntentFilter.addAction(ACTION_PRESENCE_CHANGED);

        Intent intent = getIntent();

        // Bind to LocalService
        Intent serviceIntent = new Intent(this, WebsocketService.class);
        startService(serviceIntent);
        bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);

        registerReceiver(mReceiver, mIntentFilter);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            setupDrawer();
            getSupportActionBar().setTitle(R.string.spreed_talk);
        }

        tabLayout = (TabLayout) findViewById(R.id.tabs);

        viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);
        //setupTabIcons();

        Account account = getCurrentOwnCloudAccount(this);
        if (account != null) {
            AccountManager accountMgr = AccountManager.get(this);
            String serverUrl = accountMgr.getUserData(account, "oc_base_url");
            mDisplayName = accountMgr.getUserData(account, "oc_display_name");

            String name = account.name.substring(0, account.name.indexOf('@'));
            int size = getResources().getDimensionPixelSize(R.dimen.avatar_size_small);
            String url = serverUrl + "/index.php/avatar/" + name + "/" + size;
            Bitmap avatar = ThumbnailsCacheManager.getBitmapFromDiskCache(url);

        }
        
        handleIntent(intent);

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
                    String presence = mService.getPresenceString();

                    mService.sendStatus(mDisplayName, encodedImage, presence + " " + getStatusText());
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

    @Override
    public void onResume() {
        super.onResume();

    }
  
    /*private void setupPeerConnection() {
        // is there a connection open?
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
        mRemoteSdpSet = false;
        mLocalSdpSent = false;
        peerConnectionClient = PeerConnectionClient.getInstance();
        peerConnectionClient.createPeerConnectionFactory(
                this, peerConnectionParameters, this);
        peerConnectionClient.setDataChannelCallback(this);

    }*/

    private void handleIntent(Intent intent) {

        String action = intent.getAction();

        if (action != null && action.equals(ACTION_CANCEL_DOWNLOAD)) {

            String remoteId = intent.getStringExtra(EXTRA_TO);
            FileInfo downloadFile = (FileInfo) intent.getSerializableExtra(WebsocketService.EXTRA_FILEINFO);
            String token = downloadFile.id;

            String peerConnectionId = remoteId + token;
            for (HashMap.Entry<String, TokenPeerConnection> entry: mPeerConnections.entrySet()) {
                String key = entry.getKey();
                if (key.startsWith(peerConnectionId)) {
                    entry.getValue().close();
                    mPeerConnections.remove(key);
                    break;
                }
            }
        }
        else if (action != null && action.equals(ACTION_DOWNLOAD)) {
            initiator = true;

            String remoteId = intent.getStringExtra(EXTRA_TO);
            FileInfo downloadFile = (FileInfo) intent.getSerializableExtra(WebsocketService.EXTRA_FILEINFO);
            String token = downloadFile.id;
            int downloadIndex = intent.getIntExtra(EXTRA_INDEX, -1);

            String connectionId = getNextId();
            String peerConnectionId = remoteId + token + connectionId;
            if (mPeerConnections.containsKey(peerConnectionId)) {
                mPeerConnections.get(peerConnectionId).close();
                mPeerConnections.remove(peerConnectionId);
            }

            TokenPeerConnection connection = new TokenPeerConnection(this, this, true, token, remoteId, connectionId, mService.getIceServers(), downloadIndex);
            connection.setFileInfo(downloadFile);
            mPeerConnections.put(peerConnectionId, connection);
        }
        else if (action != null && action.equals(ACTION_NEW_CHAT)) {
            viewPager.setCurrentItem(CHAT_INDEX);
            User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);

            ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
            ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
            chatFragment.setUser(user);
        }
        else if (action != null && action.equals(ACTION_VIEW_CHAT)) {
            String key = intent.getStringExtra(EXTRA_CHAT_ID);
            ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
            ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
            chatFragment.viewChat(key);
        }
        else if (action != null && action.equals(ACTION_SHARE_FILE)) {
            User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
            mFileRecipient = user.Id;
            ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
            ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
            chatFragment.setUser(user);

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
        else if (action != null && action.equals(WebsocketService.ACTION_REMOTE_ICE_CANDIDATE)) {
            SerializableIceCandidate candidate = (SerializableIceCandidate)intent.getParcelableExtra(WebsocketService.EXTRA_CANDIDATE);
            String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
            String remoteId = candidate.from;
            String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);

            String peerConnectionId = remoteId + token + id;
            if (!mPeerConnections.containsKey(peerConnectionId)) {
                Log.e("RoomActivity", "Received remote ice candidate for non-initilized peer connection.");
                return;
            }

            IceCandidate ic = new IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
            mPeerConnections.get(peerConnectionId).addRemoteIceCandidate(ic);

        }
        else if (action != null && action.equals(WebsocketService.ACTION_REMOTE_DESCRIPTION)) {
            SerializableSessionDescription sdp = (SerializableSessionDescription) intent.getSerializableExtra(WebsocketService.EXTRA_REMOTE_DESCRIPTION);
            String remoteId = sdp.from;
            String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);
            String id = intent.getStringExtra(WebsocketService.EXTRA_ID);

            if (!token.equals(mToken)) {
                mRemoteSdpSet = false;
            }

            mToken = token;

            String peerConnectionId = remoteId + token + id;
            if (mPeerConnections.containsKey(peerConnectionId)) {
                // an existing connection
                SessionDescription sd = new SessionDescription(sdp.type, sdp.description);
                mPeerConnections.get(peerConnectionId).setRemoteDescription(sd);
            } else {
                // does not exist, create the new connection
                TokenPeerConnection connection = new TokenPeerConnection(this, this, false, token, remoteId, id, mService.getIceServers(), -1);
                SessionDescription sd = new SessionDescription(sdp.type, sdp.description);
                connection.setRemoteDescription(sd);
                mPeerConnections.put(peerConnectionId, connection);

            }
        }

        if (intent.hasExtra(EXTRA_ACTIVE_TAB)) {
            viewPager.setCurrentItem(intent.getIntExtra(EXTRA_ACTIVE_TAB, 0));
        }

        if (intent.hasExtra(EXTRA_SERVER_NAME)) {
            mServerName = intent.getStringExtra(EXTRA_SERVER_NAME);
        }
        if (intent.hasExtra(EXTRA_ROOM_NAME)) {
            mRoomName = intent.getStringExtra(EXTRA_ROOM_NAME);
        }
        if (intent.hasExtra(WebsocketService.EXTRA_OWN_ID)) {
            mOwnId = intent.getStringExtra(WebsocketService.EXTRA_OWN_ID);
        }
        if (intent.hasExtra(EXTRA_ROOM_LOCKED)) {
            mRoomLocked = intent.getBooleanExtra(EXTRA_ROOM_LOCKED, false);
        }
    }



    private String getNextId() {
        mCurrentId++;
        return String.valueOf(mCurrentId);
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
                    mService.sendFileMessage(time, "Me", "self", fileInfo, path.toString(), size, name, mime, mFileRecipient, mRoomName);

                    ChatItem item = new ChatItem(time, "Me", fileInfo, "self", mFileRecipient);
                    item.setOutgoing();
                    ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
                    ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
                    chatFragment.addOutgoingMessage(item);
                }

        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Unbind from the service
        if (mWebsocketServiceBound) {
            unbindService(mConnection);
            mWebsocketServiceBound = false;
        }
        unregisterReceiver(mReceiver);

        viewPager = null;
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
        } else if (item.getItemId() == R.id.action_lock_room) {
            if (!mRoomLocked) {
                PromptPin(item);
            } else {
                PromptUnlock(item);
            }
        }
        else if (item.getItemId() == R.id.action_invite_user) {
            Class<?> c = null;
            try {
                c = Class.forName(getString(R.string.share_with_activity) );
            } catch (ClassNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            Intent intent = new Intent(this, c);
            intent.setAction(Intent.ACTION_SEND);
            String link = "https://" + mServerName + "/apps/spreedme";
            if (mRoomName.length() != 0 && !mRoomName.equals(getString(R.string.default_room))) {
                link += "#" + mRoomName;
            }
            intent.putExtra(Intent.EXTRA_TEXT, link);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private void setupTabIcons() {
        int[] tabIcons = {
                R.drawable.rooms,
                R.drawable.recent_chats,
                R.drawable.files
        };

        tabLayout.getTabAt(0).setIcon(tabIcons[0]);
        tabLayout.getTabAt(1).setIcon(tabIcons[1]);
        tabLayout.getTabAt(2).setIcon(tabIcons[2]);
    }

    private void setupViewPager(ViewPager viewPager) {
        ViewPagerAdapter adapter = new ViewPagerAdapter(getSupportFragmentManager());

        RoomFragment roomFragment = (RoomFragment)getSupportFragmentManager().findFragmentByTag(makeFragmentName(viewPager.getId(), ROOM_INDEX));
        if (roomFragment == null) {
            roomFragment = new RoomFragment();
            roomFragment.setArguments(getIntent().getExtras());
        }
        adapter.addFrag(roomFragment, getString(R.string.rooms));

        ChatFragment chatFragment = (ChatFragment)getSupportFragmentManager().findFragmentByTag(makeFragmentName(viewPager.getId(), CHAT_INDEX));
        if (chatFragment == null) {
            chatFragment = new ChatFragment();
            chatFragment.setArguments(getIntent().getExtras());
        }
        adapter.addFrag(chatFragment, getString(R.string.recent));

        FilesFragment fileFragment = (FilesFragment)getSupportFragmentManager().findFragmentByTag(makeFragmentName(viewPager.getId(), FILE_INDEX));
        if (fileFragment == null) {
            fileFragment = new FilesFragment();
        }
        adapter.addFrag(fileFragment, getString(R.string.files));


        viewPager.setAdapter(adapter);
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public void onSendChatMessage(String time, String displayName, String buddyPicture, String message, String to) {
        if (mService != null) {
            mService.sendChatMessage(time, displayName, buddyPicture, message, to, mRoomName);
        }
    }

    @Override
    public void onMessageRead() {
        //tabLayout.getTabAt(1).setIcon(R.drawable.recent_chats);
    }

    @Override
    public void onSendFile(String time, String displayName, String buddyPicture, String message, long size, String name, String mime, String to) {
        if (mService != null) {
           // mService.sendFileMessage(time, displayName, buddyPicture, fileInfo, message, size, name, mime, to, mRoomName);
        }
    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        mLocalSdp = sdp;
    }

    @Override
    public void onIceCandidate(SerializableIceCandidate candidate) {
        mIceCandidate = candidate;

        if (!mLocalSdpSent) {
            mLocalSdpSent = true;
            if (initiator) {
                mSdpId = String.valueOf(mTokenId++);

                mService.sendTokenOfferSdp(mLocalSdp, mToken, mSdpId, mRemoteId);
            }
            else {
                mService.sendTokenAnswerSdp(mLocalSdp, mToken, mSdpId, mPeerId);
            }
        }

        mService.sendLocalIceCandidate(mIceCandidate, mToken, mSdpId, mPeerId);

    }

    @Override
    public void onIceCandidatesRemoved(SerializableIceCandidate[] candidates) {

    }

    @Override
    public void onIceConnected() {
        /*Log.d("RoomActivity", "IceConnected");
        if (initiator) {
            mChunkIndex = 0;
            JSONObject json = new JSONObject();
            try {
                json.put("m", "r");
                json.put("i", mChunkIndex++);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            File sdCard = Environment.getExternalStorageDirectory();
            File dir = new File(sdCard.getAbsolutePath() + "/Downloads");
            dir.mkdirs();
            File file = new File(dir, mDownloadFile.name);
            mChatFragment.setDownloadPath(mDownloadIndex, file.getAbsolutePath());

            try {
                mDownloadStream = new FileOutputStream(file);
                mDownloadedBytes = 0;
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            peerConnectionClient.sendDataChannelMessage(json.toString());
        }*/
    }

    @Override
    public void onIceDisconnected() {
        Log.d("RoomActivity", "IceDisconnected");
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.d("RoomActivity", "onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {

    }

    @Override
    public void onPeerConnectionError(String description) {
        Log.d("RoomActivity", "onPeerConnectionError: " + description);
    }

    @Override
    public void onPeerConnectionFactoryCreated() {


       /* signalingParameters = new AppRTCClient.SignalingParameters(mService.getIceServers(), false, "", "", "", null, null);
        signalingParameters.dataonly = true;
        if (peerConnectionClient != null) {
            peerConnectionClient.createPeerConnection(signalingParameters);

        }*/
    }

    @Override
    public void onPeerConnectionCreated() {

       /* if (initiator) {
            peerConnectionClient.createOffer();
        }*/
    }

    @Override
    public void onRemoteSdpSet() {
        /*if (!initiator) {
            // Create answer. Answer SDP will be sent to offering client in
            // PeerConnectionEvents.onLocalDescription event.
            peerConnectionClient.createAnswer();
        }*/
    }

    @Override
    public void onVideoEnabled() {

    }

    @Override
    public void onVideoDisabled() {

    }

    @Override
    public void onBinaryMessage(DataChannel.Buffer buffer) {
        // write the stream to file
        /*ByteBuffer data = buffer.data;
        int length = data.capacity() - BINARY_HEADER_SIZE;
        final byte[] bytes = new byte[length];
        byte[] header = new byte[BINARY_HEADER_SIZE];
        data.get(header, 0, BINARY_HEADER_SIZE);
        data.get(bytes, 0, length);

        try {
            mDownloadStream.write(bytes);
            mDownloadedBytes += length;
            mChatFragment.setDownloadedBytes(mDownloadIndex, mDownloadedBytes);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mChunkIndex < Integer.valueOf(mDownloadFile.chunks)) {
            JSONObject json = new JSONObject();
            try {
                json.put("m", "r");
                json.put("i", mChunkIndex++);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            peerConnectionClient.sendDataChannelMessage(json.toString());
        }
        else {
            try {
                mDownloadStream.close();

                mChatFragment.setDownloadComplete(mDownloadIndex);

                JSONObject json = new JSONObject();
                try {
                    json.put("m", "bye");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                peerConnectionClient.sendDataChannelMessage(json.toString());
                peerConnectionClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }*/
    }

    @Override
    public void onTextMessage(String text) {
        // parse json
       /* try {
            JSONObject json = new JSONObject(text);
            String m = json.optString("m");
            if (m.equals("r")) {
                String chunk = json.optString("i");
                // read the chunk from the file and send it
                String path = new String(Base64.decode(mToken.substring(5), Base64.DEFAULT));

                long index = Long.valueOf(chunk);
                long start = index * 60000;
                inputStream.skip(start);
                long readSize = 60000;
                if (start + readSize > totalSize) {
                    readSize = totalSize - start;
                }
                byte[] data = new byte[(int)readSize];
                inputStream.read(data, (int)start, (int)readSize);
                inputStream.close();
                peerConnectionClient.sendDataChannelBinary(data);
            }
            else if (m.equals("bye")) {
                inputStream.close();
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }*/
    }

    @Override
    public void onStateChange(DataChannel.State state) {

    }

    @Override
    public void onDownloadedBytes(int index, long bytes, String to) {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
        chatFragment.setDownloadedBytes(index, bytes, to);
    }

    @Override
    public void onDownloadComplete(int index, String to) {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
        chatFragment.setDownloadComplete(index, to);
    }

    @Override
    public void TokenOfferSdp(SessionDescription localSdp, String token, String connectionId, String remoteId) {
        mService.sendTokenOfferSdp(localSdp, token, connectionId, remoteId);
    }

    @Override
    public void sendTokenAnswerSdp(SessionDescription localSdp, String token, String connectionId, String remoteId) {
        mService.sendTokenAnswerSdp(localSdp, token, connectionId, remoteId);
    }

    @Override
    public void sendLocalIceCandidate(SerializableIceCandidate candidate, String token, String connectionId, String remoteId) {
        mService.sendLocalIceCandidate(candidate, token, connectionId, remoteId);
    }

    @Override
    public void onDownloadPath(int index, String path, String to) {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
        chatFragment.setDownloadPath(index, path, to);
    }

    @Override
    public void onError(String description, int index, String to) {
        ViewPagerAdapter adapter = (ViewPagerAdapter) viewPager.getAdapter();
        ChatFragment chatFragment = (ChatFragment)adapter.getItem(CHAT_INDEX);
        chatFragment.onDownloadError(description, index, to);
    }

    private static String makeFragmentName(int viewId, long id) {
        return "android:switcher:" + viewId + ":" + id;
    }

    class ViewPagerAdapter extends FragmentPagerAdapter {
        private final List<Fragment> mFragmentList = new ArrayList<>();
        private final List<String> mFragmentTitleList = new ArrayList<>();

        public ViewPagerAdapter(FragmentManager manager) {
            super(manager);
        }

        @Override
        public Fragment getItem(int position) {
            return mFragmentList.get(position);
        }

        @Override
        public int getCount() {
            return mFragmentList.size();
        }

        public void addFrag(Fragment fragment, String title) {
            mFragmentList.add(fragment);
            mFragmentTitleList.add(title);
        }

        @Override
        public CharSequence getPageTitle(int position) {

            return mFragmentTitleList.get(position);
        }
    }


    private void connectToRoom(String roomId, boolean commandLineRun, boolean loopback,
                               boolean useValuesFromIntent, int runTimeMs) {
        if (mService != null) {
            mService.connectToRoom(roomId);
        }

        mWaitingToEnterRoom = true;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public long getContentSize(Uri uri) {
        long ret = 0;
        // The query, since it only applies to a single document, will only return
        // one row. There's no need to filter, sort, or select fields, since we want
        // all fields for one document.
        Cursor cursor = getContentResolver()
                .query(uri, null, null, null, null, null);

        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {

                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                String displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                // If the size is unknown, the value stored is null.  But since an
                // int can't be null in Java, the behavior is implementation-specific,
                // which is just a fancy term for "unpredictable".  So as
                // a rule, check if it's null before assigning to an int.  This will
                // happen often:  The storage API allows for remote files, whose
                // size might not be locally known.
                String size = null;
                if (!cursor.isNull(sizeIndex)) {
                    // Technically the column stores an int, but cursor.getString()
                    // will do the conversion automatically.
                    size = cursor.getString(sizeIndex);
                } else {
                    size = "0";
                }
                ret = Long.valueOf(size);
            }
        } finally {
            cursor.close();
        }

        return ret;
    }

}
