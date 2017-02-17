package org.appspot.apprtc.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

import org.appspot.apprtc.AppRTCClient;
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

    // Binder given to clients
    private final IBinder mBinder = new WebsocketBinder();

    private AppRTCClient appRtcClient;
    private HashMap<String, ArrayList<String>> mUsers = new HashMap<String, ArrayList<String>>();

    public ArrayList<String> getUsersInRoom(String roomName) {
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
            appRtcClient.sendOfferSdp(sdp);
        }
    }

    public void sendLocalIceCandidate(IceCandidate iceCandidate) {
        if (appRtcClient != null) {
            appRtcClient.sendLocalIceCandidate(iceCandidate);
        }
    }

    public void sendLocalIceCandidateRemovals(IceCandidate[] iceCandidates) {
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
    public void onUserEnteredRoom(String user, String room) {
        ArrayList<String> users = mUsers.get(room);
        if (!users.contains(user)) {
            users.add(user);
            mUsers.put(room, users);
        }
    }

    @Override
    public void onUserLeftRoom(String user, String room) {
        ArrayList<String> users = mUsers.get(room);
        if (users.contains(user)) {
            users.remove(user);
            mUsers.put(room, users);
        }
    }

    @Override
    public void onRemoteDescription(SessionDescription sdp) {

    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {

    }

    @Override
    public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {

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

        if (intent.getAction() != null && intent.getAction().equals(ACTION_CONNECT)) {
            if (intent.hasExtra(EXTRA_ADDRESS)) {
                String address = intent.getExtras().getString(EXTRA_ADDRESS);
                appRtcClient.connectToServer(address);
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