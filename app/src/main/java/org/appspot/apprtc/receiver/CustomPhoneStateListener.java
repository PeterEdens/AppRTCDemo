package org.appspot.apprtc.receiver;

import java.lang.reflect.Method;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;


public class CustomPhoneStateListener extends PhoneStateListener {
    public static final String ACTION_HOLD_ON = "org.appspot.apprtc.receiver.ACTION_HOLD_ON";
    public static final String ACTION_HOLD_OFF = "org.appspot.apprtc.receiver.ACTION_HOLD_OFF";
    Context context;

    public CustomPhoneStateListener(Context context) {
        super();
        this.context = context;
    }

    @Override
    public void onCallStateChanged(int state, String callingNumber)
    {
        super.onCallStateChanged(state, callingNumber);
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                notifyFinishedCall();
                break;
            case TelephonyManager.CALL_STATE_OFFHOOK:
                //handle out going call
                notifyActiveCall();
                break;
            case TelephonyManager.CALL_STATE_RINGING:
                //handle in coming call

                break;
            default:
                break;
        }
    }

    private void notifyActiveCall() {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_HOLD_ON);
        context.sendBroadcast(broadcastIntent);
    }

    private void notifyFinishedCall() {
        Intent broadcastIntent = new Intent();
        broadcastIntent.setAction(ACTION_HOLD_OFF);
        context.sendBroadcast(broadcastIntent);
    }
} 