/*
 *  Copyright 2015 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc.fragment;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import com.github.clans.fab.FloatingActionButton;
import com.github.clans.fab.FloatingActionMenu;

import org.appspot.apprtc.CallActivity;
import org.appspot.apprtc.InCallUsersAdapter;
import org.appspot.apprtc.R;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.User;
import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.sound.SoundPlayer;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.webrtc.RendererCommon.ScalingType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import static org.appspot.apprtc.receiver.CustomPhoneStateListener.ACTION_HOLD_OFF;

/**
 * Fragment for call control.
 */
public class CallListFragment extends Fragment implements InCallUsersAdapter.InCallUsersAdapterEvents {
    private View controlView;
    private TextView contactView;

    private OnUserEvents callEvents;
    private Context mContext;
    private WeakReference<CallActivity> parentActivity;
    private String mOwnId;
    private HashMap userMap = new HashMap<String, User>();
    private ArrayList<User> userList = new ArrayList<User>();
    private ArrayList<User> activeUserList = new ArrayList<User>();
    private RecyclerView recyclerView;
    private String mServerName;
    private LinearLayoutManager layoutManager;
    private InCallUsersAdapter adapter;
    private boolean videoCallEnabled;
    FloatingActionButton addUsersButton;
    private boolean filter = true;
    private LinearLayout roomEmptyLayout;
    private String mRoomName;
    private InCallUsersAdapter.InCallUsersAdapterEvents mInstance;

    public void init(ArrayList<User> users) {
        if (users != null) {
            for (User user : users) {
                onUserEntered(user);
            }
        }
    }

    @Override
    public void onSendMessage(User user) {
        parentActivity.get().showChatMessages(user);
    }

    public class UserComparator implements Comparator<User> {
        public int compare(User left, User right) {
            return right.getCallState().ordinal() - left.getCallState().ordinal();
        }
    }

    UserComparator userComparator = new UserComparator();

    /**
     * Call control interface for container activity.
     */
    public interface OnUserEvents {

    }

    public void onUserEntered(User user) {
        if (!userMap.containsKey(user.Id)) {
            userMap.put(user.Id, user);
            userList.add(user);
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    public void onUserLeft(User user) {
        if (userMap.containsKey(user.Id)) {
            userMap.remove(user.Id);

            for (User listUser: userList) {
                if (listUser.Id.equals(user.Id)) {
                    userList.remove(listUser);
                    break;
                }
            }

            if (adapter != null) {
                if (adapter.getItemCount() == 0) {
                    roomEmptyLayout.setVisibility(View.VISIBLE);
                }
                adapter.notifyDataSetChanged();
            }
        }
    }

    public void updateUserState(User.CallState state, String userId) {
        if (userMap.containsKey(userId)) {
            User thisuser = ((User)userMap.get(userId));
            thisuser.setCallState(state);
            boolean found = userList.contains(thisuser);

            if (!found && state == User.CallState.NONE) {
                userList.add(thisuser);
            }
            else if (found && state == User.CallState.CONNECTED) {
                userList.remove(thisuser);
                if (userList.size() == 0 && roomEmptyLayout != null) {
                    roomEmptyLayout.setVisibility(View.INVISIBLE);
                    if (!filter) {
                        filter = !filter;
                        addUsersButton.setImageResource(!filter ? R.drawable.ic_close_white_24dp : R.drawable.ic_person_add_white_36dp);
                        showActiveUsers();
                    }
                }
            }

            boolean activeFound = activeUserList.contains(thisuser);
            if (!activeFound && thisuser.getCallState() != User.CallState.NONE) {
                activeUserList.add(thisuser);
            }
            else if (activeFound && thisuser.getCallState() == User.CallState.NONE) {
                activeUserList.remove(thisuser);
            }


            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
        }
    }

    void addUsers() {
        if (parentActivity.get() != null) {
            ArrayList<User> users = parentActivity.get().getUsers();
            if (users != null) {
                for (User user : users) {
                    onUserEntered(user);
                }
            }
        }
    }

    private void addUserToCall(User user) {
        Intent intent = new Intent(mContext, CallActivity.class);
        intent.setAction(CallActivity.ACTION_NEW_CALL);
        intent.putExtra(WebsocketService.EXTRA_USER, user);
        intent.putExtra(WebsocketService.EXTRA_OWN_ID, mOwnId);
        intent.putExtra(WebsocketService.EXTRA_ID, user.Id);
        intent.putExtra(WebsocketService.EXTRA_USERACTION, true);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        mContext.startActivity(intent);
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        controlView = inflater.inflate(R.layout.fragment_calllist, container, false);
        mContext = container != null ? container.getContext() : getActivity();
        mInstance = this;

        // Create UI controls.
        contactView = (TextView) controlView.findViewById(R.id.contact_name_call);

        addUsersButton = (FloatingActionButton) controlView.findViewById(R.id.button_add_users);
        recyclerView= (RecyclerView) controlView.findViewById(R.id.recycler_view);
        roomEmptyLayout = (LinearLayout) controlView.findViewById(R.id.room_empty_layout);
        roomEmptyLayout.setVisibility(View.INVISIBLE);
        addUsers();

        addUsersButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                filter = !filter;
                addUsersButton.setImageResource(!filter ? R.drawable.ic_close_white_24dp : R.drawable.ic_person_add_white_36dp);
                if (filter) {
                    //hide users not in call
                    showActiveUsers();
                }
                else {
                    // show all users

                    adapter=new InCallUsersAdapter(userList,mContext, mServerName, mOwnId, mInstance);
                    recyclerView.swapAdapter(adapter, true);
                    if (adapter.getItemCount() == 0) {
                        roomEmptyLayout.setVisibility(View.VISIBLE);
                    }
                    else {
                        roomEmptyLayout.setVisibility(View.INVISIBLE);
                    }
                }
            }
        });
        return controlView;
    }

    void showActiveUsers() {

        adapter=new InCallUsersAdapter(activeUserList,mContext, mServerName, mOwnId, mInstance);
        recyclerView.swapAdapter(adapter, true);
        roomEmptyLayout.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onStart() {
        super.onStart();

        Bundle args = getArguments();
        if (args != null) {

            if (args.containsKey(WebsocketService.EXTRA_OWN_ID)) {
                mOwnId = args.getString(WebsocketService.EXTRA_OWN_ID);
            }

            if (args.containsKey(WebsocketService.EXTRA_ADDRESS)) {
                mServerName = args.getString(WebsocketService.EXTRA_ADDRESS);
            }
            videoCallEnabled = args.getBoolean(CallActivity.EXTRA_VIDEO_CALL, true);
        }

        layoutManager=new LinearLayoutManager(mContext);
        recyclerView.setLayoutManager(layoutManager);

        adapter=new InCallUsersAdapter(activeUserList,mContext, mServerName, mOwnId, this);
        recyclerView.setAdapter(adapter);
    }

    @Override
    public void onStop() {
        super.onStop();

    }

    // TODO(sakal): Replace with onAttach(Context) once we only support API level 23+.
    @SuppressWarnings("deprecation")
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        parentActivity = new WeakReference<CallActivity>((CallActivity)activity);
        //callEvents = (OnUserEvents) activity;
    }
}
