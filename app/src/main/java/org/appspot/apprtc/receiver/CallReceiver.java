package org.appspot.apprtc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import org.appspot.apprtc.CallActivity;
import org.appspot.apprtc.RoomActivity;
import org.appspot.apprtc.SerializableIceCandidate;
import org.appspot.apprtc.SerializableSessionDescription;
import org.appspot.apprtc.User;
import org.appspot.apprtc.service.WebsocketService;

public class CallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(WebsocketService.ACTION_REMOTE_ICE_CANDIDATE)) {
            Intent activityIntent = new Intent(context, CallActivity.class);
            SerializableIceCandidate candidate = (SerializableIceCandidate)intent.getParcelableExtra(WebsocketService.EXTRA_CANDIDATE);
            activityIntent.putExtra(WebsocketService.EXTRA_CANDIDATE, candidate);
            String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
            activityIntent.putExtra(WebsocketService.EXTRA_ID, id);
            String token = intent.getStringExtra(WebsocketService.EXTRA_TOKEN);
            activityIntent.putExtra(WebsocketService.EXTRA_TOKEN, token);

            if (id.length() != 0) {
                activityIntent.setClass(context, RoomActivity.class);
            }

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

            if (token.length() != 0) {
                activityIntent.setClass(context, RoomActivity.class);
            }

            String id = intent.getStringExtra(WebsocketService.EXTRA_ID);
            activityIntent.putExtra(WebsocketService.EXTRA_ID, id);

            activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(activityIntent);
        }
    }
}