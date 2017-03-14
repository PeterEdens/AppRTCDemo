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
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.appspot.apprtc.fragment.ChatFragment;
import org.appspot.apprtc.fragment.FilesFragment;
import org.appspot.apprtc.fragment.RoomFragment;
import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.util.ThumbnailsCacheManager;
import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

public class RoomActivity extends DrawerActivity implements ChatFragment.OnChatEvents {
    public static final String EXTRA_ROOM_NAME = "org.appspot.apprtc.EXTRA_ROOM_NAME";
    public static final String EXTRA_SERVER_NAME = "org.appspot.apprtc.EXTRA_SERVER_NAME";
    public static final String ACTION_NEW_CHAT = "org.appspot.apprtc.ACTION_NEW_CHAT";
    public static final String EXTRA_USER = "org.appspot.apprtc.EXTRA_USER";

    static final int CHAT_INDEX = 1;

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
                else if (user != null) {
                    ShowMessage(user.displayName, message, time, status, user.buddyPicture, user.Id);
                }
            }
        }
    };
    private RoomFragment mRoomFragment;
    private boolean mWaitingToEnterRoom;
    private ChatFragment mChatFragment;

    private void ShowMessage(String displayName, String message, String time, String status, String buddyPicture, String Id) {
        mChatFragment.addMessage(new ChatItem(time, displayName, message, buddyPicture, Id));
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

        Intent intent = getIntent();
        handleIntent(intent);

    }

    private void handleIntent(Intent intent) {

        String action = intent.getAction();

        if (action != null && action.equals(ACTION_NEW_CHAT)) {
            viewPager.setCurrentItem(CHAT_INDEX);
            User user = (User) intent.getSerializableExtra(EXTRA_USER);
            mChatFragment.setUser(user);
        }

        if (intent.hasExtra(EXTRA_ROOM_NAME)) {
            mRoomName = intent.getStringExtra(EXTRA_ROOM_NAME);
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
}
