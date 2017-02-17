package org.appspot.apprtc;

import org.webrtc.IceCandidate;

import java.io.Serializable;

public class SerializableIceCandidate extends IceCandidate implements Serializable {
    public SerializableIceCandidate(String sdpMid, int sdpMLineIndex, String sdp) {
        super(sdpMid, sdpMLineIndex, sdp);
    }
}