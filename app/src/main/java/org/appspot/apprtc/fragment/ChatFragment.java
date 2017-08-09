package org.appspot.apprtc.fragment;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.example.sharedresourceslib.BroadcastTypes;

import org.appspot.apprtc.CallFragment;
import org.appspot.apprtc.ChatAdapter;
import org.appspot.apprtc.ChatItem;
import org.appspot.apprtc.ChatListAdapter;
import org.appspot.apprtc.FileInfo;
import org.appspot.apprtc.R;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.SerializableIceCandidate;
import org.appspot.apprtc.TokenPeerConnection;
import org.appspot.apprtc.User;
import org.appspot.apprtc.UsersAdapter;
import org.appspot.apprtc.entities.Presence;
import org.appspot.apprtc.receiver.CallReceiver;
import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.sound.SoundPlayer;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import static com.example.sharedresourceslib.BroadcastTypes.ACTION_PRESENCE_CHANGED;
import static org.appspot.apprtc.RoomActivity.EXTRA_TO;


public class ChatFragment extends Fragment implements ChatAdapter.OnChatAdapterEvents, ChatListAdapter.ChatAdapterEvents, TokenPeerConnection.TokenPeerConnectionEvents {

    private View controlView;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private HashMap<String, ArrayList<ChatItem>> chatList = new HashMap<String, ArrayList<ChatItem>>();
    private String mRoomName = "";
    private String mServerName = "";
    TextView mUserNameTextView;
    private Context mContext;
    private TextView emptyChat;
    private ImageButton sendButton;
    private EditText editChat;
    private OnChatEvents chatEvents;
    private User mUser;
    Handler uiUpdateHandler;
    private SoundPlayer mSoundPlayer;
    private String mCurrentId = "";
    private int mNextId = 0;
    private HashMap<String, User> userIdList = new HashMap<String, User>();

    private TextView recentButton;
    private RelativeLayout recentControlsLayout;

    private TextView roomChatButton;
    private String mAvatarUrl;
    private ChatFragment mInstance;
    private IntentFilter mIntentFilter;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            if (intent.getAction().equals(WebsocketService.ACTION_CHAT_MESSAGE)) {
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

                    ChatItem chatItem = new ChatItem(time, user.displayName, message, user.buddyPicture, user.Id, user.Id);
                    chatItem.setNotificationId(notificationId);
                    addMessage(chatItem, user, true);
                }
            } else if (intent.getAction().equals(WebsocketService.ACTION_FILE_MESSAGE)) {
                FileInfo fileinfo = (FileInfo)intent.getSerializableExtra(WebsocketService.EXTRA_FILEINFO);
                String time = intent.getStringExtra(WebsocketService.EXTRA_TIME);
                User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);

                if (user != null) {
                    addMessage(new ChatItem(time, user.displayName, fileinfo, user.buddyPicture, user.Id), user, true);
                }
            }
            else if (intent.getAction().equals(BroadcastTypes.ACTION_DOWNLOAD_PATH)) {
                int index = intent.getIntExtra(BroadcastTypes.EXTRA_INDEX, 0);
                String to = intent.getStringExtra(BroadcastTypes.EXTRA_TO);
                String path = intent.getStringExtra(BroadcastTypes.EXTRA_PATH);
                setDownloadPath(index, path, to);

            }
            else if (intent.getAction().equals(BroadcastTypes.ACTION_DOWNLOAD_ERROR)) {
                int index = intent.getIntExtra(BroadcastTypes.EXTRA_INDEX, 0);
                String to = intent.getStringExtra(BroadcastTypes.EXTRA_TO);
                String path = intent.getStringExtra(BroadcastTypes.EXTRA_DESCRIPTION);
                onDownloadError(path, index, to);
            }
            else if (intent.getAction().equals(BroadcastTypes.ACTION_DOWNLOAD_COMPLETE)) {

                int index = intent.getIntExtra(BroadcastTypes.EXTRA_INDEX, 0);
                String to = intent.getStringExtra(BroadcastTypes.EXTRA_TO);
                setDownloadComplete(index, to);
            }
            else if (intent.getAction().equals(BroadcastTypes.ACTION_DOWNLOADED_BYTES)) {

                long downloaded = intent.getLongExtra(BroadcastTypes.EXTRA_BYTES, 0);
                int index = intent.getIntExtra(BroadcastTypes.EXTRA_INDEX, 0);
                String to = intent.getStringExtra(BroadcastTypes.EXTRA_TO);
                setDownloadedBytes(index, downloaded, to);
            }
            }
    };

    private String mOwnId;
    private HashMap<String, TokenPeerConnection> mPeerConnections = new HashMap<String, TokenPeerConnection>();

    private String getNextId() {
        mNextId++;
        return String.valueOf(mNextId);
    }

    public void clearMessages() {
        chatList.clear();
    }

    @Override
    public void onMessageShown() {
        chatEvents.onMessageRead();
    }

    @Override
    public void onDownload(int position, String id, FileInfo fileinfo) {

        String remoteId = id;
        FileInfo downloadFile = fileinfo;
        String token = downloadFile.id;
        int downloadIndex = position;

        String connectionId = getNextId();
        String peerConnectionId = remoteId + token + connectionId;
        CallReceiver.removeConnection(peerConnectionId);

        TokenPeerConnection connection = new TokenPeerConnection(getActivity(), this, true, token, remoteId, connectionId, mService.getIceServers(), downloadIndex);
        connection.setFileInfo(downloadFile);
        CallReceiver.addConnection(peerConnectionId, connection);
    }

    @Override
    public void onViewChat(String key) {
        viewChat(key);
    }

    public void setAvatarUrl(String avatarUrl) {
        mAvatarUrl = avatarUrl;
    }

    @Override
    public void onDownloadedBytes(int index, long bytes, String to) {
        setDownloadedBytes(index, bytes, to);
    }

    @Override
    public void onDownloadComplete(int index, String to) {
        setDownloadComplete(index, to);
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
        setDownloadPath(index, path, to);
    }

    @Override
    public void onError(String description, int index, String to) {
        onDownloadError(description, index, to);
    }

    public enum ChatMode {
        TOPLEVEL,
        CONTENTS
    }

    private ChatMode mode = ChatMode.TOPLEVEL;

    private boolean mWebsocketServiceBound;

    private WebsocketService mService;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            // We've bound to LocalService, cast the IBinder and get LocalService instance
            WebsocketService.WebsocketBinder binder = (WebsocketService.WebsocketBinder) service;
            mService = binder.getService();
            mWebsocketServiceBound = true;

            if (mAvatarUrl == null) {
                mAvatarUrl = mService.getAvatarUrl();
            }

            mRoomName = mService.getCurrentRoomName();

            ArrayList<User> users = mService.getUsersInRoom(mRoomName.equals(getString(R.string.default_room)) ? "" : mRoomName);

            ArrayList<ChatItem> messages = mService.getMessages(mRoomName.equals(getString(R.string.default_room)) ? "" : mRoomName);

            if (messages != null && users != null) {
                HashMap<String, User> userMap = new HashMap<>();
                for (User user : users) {
                    userMap.put(user.Id, user);
                }

                clearMessages();
                for (ChatItem chatItem : messages) {
                    User user = userMap.get(chatItem.Id);
                    if (user != null) {
                        addMessage(chatItem, user, false);
                    }
                    else {
                        // self
                        user = userMap.get(chatItem.getRecipient());
                        addMessage(chatItem, user, false);
                    }

                }

                prepareChatDisplay();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mWebsocketServiceBound = false;
        }
    };

    public void viewChat(String key) {
        mode = ChatMode.CONTENTS;
        mCurrentId = key;

        if (chatList.get(key) == null) {
            chatList.put(key, new ArrayList<ChatItem>());
        }
        adapter = new ChatAdapter(chatList.get(mCurrentId), getContext(), mServerName, mAvatarUrl, this);

        if (recyclerView != null) {
            recyclerView.setAdapter(adapter);
        }

        User user = userIdList.get(key);

        if (mUserNameTextView != null && user != null) {
            mUserNameTextView.setText(user.displayName);
        }

        if (recentButton != null) {
            recentButton.setVisibility(View.VISIBLE);
        }

        if (editChat != null) {
            editChat.setVisibility(View.VISIBLE);
        }

        if (sendButton != null) {
            sendButton.setVisibility(View.VISIBLE);
        }
    }

    public void setUser(User user) {
        if (user != null) {
            if (!userIdList.containsKey(user.Id)) {
                userIdList.put(user.Id, user);
            }
        }

        if (chatList.get(user.Id) == null) {
            chatList.put(user.Id, new ArrayList<ChatItem>());
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }

        viewChat(user.Id);

        mUser = user;
        mCurrentId = user.Id;

        if (editChat != null) {
            editChat.requestFocus(); editChat.postDelayed(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager keyboard = (InputMethodManager)
                            getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    keyboard.showSoftInput(editChat, 0);
                }
            },200);
        }
    }

    public void setDownloadedBytes(final int index, final long downloaded, final String to) {
        uiUpdateHandler.post(new Runnable() {

            @Override
            public void run() {
                ChatItem item = chatList.get(to).get(index);
                long filesize = item.getFilesize();
                item.setPercentDownloaded((int) ((float)((float)downloaded / (float)filesize) * 100.0f));
                adapter.notifyDataSetChanged();
            }
        });
    }

    public void setDownloadPath(int index, String path, String to) {
        ChatItem item = chatList.get(to).get(index);
        item.setDownloadPath(path);
    }

    public void setDownloadComplete(final int index, final String to) {
        uiUpdateHandler.post(new Runnable() {

            @Override
            public void run() {
                ChatItem item = chatList.get(to).get(index);
                item.setDownloadComplete();
                adapter.notifyDataSetChanged();
            }
        });
    }

    public void onDownloadError(final String description, final int index, final String to) {
        uiUpdateHandler.post(new Runnable() {

            @Override
            public void run() {
                if (index < chatList.size()) {
                    ChatItem item = chatList.get(to).get(index);
                    item.setDownloadFailed(description);
                    adapter.notifyDataSetChanged();
                }
            }
        });
    }


    /**
     * Call control interface for container activity.
     */
    public interface OnChatEvents {
        void onSendChatMessage(String time, String displayName, String buddyPicture, String message, String to);
        void onMessageRead();
        void onSendFile(String time, String displayName, String buddyPicture, String message, long size, String name, String mime, String to);

        void onDownload(int position, String id, FileInfo fileinfo);
    }

    public ChatFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mInstance = this;
        if (savedInstanceState != null) {
            chatList = (HashMap<String, ArrayList<ChatItem>>)savedInstanceState.getSerializable("chatList");
            userIdList = (HashMap<String, User>)savedInstanceState.getSerializable("userList");
            mode = savedInstanceState.getInt("mode") == 0 ? ChatMode.TOPLEVEL : ChatMode.CONTENTS;
            mCurrentId = savedInstanceState.getString("current");
        }

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WebsocketService.ACTION_CHAT_MESSAGE);
        mIntentFilter.addAction(WebsocketService.ACTION_FILE_MESSAGE);
        mIntentFilter.addAction(BroadcastTypes.ACTION_DOWNLOAD_PATH);
        mIntentFilter.addAction(BroadcastTypes.ACTION_DOWNLOAD_ERROR);
        mIntentFilter.addAction(BroadcastTypes.ACTION_DOWNLOAD_COMPLETE);
        mIntentFilter.addAction(BroadcastTypes.ACTION_DOWNLOADED_BYTES);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putSerializable("chatList", chatList);
        savedInstanceState.putSerializable("userList", userIdList);
        savedInstanceState.putInt("mode", mode.ordinal());
        savedInstanceState.putString("current", mCurrentId);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        controlView = inflater.inflate(R.layout.fragment_chat, container, false);

        mUserNameTextView = (TextView) controlView.findViewById(R.id.userName);
        recentControlsLayout = (RelativeLayout) controlView.findViewById(R.id.recent_controls_layout);

        recentButton = (TextView) controlView.findViewById(R.id.recentButton);

        recyclerView= (RecyclerView) controlView.findViewById(R.id.recycler_view);
        emptyChat = (TextView) controlView.findViewById(R.id.emptyChat);
        sendButton = (ImageButton) controlView.findViewById(R.id.sendButton);
        editChat = (EditText) controlView.findViewById(R.id.chatEdit);
        roomChatButton = (TextView) controlView.findViewById(R.id.roomChatButton);

        roomChatButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                viewChat("");
            }
        });
        recentButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mode = ChatMode.TOPLEVEL;
                mCurrentId = "";
                adapter = new ChatListAdapter(chatList, userIdList, getContext(), mServerName, mRoomName, mInstance);
                recyclerView.setAdapter(adapter);
                mUserNameTextView.setText("");
                recentButton.setVisibility(View.GONE);
                editChat.setVisibility(View.GONE);
                sendButton.setVisibility(View.GONE);
            }
        });

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = editChat.getText().toString();
                editChat.setText("");
                String to = mCurrentId;

                SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                fmt.setTimeZone(TimeZone.getTimeZone("GMT"));
                String time = fmt.format(new Date());
                chatEvents.onSendChatMessage(time, mService.getAccountName(), "self", message, to);

                ChatItem item = new ChatItem(time, mService.getAccountName(), message, "self", "", to);
                item.setOutgoing();
                addOutgoingMessage(item);
            }
        });

        uiUpdateHandler = new Handler();

        return controlView;
    }

    @Override
    public void onResume() {
        super.onResume();
        chatEvents.onMessageRead();
        if (chatList.size() != 0) {
            emptyChat.setVisibility(View.GONE);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(RoomActivity.EXTRA_ROOM_NAME)) {
                mRoomName = args.getString(RoomActivity.EXTRA_ROOM_NAME);
                if (mRoomName.length() == 0) {
                    mRoomName = getString(R.string.default_room);
                }
                userIdList.put("", new User("", "", mRoomName, ""));
            }

            if (args.containsKey(RoomActivity.EXTRA_SERVER_NAME)) {
                mServerName = args.getString(RoomActivity.EXTRA_SERVER_NAME);
            }

            if (args.containsKey(WebsocketService.EXTRA_ADDRESS)) {
                mServerName = args.getString(WebsocketService.EXTRA_ADDRESS);
            }

            if (args.containsKey(RoomActivity.EXTRA_AVATAR_URL)) {
                mAvatarUrl = args.getString(RoomActivity.EXTRA_AVATAR_URL);
            }

            if (args.containsKey(WebsocketService.EXTRA_USER)) {
                User user = (User) args.getSerializable(WebsocketService.EXTRA_USER);
                setUser(user);
            }


            if (args.containsKey(WebsocketService.EXTRA_OWN_ID)) {
                mOwnId = args.getString(WebsocketService.EXTRA_OWN_ID);
            }
        }

        if (chatList.get("") == null) {
            chatList.put("", new ArrayList<ChatItem>());
        }

        layoutManager=new LinearLayoutManager(getContext());
        ((LinearLayoutManager)layoutManager).setStackFromEnd(true);
        recyclerView.setLayoutManager(layoutManager);

        prepareChatDisplay();

        getActivity().registerReceiver(mReceiver, mIntentFilter);

        // Bind to LocalService
        Intent serviceIntent = new Intent(getActivity(), WebsocketService.class);
        getActivity().startService(serviceIntent);
        getActivity().bindService(serviceIntent, mConnection, Context.BIND_AUTO_CREATE);

    }

    private void prepareChatDisplay() {

        if (mode == ChatMode.TOPLEVEL) {
            recentButton.setVisibility(View.GONE);
            adapter = new ChatListAdapter(chatList, userIdList, getContext(), mServerName, mRoomName, mInstance);
            mUserNameTextView.setText("");
            editChat.setVisibility(View.GONE);
            sendButton.setVisibility(View.GONE);
        }
        else {
            recentButton.setVisibility(View.VISIBLE);

            editChat.setVisibility(View.VISIBLE);
            sendButton.setVisibility(View.VISIBLE);


            if (chatList.get(mCurrentId) == null) {
                chatList.put(mCurrentId, new ArrayList<ChatItem>());
            }

            adapter = new ChatAdapter(chatList.get(mCurrentId), getContext(), mServerName, mAvatarUrl, this);

            if (userIdList.get(mCurrentId) != null) {
                mUserNameTextView.setText(userIdList.get(mCurrentId).displayName);
            }
        }
        recyclerView.setAdapter(adapter);

        recyclerView.scrollToPosition(adapter.getItemCount() - 1);

    }

    @Override
    public void onStop() {
        super.onStop();

        // Unbind from the service
        if (mWebsocketServiceBound) {
            getActivity().unbindService(mConnection);
            mWebsocketServiceBound = false;
        }
    }

    public void addOutgoingMessage(ChatItem item) {
        addMessage(item, mUser, false);
    }

    public void addMessage(ChatItem chatItem, User user, boolean playSound) {
        if (user != null) {
            if (!userIdList.containsKey(user.Id)) {
                userIdList.put(user.Id, user);
            }
        }

        if (chatList.get(chatItem.getRecipient()) == null) {
            chatList.put(chatItem.getRecipient(), new ArrayList<ChatItem>());
        }

        chatList.get(chatItem.getRecipient()).add(chatItem);

        if (adapter != null) {
            adapter.notifyDataSetChanged();

            if (emptyChat != null) {
                emptyChat.setVisibility(View.GONE);
            }

            if (playSound) {
                if (getContext() != null) {
                    mSoundPlayer = new SoundPlayer(getContext(), R.raw.message1);
                    mSoundPlayer.Play(false);
                }
            }

            if (recyclerView != null) {
                recyclerView.scrollToPosition(adapter.getItemCount() - 1);
            }
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        chatEvents = (OnChatEvents) activity;
    }
}
