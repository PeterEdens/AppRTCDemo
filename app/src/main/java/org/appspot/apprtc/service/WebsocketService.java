package org.appspot.apprtc.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.SerializableIceCandidate;
import org.appspot.apprtc.SerializableSessionDescription;
import org.appspot.apprtc.User;
import org.appspot.apprtc.WebSocketRTCClient;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;

public class WebsocketService extends Service implements AppRTCClient.SignalingEvents{
    public static final String ACTION_CONNECT = "org.appspot.apprtc.service.ACTION_CONNECT";
    public static final String ACTION_DISCONNECT = "org.appspot.apprtc.service.ACTION_DISCONNECT";
    public static final String ACTION_CONNECTED = "org.appspot.apprtc.service.ACTION_CONNECTED";
    public static final String ACTION_DISCONNECTED = "org.appspot.apprtc.service.ACTION_DISCONNECTED";
    public static final String EXTRA_ADDRESS = "org.appspot.apprtc.service.EXTRA_ADDRESS";
    public static final String ACTION_CONNECTED_TO_ROOM = "org.appspot.apprtc.service.ACTION_CONNECTED_TO_ROOM";
    public static final String EXTRA_ROOM_NAME = "org.appspot.apprtc.service.EXTRA_ROOM_NAME";
    public static final String ACTION_USER_ENTERED = "org.appspot.apprtc.service.ACTION_USER_ENTERED";
    public static final String ACTION_USER_LEFT = "org.appspot.apprtc.service.ACTION_USER_LEFT";
    public static final String ACTION_REMOTE_ICE_CANDIDATE = "org.appspot.apprtc.service.ACTION_REMOTE_ICE_CANDIDATE";
    public static final String ACTION_REMOTE_DESCRIPTION = "org.appspot.apprtc.service.ACTION_REMOTE_DESCRIPTION";
    public static final String ACTION_BYE = "org.appspot.apprtc.service.ACTION_BYE";
    public static final String EXTRA_REASON = "org.appspot.apprtc.service.EXTRA_REASON";
    public static final String EXTRA_USER = "org.appspot.apprtc.service.EXTRA_USER";
    public static final String EXTRA_CANDIDATE = "org.appspot.apprtc.service.EXTRA_CANDIDATE";
    public static final String EXTRA_REMOTE_DESCRIPTION = "org.appspot.apprtc.service.EXTRA_REMOTE_DESCRIPTION";

    // Binder given to clients
    private final IBinder mBinder = new WebsocketBinder();

    private AppRTCClient appRtcClient;
    private HashMap<String, ArrayList<User>> mUsers = new HashMap<String, ArrayList<User>>();

    public ArrayList<User> getUsersInRoom(String roomName) {
        return mUsers.get(roomName);
    }

    public void connectToRoom(AppRTCClient.RoomConnectionParameters connectionParameters) {
        if (appRtcClient != null) {
            appRtcClient.connectToRoom(connectionParameters);
        }
    }

    public void disconnectFromRoom() {
        if (appRtcClient != null) {
            appRtcClient.disconnectFromRoom();
        }
    }

    public void sendOfferSdp(SessionDescription sdp) {
        if (appRtcClient != null) {
            appRtcClient.sendOfferSdp(sdp);
        }
    }

    public void sendAnswerSdp(SessionDescription sdp) {
        if (appRtcClient != null) {
            appRtcClient.sendAnswerSdp(sdp);
        }
    }

    public void sendLocalIceCandidate(SerializableIceCandidate iceCandidate) {
        if (appRtcClient != null) {
            appRtcClient.sendLocalIceCandidate(iceCandidate);
        }
    }

    public void sendLocalIceCandidateRemovals(SerializableIceCandidate[] iceCandidates) {
        if (appRtcClient != null) {
            appRtcClient.sendLocalIceCandidateRemovals(iceCandidates);
        }
    }

    public void connectToServer(String address) {
        if (appRtcClient != null) {
            appRtcClient.connectToServer(address);
        }
    }

    @Override
    public void onConnectedToRoom(String roomName) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_CONNECTED_TO_ROOM);
        broadcastIntent.putExtra(EXTRA_ROOM_NAME, roomName);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onUserEnteredRoom(User user, String room) {
        ArrayList<User> users = mUsers.get(room);

        if (users == null) {
            users = new ArrayList<User>();
        }

        if (!users.contains(user)) {
            users.add(user);
            mUsers.put(room, users);
        }

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_USER_ENTERED);
        broadcastIntent.putExtra(EXTRA_USER, user);
        broadcastIntent.putExtra(EXTRA_ROOM_NAME, room);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onUserLeftRoom(User user, String room) {
        ArrayList<User> users = mUsers.get(room);
        if (users.contains(user)) {
            users.remove(user);
            mUsers.put(room, users);
        }

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_USER_LEFT);
        broadcastIntent.putExtra(EXTRA_USER, user);
        broadcastIntent.putExtra(EXTRA_ROOM_NAME, room);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onBye(final String reason) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_BYE);
        broadcastIntent.putExtra(EXTRA_REASON, reason);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onRemoteDescription(SerializableSessionDescription sdp) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_REMOTE_DESCRIPTION);
        broadcastIntent.putExtra(EXTRA_REMOTE_DESCRIPTION, sdp);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onRemoteIceCandidate(SerializableIceCandidate candidate) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_REMOTE_ICE_CANDIDATE);
        broadcastIntent.putExtra(EXTRA_CANDIDATE, candidate);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onRemoteIceCandidatesRemoved(SerializableIceCandidate[] candidates) {

    }

    @Override
    public void onChannelOpen() {

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_CONNECTED);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onChannelClose() {
        broadcastClose();
    }

    @Override
    public void onChannelError(String description) {

    }


    private void broadcastClose() {

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_DISCONNECTED);
        sendBroadcast(broadcastIntent);
    }

    /**
     * Class used for the client Binder.  Because we know this service always
     * runs in the same process as its clients, we don't need to deal with IPC.
     */
    public class WebsocketBinder extends Binder {
        public WebsocketService getService() {
            // Return this instance of LocalService so clients can call public methods
            return WebsocketService.this;
        }

    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if (appRtcClient == null) {
            appRtcClient = new WebSocketRTCClient(this);
        }

        if (intent != null) {
            if (intent.getAction() != null && intent.getAction().equals(ACTION_CONNECT)) {
                if (intent.hasExtra(EXTRA_ADDRESS)) {
                    String address = intent.getExtras().getString(EXTRA_ADDRESS);
                    appRtcClient.connectToServer(address);
                }
            }
        }
        // We want this service to continue running until it is explicitly
        // stopped, so return sticky.
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        if (appRtcClient != null) {
            appRtcClient.disconnectFromRoom();
            appRtcClient = null;
        }

        super.onDestroy();
    }
}