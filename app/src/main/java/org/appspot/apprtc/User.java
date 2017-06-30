package org.appspot.apprtc;


import java.io.Serializable;

import static org.appspot.apprtc.User.CallState.CONNECTED;
import static org.appspot.apprtc.User.CallState.NONE;

public class User implements Serializable{
    String userId;
    public String buddyPicture;
    public String displayName;
    public String Id;
    public String message;
    public CallState mCallState = NONE;
    private long mConnectedTime;

    public long getConnectedTime() {
        return mConnectedTime;
    }

    public enum CallState {
        NONE,
        CALLING,
        CONNECTED,
        HOLD
    }

    public User(String userid, String pic, String name, String id) {
        this.userId = userid;
        this.buddyPicture = pic;
        this.displayName = name;
        this.Id = id;
    }

    public void setCallState(CallState state) {
        if (mCallState != CONNECTED && state == CONNECTED) {
            mConnectedTime = System.currentTimeMillis();
        }
        else if (state != CONNECTED) {
            mConnectedTime = 0;
        }
        mCallState = state;
    }

    public CallState getCallState() {
        return mCallState;
    }
}