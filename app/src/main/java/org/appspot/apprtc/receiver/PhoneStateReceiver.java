package org.appspot.apprtc.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

public class PhoneStateReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        //Create object of Telephony Manager class.
        TelephonyManager telephony = (TelephonyManager)  context.getSystemService(Context.TELEPHONY_SERVICE);
        //Assign a phone state listener.
        CustomPhoneStateListener customPhoneListener = new CustomPhoneStateListener (context);
        telephony.listen(customPhoneListener, PhoneStateListener.LISTEN_CALL_STATE);
    }
}