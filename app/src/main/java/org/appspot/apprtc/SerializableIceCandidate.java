package org.appspot.apprtc;

import android.os.Parcel;
import android.os.Parcelable;

import org.webrtc.IceCandidate;

import java.io.Serializable;

public class SerializableIceCandidate implements Parcelable {

    public String sdpMid;
    public int sdpMLineIndex;
    public String sdp;
    public String from;

    public SerializableIceCandidate() {
        this.sdpMid = "";
        this.sdpMLineIndex = 0;
        this.sdp = "";
        this.from = "";
    }

    public SerializableIceCandidate(String sdpMid, int sdpMLineIndex, String sdp) {
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdp = sdp;
    }

    public SerializableIceCandidate(String sdpMid, int sdpMLineIndex, String sdp, String from) {
        this.sdpMid = sdpMid;
        this.sdpMLineIndex = sdpMLineIndex;
        this.sdp = sdp;
        this.from = from;
    }

    public String toString() {
        return this.sdpMid + ":" + this.sdpMLineIndex + ":" + this.sdp;
    }

        /* everything below here is for implementing Parcelable */

    // 99.9% of the time you can just ignore this
    @Override
    public int describeContents() {
        return 0;
    }

    // write your object's data to the passed-in Parcel
    @Override
    public void writeToParcel(Parcel out, int flags) {
        out.writeString(sdpMid);
        out.writeInt(sdpMLineIndex);
        out.writeString(sdp);
        out.writeString(from);
    }

    // this is used to regenerate your object. All Parcelables must have a CREATOR that implements these two methods
    public static final Parcelable.Creator<SerializableIceCandidate> CREATOR = new Parcelable.Creator<SerializableIceCandidate>() {
        public SerializableIceCandidate createFromParcel(Parcel in) {
            return new SerializableIceCandidate(in);
        }

        public SerializableIceCandidate[] newArray(int size) {
            return new SerializableIceCandidate[size];
        }
    };

    // example constructor that takes a Parcel and gives you an object populated with it's values
    private SerializableIceCandidate(Parcel in) {
        sdpMid = in.readString();
        sdpMLineIndex = in.readInt();
        sdp = in.readString();
        from = in.readString();
    }
}