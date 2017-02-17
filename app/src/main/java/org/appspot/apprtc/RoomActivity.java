package org.appspot.apprtc;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.ListView;
import android.widget.TextView;

import org.appspot.apprtc.service.WebsocketService;
import org.w3c.dom.Text;

import java.util.ArrayList;

public class RoomActivity extends AppCompatActivity {
    static final String EXTRA_ROOM_NAME = "org.appspot.apprtc.EXTRA_ROOM_NAME";
    static final String EXTRA_SERVER_NAME = "org.appspot.apprtc.EXTRA_SERVER_NAME";

    static final String BUDDY_IMG_PATH = "/webrtc/static/img/buddy/s46/";

    private RecyclerView recyclerView;
    private RecyclerView.Adapter adapter;
    private RecyclerView.LayoutManager layoutManager;
    private ArrayList<User> userList=new ArrayList();
    private String mRoomName = "";
    private String mServerName = "";
    TextView mRoomNameTextView;

    WebsocketService mService;
    boolean mWebsocketServiceBound = false;
    private IntentFilter mIntentFilter;

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
            if (users != null) {
                userList.addAll(users);
            }
            adapter.notifyDataSetChanged();
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


            } else if (intent.getAction().equals(WebsocketService.ACTION_USER_LEFT)) {


            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_room);

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WebsocketService.ACTION_CONNECTED);
        mIntentFilter.addAction(WebsocketService.ACTION_DISCONNECTED);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_ENTERED);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_LEFT);

        Intent intent = getIntent();

        if (intent != null && intent.hasExtra(EXTRA_ROOM_NAME)) {
            mRoomName = intent.getStringExtra(EXTRA_ROOM_NAME);
        }

        if (intent != null && intent.hasExtra(EXTRA_SERVER_NAME)) {
            mServerName = intent.getStringExtra(EXTRA_SERVER_NAME);
        }

        mRoomNameTextView = (TextView) findViewById(R.id.roomName);
        mRoomNameTextView.setText(mRoomName);
        recyclerView= (RecyclerView) findViewById(R.id.recycler_view);
        layoutManager=new LinearLayoutManager(this);
        recyclerView.setLayoutManager(layoutManager);

        adapter=new UsersAdapter(userList,getApplicationContext(), mServerName);
        recyclerView.setAdapter(adapter);
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
    }

}
