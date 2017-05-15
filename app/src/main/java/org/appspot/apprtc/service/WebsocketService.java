package org.appspot.apprtc.service;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Handler;
import android.os.IBinder;

import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.FileInfo;
import org.appspot.apprtc.SerializableIceCandidate;
import org.appspot.apprtc.SerializableSessionDescription;
import org.appspot.apprtc.User;
import org.appspot.apprtc.WebSocketRTCClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.appspot.apprtc.RoomActivity.EXTRA_TO;

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
    public static final String EXTRA_RESPONSE = "org.appspot.apprtc.service.EXTRA_RESPONSE";
    public static final String EXTRA_MESSAGE = "org.appspot.apprtc.service.EXTRA_MESSAGE";
    public static final String EXTRA_TIME = "org.appspot.apprtc.service.EXTRA_TIME";
    public static final String EXTRA_STATUS = "org.appspot.apprtc.service.EXTRA_STATUS";
    public static final String ACTION_PATCH_RESPONSE = "org.appspot.apprtc.service.ACTION_PATCH_RESPONSE";
    public static final String ACTION_POST_RESPONSE = "org.appspot.apprtc.service.ACTION_POST_RESPONSE";
    public static final String ACTION_CHAT_MESSAGE = "org.appspot.apprtc.service.ACTION_CHAT_MESSAGE";
    public static final String ACTION_FILE_MESSAGE = "org.appspot.apprtc.service.ACTION_FILE_MESSAGE";
    public static final String EXTRA_TOKEN = "org.appspot.apprtc.service.EXTRA_TOKEN";
    public static final String EXTRA_ID = "org.appspot.apprtc.service.EXTRA_ID";
    public static final String EXTRA_OWN_ID = "org.appspot.apprtc.service.EXTRA_OWN_ID";
    public static final String EXTRA_FILEINFO = "org.appspot.apprtc.service.EXTRA_FILEINFO";
    public static final String ACTION_ADD_CONFERENCE_USER = "org.appspot.apprtc.service.ACTION_ADD_CONFERENCE_USER";
    public static final String EXTRA_CONFERENCE_ID = "org.appspot.apprtc.service.EXTRA_CONFERENCE_ID";
    public static final String ACTION_ADD_ALL_CONFERENCE = "org.appspot.apprtc.service.ACTION_ADD_ALL_CONFERENCE";
    public static final String EXTRA_USERACTION = "org.appspot.apprtc.service.EXTRA_USERACTION";

    private String mServer = "";

    private Handler selfHandler = new Handler();
    private Runnable selfRenew = new Runnable() {

        @Override
        public void run() {
            if (appRtcClient != null) {
                appRtcClient.sendSelf();
            }
        }
    };

    public String getCurrentRoomName() {
        if (appRtcClient != null) {
            return appRtcClient.getRoomName();
        }
        return null;
    }
	
    public void disconnectFromServer() {
        if (appRtcClient != null) {
            appRtcClient.disconnectFromRoom();
            mState = ConnectionState.DISCONNECTED;
            mUsers.clear();
        }
    }


    enum ConnectionState {
        DISCONNECTED,
        CONNECTED,
        ERROR
    }

    ConnectionState mState;

    // Binder given to clients
    private final IBinder mBinder = new WebsocketBinder();

    private AppRTCClient appRtcClient;
    private HashMap<String, ArrayList<User>> mUsers = new HashMap<String, ArrayList<User>>();
    static private List<PeerConnection.IceServer> mIceServers = new ArrayList<PeerConnection.IceServer>();

    public ArrayList<User> getUsersInRoom(String roomName) {
        return mUsers.get(roomName);
    }

    public static List<PeerConnection.IceServer> getIceServers() {
        return mIceServers;
    }

    public static void clearIceServers() {
        mIceServers.clear();
    }

    public String getServerAddress() {
        return mServer;
    }

    public void connectToRoom(String roomName) {
        if (appRtcClient != null) {
            appRtcClient.connectToRoom(roomName);
        }
    }

    public void disconnectFromRoom() {
        if (appRtcClient != null) {
            appRtcClient.disconnectFromRoom();
        }
    }

    public void sendOfferSdp(SessionDescription sdp, String to) {
        if (appRtcClient != null) {
            appRtcClient.sendOfferSdp(sdp, to);
        }
    }

    public void sendTokenOfferSdp(SessionDescription sdp, String token, String id, String to) {
        if (appRtcClient != null) {
            appRtcClient.sendTokenOffer(sdp, token, id, to);
        }
    }

    public void sendTokenAnswerSdp(SessionDescription sdp, String token, String id, String to) {
        if (appRtcClient != null) {
            appRtcClient.sendTokenAnswer(sdp, token, id, to);
        }
    }

    public void sendAnswerSdp(SessionDescription sdp, String to) {
        if (appRtcClient != null) {
            appRtcClient.sendAnswerSdp(sdp, to);
        }
    }

    public void sendLocalIceCandidate(SerializableIceCandidate iceCandidate, String token, String id, String to) {
        if (appRtcClient != null) {
            appRtcClient.sendLocalIceCandidate(iceCandidate, token, id, to);
        }
    }

    public void sendLocalIceCandidateRemovals(SerializableIceCandidate[] iceCandidates, String to) {
        if (appRtcClient != null) {
            appRtcClient.sendLocalIceCandidateRemovals(iceCandidates, to);
        }
    }

    public void connectToServer(String address) {
        mServer = address;
        if (appRtcClient != null) {
            appRtcClient.connectToServer(address);
        }
    }

    public void leaveRoom() {
        if (appRtcClient != null) {
            appRtcClient.sendLeave();
        }
    }

    public void sendChatMessage(String message, String to) {
        if (appRtcClient != null) {
            appRtcClient.sendChatMessage(message, to);
        }
    }

    public void sendFileMessage(String message, long size, String name, String mime, String to) {
        if (appRtcClient != null) {
            appRtcClient.sendFileMessage(message, size, name, mime, to);
        }
    }

    public void sendPatchMessage(String username, String password, String url) {
        if (appRtcClient != null) {
            appRtcClient.sendPatchMessage(username, password, url);
        }
    }

    public void sendPostMessage(String username, String password, String url) {
        if (appRtcClient != null) {
            appRtcClient.sendPostMessage(username, password, url);
        }
    }

    public void sendStatus(String displayName, String buddyPicture, String message) {
        if (appRtcClient != null) {
            appRtcClient.sendStatus(displayName, buddyPicture, message);
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

        boolean found = false;
        for (User u : users) {
            if (u.Id.equals(user.Id)) {
                found = true;
                break;
            }
        }

        if (!found) {
            users.add(user);
            mUsers.put(room, users);

            Intent broadcastIntent = new Intent();
            broadcastIntent.setAction(ACTION_USER_ENTERED);
            broadcastIntent.putExtra(EXTRA_USER, user);
            broadcastIntent.putExtra(EXTRA_ROOM_NAME, room);
            sendBroadcast(broadcastIntent);
        }
    }

    @Override
    public void onUserLeftRoom(User user, String room) {
        ArrayList<User> users = mUsers.get(room);
        for (User u : users) {
            if (u.Id.equals(user.Id)) {
                users.remove(u);
                mUsers.put(room, users);
                break;
            }
        }

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_USER_LEFT);
        broadcastIntent.putExtra(EXTRA_USER, user);
        broadcastIntent.putExtra(EXTRA_ROOM_NAME, room);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onBye(final String reason, String fromId, String roomName) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_BYE);
        broadcastIntent.putExtra(EXTRA_REASON, reason);
        ArrayList<User> users = mUsers.get(roomName);
        if (users != null) {
            for (User user : users) {
                if (user.Id.equals(fromId)) {
                    broadcastIntent.putExtra(EXTRA_USER, user);
                }
            }
        }
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void sendBye(String to) {
        if (appRtcClient != null) {
            appRtcClient.sendBye(to);
        }
    }

    @Override
    public void onPatchResponse(String response) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_PATCH_RESPONSE);
        broadcastIntent.putExtra(EXTRA_RESPONSE, response);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onPostResponse(String response) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_POST_RESPONSE);
        broadcastIntent.putExtra(EXTRA_RESPONSE, response);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onConfigResponse(String response) {
       /* try {
            JSONObject json = new JSONObject(response);
            String username = json.getString("TurnUsername");
            String password = json.getString("TurnPassword");
            JSONArray stunUris = json.getJSONArray("StunURIs");
            for (int count = 0; count < stunUris.length(); count++) {
                String stunUri = stunUris.getString(count);
                PeerConnection.IceServer iceServer = new PeerConnection.IceServer(stunUri, "", "");
                mIceServers.add(iceServer);
            }
            JSONArray turnUris = json.getJSONArray("TurnURIs");
            for (int count = 0; count < turnUris.length(); count++) {
                String turnUri = turnUris.getString(count);
                PeerConnection.IceServer iceServer = new PeerConnection.IceServer(turnUri, username, password);
                mIceServers.add(iceServer);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }*/
    }

    @Override
    public void onChatMessage(String message, String time, String status, String to, String fromId, String roomName) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_CHAT_MESSAGE);
        broadcastIntent.putExtra(EXTRA_MESSAGE, message);
        broadcastIntent.putExtra(EXTRA_TIME, time);
        broadcastIntent.putExtra(EXTRA_STATUS, status);
        broadcastIntent.putExtra(EXTRA_TO, to);
        ArrayList<User> users = mUsers.get(roomName);
        if (users != null) {
            for (User user : users) {
                if (user.Id.equals(fromId)) {
                    broadcastIntent.putExtra(EXTRA_USER, user);
                }
            }
        }
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onFileMessage(String time, String id, String chunks, String name, String size, String filetype, String fromId, String roomName) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_FILE_MESSAGE);
        broadcastIntent.putExtra(EXTRA_ID, id);
        broadcastIntent.putExtra(EXTRA_TIME, time);
        FileInfo fileInfo = new FileInfo(id, chunks, name, size, filetype);
        broadcastIntent.putExtra(EXTRA_FILEINFO, fileInfo);

        ArrayList<User> users = mUsers.get(roomName);
        if (users != null) {
            for (User user : users) {
                if (user.Id.equals(fromId)) {
                    broadcastIntent.putExtra(EXTRA_USER, user);
                }
            }
        }
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onAddTurnServer(String url, String username, String password) {

        boolean found = false;
        for (PeerConnection.IceServer server: mIceServers) {
            if (server.uri.equals(url) && server.username.equals(username) && server.password.equals(password)) {
                found = true;
                break;
            }
        }

        if (!found) {
            PeerConnection.IceServer iceServer = new PeerConnection.IceServer(url, username, password);
            mIceServers.add(iceServer);
        }
    }

    @Override
    public void onAddStunServer(String url, String username, String password) {

        boolean found = false;
        for (PeerConnection.IceServer server: mIceServers) {
            if (server.uri.equals(url) && server.username.equals(username) && server.password.equals(password)) {
                found = true;
                break;
            }
        }

        if (!found) {
            PeerConnection.IceServer iceServer = new PeerConnection.IceServer(url, username, password);
            mIceServers.add(iceServer);
        }
    }

    @Override
    public void onRemoteDescription(SerializableSessionDescription sdp, String token, String id, String fromId, String roomName) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_REMOTE_DESCRIPTION);
        broadcastIntent.putExtra(EXTRA_REMOTE_DESCRIPTION, sdp);
        broadcastIntent.putExtra(EXTRA_TOKEN, token);
        broadcastIntent.putExtra(EXTRA_ID, id);

        broadcastIntent.putExtra(EXTRA_OWN_ID, appRtcClient.getId());
        broadcastIntent.putExtra(EXTRA_CONFERENCE_ID, conferenceId);
        broadcastIntent.putExtra(EXTRA_ADDRESS, mServer);

        ArrayList<User> users = mUsers.get(roomName);
        if (users != null) {
            for (User user : users) {
                if (user.Id.equals(fromId)) {
                    broadcastIntent.putExtra(EXTRA_USER, user);
                }
            }
        }
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onRemoteIceCandidate(SerializableIceCandidate candidate, String id, String token) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_REMOTE_ICE_CANDIDATE);
        broadcastIntent.putExtra(EXTRA_CANDIDATE, candidate);
        broadcastIntent.putExtra(EXTRA_ID, id);
        broadcastIntent.putExtra(EXTRA_TOKEN, token);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onRemoteIceCandidatesRemoved(SerializableIceCandidate[] candidates) {

    }

    @Override
    public void onSelf() {
        clearIceServers();
    }

    @Override
    public void onTurnTtl(int ttl) {
        long interval = (long)(ttl - (ttl * 0.1f)) * 1000; // ms

        selfHandler.postDelayed(selfRenew, interval);
    }

    @Override
    public void onConferenceUser(String roomName, String conferenceId, String id) {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_ADD_CONFERENCE_USER);
        broadcastIntent.putExtra(EXTRA_ID, id);
        broadcastIntent.putExtra(EXTRA_OWN_ID, appRtcClient.getId());
        broadcastIntent.putExtra(EXTRA_CONFERENCE_ID, conferenceId);
        ArrayList<User> users = mUsers.get(roomName);
        if (users != null) {
            for (User user : users) {
                if (user.Id.equals(id)) {
                    broadcastIntent.putExtra(EXTRA_USER, user);
                }
            }
        }
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onChannelOpen() {
        mState = ConnectionState.CONNECTED;
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_CONNECTED);
        sendBroadcast(broadcastIntent);
    }

    @Override
    public void onChannelClose() {
        mState = ConnectionState.DISCONNECTED;
        broadcastClose();
    }

    @Override
    public void onChannelError(String description) {
        mState = ConnectionState.ERROR;
    }


    private void broadcastClose() {

        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_DISCONNECTED);
        sendBroadcast(broadcastIntent);
    }

    public void sendAuthentication(String userid, String nonce) {
        if (appRtcClient != null) {
            appRtcClient.sendAuthentication(userid, nonce);
        }
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

    public boolean getIsConnected() {
        return mState == ConnectionState.CONNECTED;
    }
}