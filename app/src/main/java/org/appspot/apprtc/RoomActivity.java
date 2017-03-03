package org.appspot.apprtc;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.design.widget.TabLayout;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.appspot.apprtc.fragment.ChatFragment;
import org.appspot.apprtc.fragment.FilesFragment;
import org.appspot.apprtc.fragment.RoomFragment;
import org.appspot.apprtc.service.WebsocketService;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

public class RoomActivity extends AppCompatActivity {
    public static final String EXTRA_ROOM_NAME = "org.appspot.apprtc.EXTRA_ROOM_NAME";
    public static final String EXTRA_SERVER_NAME = "org.appspot.apprtc.EXTRA_SERVER_NAME";

    static final String BUDDY_IMG_PATH = "/webrtc/static/img/buddy/s46/";
    String mRoomName = "";
    WebsocketService mService;
    boolean mWebsocketServiceBound = false;
    private IntentFilter mIntentFilter;

    private Toolbar toolbar;
    private TabLayout tabLayout;
    private ViewPager viewPager;

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
                ShowMessage(message, time, status);
            }
        }
    };
    private RoomFragment mRoomFragment;

    private void ShowMessage(String message, String time, String status) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
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

        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WebsocketService.ACTION_CONNECTED);
        mIntentFilter.addAction(WebsocketService.ACTION_DISCONNECTED);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_ENTERED);
        mIntentFilter.addAction(WebsocketService.ACTION_USER_LEFT);
        mIntentFilter.addAction(WebsocketService.ACTION_CHAT_MESSAGE);

        toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        viewPager = (ViewPager) findViewById(R.id.viewpager);
        setupViewPager(viewPager);

        tabLayout = (TabLayout) findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(viewPager);
        setupTabIcons();



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
        adapter.addFrag(mRoomFragment, getString(R.string.rooms));
        adapter.addFrag(new ChatFragment(), getString(R.string.recent));
        adapter.addFrag(new FilesFragment(), getString(R.string.files));
        viewPager.setAdapter(adapter);
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
}
