package org.appspot.apprtc.fragment;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.appspot.apprtc.R;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.User;
import org.appspot.apprtc.UsersAdapter;
import org.appspot.apprtc.service.WebsocketService;

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
    private TextView emptyRoom;
    private TextView roomsButton;
    private Activity mParentActivity;
    private String mOwnId;
    private ImageView shareRoom;

    public RoomFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            userList = (ArrayList<User>) savedInstanceState.getSerializable("userList");
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedState) {
        savedState.putSerializable("userList", userList);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mContext = container.getContext();

        // Inflate the layout for this fragment
        controlView = inflater.inflate(R.layout.fragment_roomlist, container, false);

        mRoomNameTextView = (TextView) controlView.findViewById(R.id.roomName);
        recyclerView= (RecyclerView) controlView.findViewById(R.id.recycler_view);
        emptyRoom = (TextView) controlView.findViewById(R.id.emptyRoom);
        shareRoom = (ImageView) controlView.findViewById(R.id.share_room);
        roomsButton = (TextView) controlView.findViewById(R.id.roomsButton);

        roomsButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mParentActivity.finish();
            }
        });

        shareRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Class<?> c = null;
                try {
                    c = Class.forName(getString(R.string.share_with_activity) );
                } catch (ClassNotFoundException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                Intent intent = new Intent(mContext, c);
                intent.setAction(Intent.ACTION_SEND);
                String link = "https://" + mServerName + "/apps/spreedme";
                if (mRoomName.length() != 0 && !mRoomName.equals(getString(R.string.default_room))) {
                    link += "#" + mRoomName;
                }
                intent.putExtra(Intent.EXTRA_TEXT, link);
                mParentActivity.startActivity(intent);
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
                if (mRoomName.equals("")) {
                    mRoomName = getString(R.string.default_room);
                }
            }

            if (args.containsKey(WebsocketService.EXTRA_OWN_ID)) {
                mOwnId = args.getString(WebsocketService.EXTRA_OWN_ID);
            }
            
            if (args.containsKey(RoomActivity.EXTRA_SERVER_NAME)) {
                mServerName = args.getString(RoomActivity.EXTRA_SERVER_NAME);
            }
        }
        mRoomNameTextView.setText(mRoomName);
        layoutManager=new LinearLayoutManager(mContext);
        recyclerView.setLayoutManager(layoutManager);

        adapter=new UsersAdapter(userList,mContext, mServerName, mOwnId);
        recyclerView.setAdapter(adapter);


        if (emptyRoom != null) {
            if (userList.size() == 0) {
                emptyRoom.setVisibility(View.VISIBLE);
                shareRoom.setVisibility(View.VISIBLE);
            } else if (userList.size() != 0 && emptyRoom.getVisibility() == View.VISIBLE) {
                emptyRoom.setVisibility(View.GONE);
                shareRoom.setVisibility(View.GONE);
            }
        }
    }

    public void addUsers(ArrayList<User> users) {
        if (users != null) {
            userList.clear();
            userList.addAll(users);
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (userList.size() != 0 && emptyRoom != null && emptyRoom.getVisibility() == View.VISIBLE) {
            emptyRoom.setVisibility(View.GONE);
            shareRoom.setVisibility(View.GONE);
        }
    }

    public void addUser(User userEntered) {
        if (userEntered != null) {
            boolean found = false;
            for (User u : userList) {
                if (u.Id.equals(userEntered.Id)) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                userList.add(userEntered);
                if (adapter != null) {
                    adapter.notifyDataSetChanged();
                }

                if (userList.size() != 0 && emptyRoom != null && emptyRoom.getVisibility() == View.VISIBLE) {
                    emptyRoom.setVisibility(View.GONE);
                    shareRoom.setVisibility(View.GONE);
                }
            }
        }

    }

    public void removeUser(User userLeft) {
        if (userLeft != null) {
            for (User u : userList) {
                if (u.Id.equals(userLeft.Id)) {
                    userList.remove(u);
                    break;
                }
            }
        }

        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }

        if (userList.size() == 0 && emptyRoom != null && emptyRoom.getVisibility() != View.VISIBLE) {
            emptyRoom.setVisibility(View.VISIBLE);
            shareRoom.setVisibility(View.VISIBLE);
        }
    }


    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mParentActivity = activity;
    }
}
