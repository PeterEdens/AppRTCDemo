package org.appspot.apprtc.fragment;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import org.appspot.apprtc.CallFragment;
import org.appspot.apprtc.ChatAdapter;
import org.appspot.apprtc.ChatItem;
import org.appspot.apprtc.R;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.User;
import org.appspot.apprtc.UsersAdapter;
import org.webrtc.RendererCommon;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;


public class ChatFragment extends Fragment {

    private View controlView;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<ChatItem> chatList = new ArrayList();
    private String mRoomName = "";
    private String mServerName = "";
    TextView mRoomNameTextView;
    private Context mContext;
    private TextView emptyChat;
    private ImageButton sendButton;
    private EditText editChat;
    private OnChatEvents chatEvents;
    private User mUser;

    public void setUser(User user) {
        mUser = user;
    }


    /**
     * Call control interface for container activity.
     */
    public interface OnChatEvents {
        void onSendChatMessage(String message, String to);
    }

    public ChatFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mContext = container.getContext();

        // Inflate the layout for this fragment
        controlView = inflater.inflate(R.layout.fragment_chat, container, false);

        mRoomNameTextView = (TextView) controlView.findViewById(R.id.roomName);
        recyclerView= (RecyclerView) controlView.findViewById(R.id.recycler_view);
        emptyChat = (TextView) controlView.findViewById(R.id.emptyChat);
        sendButton = (ImageButton) controlView.findViewById(R.id.sendButton);
        editChat = (EditText) controlView.findViewById(R.id.chatEdit);

        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String message = editChat.getText().toString();
                String to = "";
                if (mUser != null) {
                    to = mUser.Id;
                }
                chatEvents.onSendChatMessage(message, to);
                String time = DateFormat.getDateTimeInstance().format(new Date());
                ChatItem item = new ChatItem(time, "Me", message, "", "");
                item.setOutgoing();
                addMessage(item);
            }
        });
        return controlView;
    }

    @Override
    public void onStart() {
        super.onStart();

        Bundle args = getArguments();
        if (args != null) {
            if (args.containsKey(RoomActivity.EXTRA_ROOM_NAME)) {
                mRoomName = args.getString(RoomActivity.EXTRA_ROOM_NAME);
            }

            if (args.containsKey(RoomActivity.EXTRA_SERVER_NAME)) {
                mServerName = args.getString(RoomActivity.EXTRA_SERVER_NAME);
            }
        }
        mRoomNameTextView.setText(mRoomName);
        layoutManager=new LinearLayoutManager(mContext);
        recyclerView.setLayoutManager(layoutManager);

        adapter=new ChatAdapter(chatList,mContext, mServerName);
        recyclerView.setAdapter(adapter);

    }

    public void addMessage(ChatItem chatItem) {
        chatList.add(chatItem);
        adapter.notifyDataSetChanged();
        emptyChat.setVisibility(View.GONE);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        chatEvents = (OnChatEvents) activity;
    }
}
