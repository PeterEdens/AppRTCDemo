package org.appspot.apprtc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.sharedresourceslib.BroadcastTypes;

import org.appspot.apprtc.CallActivity;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.SerializableIceCandidate;
import org.appspot.apprtc.SerializableSessionDescription;
import org.appspot.apprtc.TokenPeerConnection;
import org.appspot.apprtc.User;
import org.appspot.apprtc.fragment.ChatFragment;
import org.appspot.apprtc.service.WebsocketService;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.HashMap;

import static org.appspot.apprtc.RoomActivity.CHAT_INDEX;

public class CallReceiver extends BroadcastReceiver implements TokenPeerConnection.TokenPeerConnectionEvents {

    private static HashMap<String, TokenPeerConnection> mPeerConnections = new HashMap<String, TokenPeerConnection>();
    private Context mContext;

    @Override
    public void onReceive(Context context, Intent intent) {
        mContext = context;

        if (intent.getAction().equals(WebsocketService.ACTION_REMOTE_ICE_CANDIDATE)) {
            Intent activityIntent = new Intent(context, CallActivity.class);
            SerializableIceCandidate candidate = (SerializableIceCandidate)intent.getParcelableExtra(WebsocketService.EXTRA_CANDIDATE);
            activityIntent.putExtra(WebsocketService.EXTRA_CANDIDATE, candidate);
            String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
            activityIntent.putExtra(WebsocketService.EXTRA_ID, id);
            String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);
            activityIntent.putExtra(WebsocketService.EXTRA_TOKEN, token);

            if (token.length() != 0 && !token.startsWith("screenshare")) {
                handleTokenRemoteIceCandidate(intent);
            }
            else {

                activityIntent.setAction(WebsocketService.ACTION_REMOTE_ICE_CANDIDATE);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(activityIntent);
            }
        }
        else if (intent.getAction().equals(BroadcastTypes.ACTION_REMOTE_ICE_CANDIDATE)) {
            Intent activityIntent = new Intent(context, CallActivity.class);
            SerializableIceCandidate candidate = (SerializableIceCandidate)intent.getParcelableExtra(BroadcastTypes.EXTRA_CANDIDATE);
            activityIntent.putExtra(WebsocketService.EXTRA_CANDIDATE, candidate);
            String id = intent.getStringExtra(BroadcastTypes.EXTRA_ACCOUNT_JID);
            activityIntent.putExtra(WebsocketService.EXTRA_ID, id);
            String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);
            activityIntent.putExtra(WebsocketService.EXTRA_TOKEN, token);



            activityIntent.setAction(WebsocketService.ACTION_REMOTE_ICE_CANDIDATE);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
        }
        else if (intent.getAction().equals(WebsocketService.ACTION_REMOTE_DESCRIPTION)) {
            Intent activityIntent = new Intent(context, CallActivity.class);
            SerializableSessionDescription candidate = (SerializableSessionDescription)intent.getSerializableExtra(WebsocketService.EXTRA_REMOTE_DESCRIPTION);
            activityIntent.putExtra(WebsocketService.EXTRA_REMOTE_DESCRIPTION, candidate);
            activityIntent.setAction(WebsocketService.ACTION_REMOTE_DESCRIPTION);

            if (intent.hasExtra(WebsocketService.EXTRA_USER)) {
                User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
                activityIntent.putExtra(WebsocketService.EXTRA_USER, user);
            }
            String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);
            activityIntent.putExtra(WebsocketService.EXTRA_TOKEN, token);

            if (token != null && token.length() != 0 && !token.startsWith("screenshare")) {
                handleTokenRemoteDescription(context, intent);
            }
            else {

                String ownId = intent.getStringExtra(WebsocketService.EXTRA_OWN_ID);
                activityIntent.putExtra(WebsocketService.EXTRA_OWN_ID, ownId);

                String serverAddress = intent.getStringExtra(WebsocketService.EXTRA_ADDRESS);
                activityIntent.putExtra(WebsocketService.EXTRA_ADDRESS, serverAddress);

                String conferenceId = intent.getStringExtra(WebsocketService.EXTRA_CONFERENCE_ID);
                activityIntent.putExtra(WebsocketService.EXTRA_CONFERENCE_ID, conferenceId);

                String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
                activityIntent.putExtra(WebsocketService.EXTRA_ID, id);

                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(activityIntent);
            }
        }
        else if (intent.getAction().equals(BroadcastTypes.ACTION_REMOTE_DESCRIPTION)) {
            Intent activityIntent = new Intent(context, CallActivity.class);
            SerializableSessionDescription candidate = (SerializableSessionDescription)intent.getSerializableExtra(BroadcastTypes.EXTRA_REMOTE_DESCRIPTION);
            activityIntent.putExtra(WebsocketService.EXTRA_REMOTE_DESCRIPTION, candidate);
            activityIntent.setAction(WebsocketService.ACTION_REMOTE_DESCRIPTION);

            String signaling = intent.getStringExtra(BroadcastTypes.EXTRA_SIGNALING);
            activityIntent.putExtra(BroadcastTypes.EXTRA_SIGNALING, signaling);

            String sid = intent.getStringExtra(BroadcastTypes.EXTRA_SID);
            activityIntent.putExtra(BroadcastTypes.EXTRA_SID, sid);

            String ownId = intent.getStringExtra(BroadcastTypes.EXTRA_ACCOUNT_JID);
            activityIntent.putExtra(BroadcastTypes.EXTRA_ACCOUNT_JID, ownId);

            String id = intent.getStringExtra(BroadcastTypes.EXTRA_JID);
            activityIntent.putExtra(BroadcastTypes.EXTRA_JID, id);

            activityIntent.putExtra(CallActivity.EXTRA_VIDEO_CALL, false);
            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
        }
        else if (intent.getAction().equals(WebsocketService.ACTION_ADD_CONFERENCE_USER)) {
            Intent activityIntent = new Intent(context, CallActivity.class);
            activityIntent.setAction(WebsocketService.ACTION_ADD_CONFERENCE_USER);
            if (intent.hasExtra(WebsocketService.EXTRA_USER)) {
                User user = (User) intent.getSerializableExtra(WebsocketService.EXTRA_USER);
                activityIntent.putExtra(WebsocketService.EXTRA_USER, user);
            }

            String ownId = intent.getStringExtra(WebsocketService.EXTRA_OWN_ID);
            activityIntent.putExtra(WebsocketService.EXTRA_OWN_ID, ownId);

            String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
            activityIntent.putExtra(WebsocketService.EXTRA_ID, id);

            String conferenceId = intent.getStringExtra(WebsocketService.EXTRA_CONFERENCE_ID);
            activityIntent.putExtra(WebsocketService.EXTRA_CONFERENCE_ID, conferenceId);

            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
        }
    }

    private void handleTokenRemoteDescription(Context context, Intent intent) {
        SerializableSessionDescription sdp = (SerializableSessionDescription) intent.getSerializableExtra(WebsocketService.EXTRA_REMOTE_DESCRIPTION);
        String remoteId = sdp.from;
        String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);
        String id = intent.getStringExtra(WebsocketService.EXTRA_ID);

        String peerConnectionId = remoteId + token + id;
        if (mPeerConnections.containsKey(peerConnectionId)) {
            // an existing connection
            SessionDescription sd = new SessionDescription(sdp.type, sdp.description);
            mPeerConnections.get(peerConnectionId).setRemoteDescription(sd);
        } else {
            // does not exist, create the new connection
            TokenPeerConnection connection = new TokenPeerConnection(context, this, false, token, remoteId, id, WebsocketService.getIceServers(), -1);
            SessionDescription sd = new SessionDescription(sdp.type, sdp.description);
            connection.setRemoteDescription(sd);
            mPeerConnections.put(peerConnectionId, connection);

        }
    }

    private void handleTokenRemoteIceCandidate(Intent intent) {
        SerializableIceCandidate candidate = (SerializableIceCandidate)intent.getParcelableExtra(WebsocketService.EXTRA_CANDIDATE);
        String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
        String remoteId = candidate.from;
        String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);

        String peerConnectionId = remoteId + token + id;
        if (!mPeerConnections.containsKey(peerConnectionId)) {
            Log.e("RoomActivity", "Received remote ice candidate for non-initilized peer connection.");
            return;
        }

        IceCandidate ic = new IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp);
        mPeerConnections.get(peerConnectionId).addRemoteIceCandidate(ic);
    }


    @Override
    public void onDownloadedBytes(int index, long bytes, String to) {
        Intent intent = new Intent();
        intent.setAction(BroadcastTypes.ACTION_DOWNLOADED_BYTES);
        intent.putExtra(BroadcastTypes.EXTRA_INDEX, index);
        intent.putExtra(BroadcastTypes.EXTRA_BYTES, bytes);
        intent.putExtra(BroadcastTypes.EXTRA_TO, to);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void onDownloadComplete(int index, String to) {
        Intent intent = new Intent();
        intent.setAction(BroadcastTypes.ACTION_DOWNLOAD_COMPLETE);
        intent.putExtra(BroadcastTypes.EXTRA_INDEX, index);
        intent.putExtra(BroadcastTypes.EXTRA_TO, to);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void TokenOfferSdp(SessionDescription localSdp, String token, String connectionId, String remoteId) {
        WebsocketService.getInstance().sendTokenOfferSdp(localSdp, token, connectionId, remoteId);
    }

    @Override
    public void sendTokenAnswerSdp(SessionDescription localSdp, String token, String connectionId, String remoteId) {
        WebsocketService.getInstance().sendTokenAnswerSdp(localSdp, token, connectionId, remoteId);
    }

    @Override
    public void sendLocalIceCandidate(SerializableIceCandidate candidate, String token, String connectionId, String remoteId) {
        WebsocketService.getInstance().sendLocalIceCandidate(candidate, token, connectionId, remoteId);
    }

    @Override
    public void onDownloadPath(int index, String path, String to) {
        Intent intent = new Intent();
        intent.setAction(BroadcastTypes.ACTION_DOWNLOAD_PATH);
        intent.putExtra(BroadcastTypes.EXTRA_INDEX, index);
        intent.putExtra(BroadcastTypes.EXTRA_PATH, path);
        intent.putExtra(BroadcastTypes.EXTRA_TO, to);
        mContext.sendBroadcast(intent);
    }

    @Override
    public void onError(String description, int index, String to) {
        Intent intent = new Intent();
        intent.setAction(BroadcastTypes.ACTION_DOWNLOAD_ERROR);
        intent.putExtra(BroadcastTypes.EXTRA_INDEX, index);
        intent.putExtra(BroadcastTypes.EXTRA_DESCRIPTION, description);
        intent.putExtra(BroadcastTypes.EXTRA_TO, to);
        mContext.sendBroadcast(intent);
    }

    public static void removeConnection(String peerConnectionId) {

        if (mPeerConnections.containsKey(peerConnectionId)) {
            mPeerConnections.get(peerConnectionId).close();
            mPeerConnections.remove(peerConnectionId);
        }
    }

    public static void addConnection(String peerConnectionId, TokenPeerConnection connection) {
        mPeerConnections.put(peerConnectionId, connection);
    }
}