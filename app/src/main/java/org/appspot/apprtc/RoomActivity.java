package org.appspot.apprtc;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.database.Cursor;
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
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

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
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static android.R.id.message;

public class RoomActivity extends DrawerActivity implements ChatFragment.OnChatEvents, PeerConnectionClient.PeerConnectionEvents, PeerConnectionClient.DataChannelCallback, TokenPeerConnection.TokenPeerConnectionEvents {
    public static final String EXTRA_ROOM_NAME = "org.appspot.apprtc.EXTRA_ROOM_NAME";
    public static final String EXTRA_SERVER_NAME = "org.appspot.apprtc.EXTRA_SERVER_NAME";
    public static final String ACTION_NEW_CHAT = "org.appspot.apprtc.ACTION_NEW_CHAT";
    public static final String EXTRA_USER = "org.appspot.apprtc.EXTRA_USER";
    public static final String ACTION_SHARE_FILE = "org.appspot.apprtc.ACTION_SHARE_FILE";
    public static final String ACTION_DOWNLOAD = "org.appspot.apprtc.ACTION_DOWNLOAD";
    public static final String ACTION_CANCEL_DOWNLOAD = "org.appspot.apprtc.ACTION_CANCEL_DOWNLOAD";
    public static final String EXTRA_TO = "org.appspot.apprtc.EXTRA_TO";
    public static final String EXTRA_INDEX = "org.appspot.apprtc.EXTRA_INDEX";

    static final int CHAT_INDEX = 1;

    static final String BUDDY_IMG_PATH = "/webrtc/static/img/buddy/s46/";
    private static final int FILE_CODE = 1;
    private static final int BINARY_HEADER_SIZE = 12;
    String mRoomName = "";
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

            ArrayList<User> users = mService.getUsersInRoom(mRoomName.equals(getString(R.string.default_room)) ? "" : mRoomName);

            mRoomFragment.addUsers(users);

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
            } else if (intent.getAction().equals(WebsocketService.ACTION_USER_LEFT)) {
                User userLeft = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
                RemoveUser(userLeft);
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
                    ShowMessage(user.displayName, message, time, status, user.buddyPicture, user.Id);
                }
            } else if (intent.getAction().equals(WebsocketService.ACTION_FILE_MESSAGE)) {
                FileInfo fileinfo = (FileInfo)intent.getSerializableExtra(WebsocketService.EXTRA_FILEINFO);
                String time = intent.getStringExtra(WebsocketService.EXTRA_TIME);
                User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);

                if (user != null) {
                    ShowMessage(user.displayName, time, fileinfo, user.buddyPicture, user.Id);
                }
            }
        }
    };
    private RoomFragment mRoomFragment;
    private boolean mWaitingToEnterRoom;
    private ChatFragment mChatFragment;
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

    private void ShowMessage(String displayName, String time, FileInfo fileinfo, String buddyPicture, String Id) {
        mChatFragment.addMessage(new ChatItem(time, displayName, fileinfo, buddyPicture, Id));
        tabLayout.getTabAt(1).setIcon(R.drawable.recent_chats_message);
    }

    private void ShowMessage(String displayName, String message, String time, String status, String buddyPicture, String Id) {
        mChatFragment.addMessage(new ChatItem(time, displayName, message, buddyPicture, Id));
        tabLayout.getTabAt(1).setIcon(R.drawable.recent_chats_message);
    }

    private void AddUser(User userEntered) {

        mRoomFragment.addUser(userEntered);
    }

    private void RemoveUser(User userLeft) {

        mRoomFragment.removeUser(userLeft);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        ThumbnailsCacheManager.ThumbnailsCacheManagerInit(getApplicationContext());


        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WebsocketService.ACTION_CONNECTED);
        mIntentFilter.addAction(WebsocketService.ACTION_DISCONNECTED);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_ENTERED);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_LEFT);
        mIntentFilter.addAction(WebsocketService.ACTION_CHAT_MESSAGE);
        mIntentFilter.addAction(WebsocketService.ACTION_FILE_MESSAGE);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            setupDrawer();
        }


        viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
        setupTabIcons();

        dataChannelParameters = new PeerConnectionClient.DataChannelParameters(true,
                -1,
                -1, "",
                false, -1);
        peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(false, false, false, 0, 0, 0, 0, "", false, false, 0, "", true, false, false, true, true, true, false, dataChannelParameters);

        Intent intent = getIntent();
        handleIntent(intent);

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

            String peerConnectionId = remoteId + token + "1";
            if (mPeerConnections.containsKey(peerConnectionId)) {
                mPeerConnections.get(peerConnectionId).close();
                mPeerConnections.remove(peerConnectionId);
            }
        }
        else if (action != null && action.equals(ACTION_DOWNLOAD)) {
            initiator = true;

            String remoteId = intent.getStringExtra(EXTRA_TO);
            FileInfo downloadFile = (FileInfo) intent.getSerializableExtra(WebsocketService.EXTRA_FILEINFO);
            String token = downloadFile.id;
            int downloadIndex = intent.getIntExtra(EXTRA_INDEX, -1);

            String connectionId = "1";
            String peerConnectionId = remoteId + token + connectionId;
            if (mPeerConnections.containsKey(peerConnectionId)) {
                mPeerConnections.get(peerConnectionId).close();
                mPeerConnections.remove(peerConnectionId);
            }

            TokenPeerConnection connection = new TokenPeerConnection(this, this, true, token, remoteId, connectionId, mService.getIceServers());
            connection.setFileInfo(downloadFile);
            mPeerConnections.put(peerConnectionId, connection);
        }
        else if (action != null && action.equals(ACTION_NEW_CHAT)) {
            viewPager.setCurrentItem(CHAT_INDEX);
            User user = (User) intent.getSerializableExtra(EXTRA_USER);
            mChatFragment.setUser(user);
        }
        else if (action != null && action.equals(ACTION_SHARE_FILE)) {
            User user = (User) intent.getSerializableExtra(EXTRA_USER);
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
            }
            else {
                // does not exist, create the new connection
                TokenPeerConnection connection = new TokenPeerConnection(this, this, false, token, remoteId, id, mService.getIceServers());
                SessionDescription sd = new SessionDescription(sdp.type, sdp.description);
                connection.setRemoteDescription(sd);
                mPeerConnections.put(peerConnectionId, connection);

            }
        }

        if (intent.hasExtra(EXTRA_ROOM_NAME)) {
            mRoomName = intent.getStringExtra(EXTRA_ROOM_NAME);
        }

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
                    mService.sendFileMessage(path.toString(), size, name, mime, mFileRecipient);
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
        // Bind to LocalService
        Intent intent = new Intent(this, WebsocketService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);

        registerReceiver(mReceiver, mIntentFilter);
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
        mRoomFragment = new RoomFragment();
        mRoomFragment.setArguments(getIntent().getExtras());
        adapter.addFrag(mRoomFragment, getString(R.string.rooms));
        mChatFragment = new ChatFragment();
        mChatFragment.setArguments(getIntent().getExtras());
        adapter.addFrag(mChatFragment, getString(R.string.recent));
        adapter.addFrag(new FilesFragment(), getString(R.string.files));
        viewPager.setAdapter(adapter);
    }

    @Override
    public void onSendChatMessage(String message, String to) {
        if (mService != null) {
            mService.sendChatMessage(message, to);
        }
    }

    @Override
    public void onMessageRead() {
        tabLayout.getTabAt(1).setIcon(R.drawable.recent_chats);
    }

    @Override
    public void onSendFile(String message, long size, String name, String mime, String to) {
        if (mService != null) {
            mService.sendFileMessage(message, size, name, mime, to);
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
    public void onDownloadedBytes(int index, long bytes) {
        mChatFragment.setDownloadedBytes(index, bytes);
    }

    @Override
    public void onDownloadComplete(int index) {
        mChatFragment.setDownloadComplete(index);
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
    public void onDownloadPath(int index, String path) {
        mChatFragment.setDownloadPath(index, path);
    }

    @Override
    public void onError(String description, int index) {
        mChatFragment.onDownloadError(description, index);
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
