package org.appspot.apprtc.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
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

import org.appspot.apprtc.CallFragment;
import org.appspot.apprtc.ChatAdapter;
import org.appspot.apprtc.ChatItem;
import org.appspot.apprtc.ChatListAdapter;
import org.appspot.apprtc.FileInfo;
import org.appspot.apprtc.R;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.User;
import org.appspot.apprtc.UsersAdapter;
import org.appspot.apprtc.sound.SoundPlayer;
import org.webrtc.RendererCommon;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.TimeZone;

import static org.appspot.apprtc.ConnectActivity.EXTRA_AVATAR;


public class ChatFragment extends Fragment {

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
    private String mCurrentId;
    private HashMap<String, User> userIdList = new HashMap<String, User>();

    private RelativeLayout recentButton;
    private RelativeLayout recentControlsLayout;

    private TextView roomChatButton;
    private String mAvatarUrl;

    public void clearMessages() {
        chatList.clear();
    }

    public void clearMessages() {
        chatList.clear();
    }


    public enum ChatMode {
        TOPLEVEL,
        CONTENTS
    }

    private ChatMode mode = ChatMode.TOPLEVEL;

    public void viewChat(String key) {
        mode = ChatMode.CONTENTS;
        mCurrentId = key;

        if (chatList.get(key) == null) {
            chatList.put(key, new ArrayList<ChatItem>());
        }

        adapter = new ChatAdapter(chatList.get(mCurrentId), mContext, mServerName, mAvatarUrl);

        recyclerView.setAdapter(adapter);
        User user = userIdList.get(key);
        mUserNameTextView.setText(user.displayName);
        recentControlsLayout.setVisibility(View.VISIBLE);
    }

    public void setUser(User user) {
        if (user != null) {
            if (!userIdList.containsKey(user.Id)) {
                userIdList.put(user.Id, user);
            }
        }

        if (chatList.get(user.Id) == null) {
            chatList.put(user.Id, new ArrayList<ChatItem>());
        }

        viewChat(user.Id);

        mUser = user;
        mCurrentId = user.Id;

        if (editChat != null) {
            editChat.requestFocus(); editChat.postDelayed(new Runnable() {
                @Override
                public void run() {
                    InputMethodManager keyboard = (InputMethodManager)
                            mContext.getSystemService(Context.INPUT_METHOD_SERVICE);
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
    }

    public ChatFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            chatList = (HashMap<String, ArrayList<ChatItem>>)savedInstanceState.getSerializable("chatList");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        savedInstanceState.putSerializable("chatList", chatList);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mContext = container.getContext();

        // Inflate the layout for this fragment
        controlView = inflater.inflate(R.layout.fragment_chat, container, false);

        mUserNameTextView = (TextView) controlView.findViewById(R.id.userName);
        recentControlsLayout = (RelativeLayout) controlView.findViewById(R.id.recent_controls_layout);

        recentButton = (RelativeLayout) controlView.findViewById(R.id.recent_back_layout);

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
                adapter = new ChatListAdapter(chatList, userIdList, mContext, mServerName, mRoomName);
                recyclerView.setAdapter(adapter);
                mUserNameTextView.setText(getString(R.string.recent));
                recentControlsLayout.setVisibility(View.INVISIBLE);
            }
        });

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
                adapter = new ChatListAdapter(chatList, userIdList, mContext, mServerName, mRoomName);
                recyclerView.setAdapter(adapter);
                mUserNameTextView.setText(getString(R.string.recent));
                recentControlsLayout.setVisibility(View.INVISIBLE);
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
                chatEvents.onSendChatMessage(time, "Me", "self", message, to);

                ChatItem item = new ChatItem(time, "Me", message, "self", "", to);
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

            if (args.containsKey(EXTRA_AVATAR)) {
                mAvatar = args.getString(EXTRA_AVATAR);
            }
            if (args.containsKey(RoomActivity.EXTRA_SERVER_NAME)) {
                mServerName = args.getString(RoomActivity.EXTRA_SERVER_NAME);
            }

            if (args.containsKey(RoomActivity.EXTRA_AVATAR_URL)) {
                mAvatarUrl = args.getString(RoomActivity.EXTRA_AVATAR_URL);
            }
        }


        mCurrentId = "";
        if (chatList.get("") == null) {
            chatList.put("", new ArrayList<ChatItem>());
        }

        layoutManager=new LinearLayoutManager(mContext);
        recyclerView.setLayoutManager(layoutManager);

        if (mode == ChatMode.TOPLEVEL) {
            recentControlsLayout.setVisibility(View.INVISIBLE);
            adapter = new ChatListAdapter(chatList, userIdList, mContext, mServerName, mRoomName);
            mUserNameTextView.setText(getString(R.string.recent));
        }
        else {
            recentControlsLayout.setVisibility(View.VISIBLE);

            adapter = new ChatAdapter(chatList.get(mCurrentId), mContext, mServerName, mAvatarUrl);

            mUserNameTextView.setText(userIdList.get(mCurrentId).displayName);
        }
        recyclerView.setAdapter(adapter);

    }

    public void addOutgoingMessage(ChatItem item) {
        addMessage(item, mUser);
    }

    public void addMessage(ChatItem chatItem, User user) {
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
            emptyChat.setVisibility(View.GONE);
            mSoundPlayer = new SoundPlayer(mContext, R.raw.message1);
            mSoundPlayer.Play(false);
            recyclerView.scrollToPosition(chatList.size() - 1);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        chatEvents = (OnChatEvents) activity;
    }
}
