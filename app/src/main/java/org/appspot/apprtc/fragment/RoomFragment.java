package org.appspot.apprtc.fragment;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.appspot.apprtc.R;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.User;
import org.appspot.apprtc.UsersAdapter;

import java.util.ArrayList;


public class RoomFragment extends Fragment {

    private View controlView;
    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<User> userList=new ArrayList();
    private String mRoomName = "";
    private String mServerName = "";
    TextView mRoomNameTextView;
    private Context mContext;

    public RoomFragment() {
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
        controlView = inflater.inflate(R.layout.fragment_roomlist, container, false);

        mRoomNameTextView = (TextView) controlView.findViewById(R.id.roomName);
        recyclerView= (RecyclerView) controlView.findViewById(R.id.recycler_view);

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

        adapter=new UsersAdapter(userList,mContext, mServerName);
        recyclerView.setAdapter(adapter);
    }

    public void addUsers(ArrayList<User> users) {
        if (users != null) {
            userList.clear();
            userList.addAll(users);
        }
        adapter.notifyDataSetChanged();
    }

    public void addUser(User userEntered) {
        if (userEntered != null && !userList.contains(userEntered)) {
            userList.add(userEntered);
        }
        adapter.notifyDataSetChanged();
    }

    public void removeUser(User userLeft) {
        if (userLeft != null && userList.contains(userLeft)) {
            userList.remove(userLeft);
        }
        adapter.notifyDataSetChanged();
    }
}
