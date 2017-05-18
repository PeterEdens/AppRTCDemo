package org.appspot.apprtc;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.OpenableColumns;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Created by petere on 3/27/2017.
 */

public class AdditionalPeerConnection implements PeerConnectionClient.PeerConnectionEvents{

    private static final String TAG = "AdditPeerConnection";
    private final Context mContext;
    private CallActivity.RemoteConnectionViews mRemoteConnectionViews = null;
    private ImageView remoteUserImage;
    private final EglBase rootEglBase;
    private final CallActivity parent;
    private final SurfaceViewRenderer localRender;
    private final MediaStream mediaStream;
    private String mRemoteId;
    private PeerConnectionClient peerConnectionClient = null;
    private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    private PeerConnectionClient.DataChannelParameters dataChannelParameters;
    private List<PeerConnection.IceServer> mIceServers;
    private SessionDescription mLocalSdp;
    private AppRTCClient.SignalingParameters signalingParameters;
    private boolean initiator;
    private HashMap<String, SerializableIceCandidate> mIceCandidates = new HashMap<String, SerializableIceCandidate>();
    private HashMap<String, IceCandidate> mRemoteIceCandidates = new HashMap<String, IceCandidate>();
    private boolean mLocalSdpSent;
    private SessionDescription mRemoteSdp;
    private final List<VideoRenderer.Callbacks> remoteRenderers =
            new ArrayList<VideoRenderer.Callbacks>();

    private String mConferenceId;

    public CallActivity.RemoteConnectionViews getRemoteViews() {
        return mRemoteConnectionViews;
    }

    enum ConnectionState {
        IDLE,
        PEERCONNECTED,
        PEERDISCONNECTED,
        ICECONNECTED,
        ICEDISCONNECTED
    }

    interface AdditionalPeerConnectionEvents {

        void sendOfferSdp(SessionDescription localSdp, String remoteId, String conferenceId);

        void sendAnswerSdp(SessionDescription localSdp, String remoteId);

        void sendLocalIceCandidate(SerializableIceCandidate candidate, String remoteId);

        void onError(String description, String to);

        void onConnected(String remoteId);

        void onConnectionClosed(String mRemoteId);
    }

    AdditionalPeerConnectionEvents events;
    ConnectionState mConnectionState = ConnectionState.IDLE;

    AdditionalPeerConnection(CallActivity parent, Context context, AdditionalPeerConnectionEvents events, boolean initiator, String remoteId, List<PeerConnection.IceServer> iceServers, PeerConnectionClient.PeerConnectionParameters params, EglBase rootEglBase, SurfaceViewRenderer localRender, CallActivity.RemoteConnectionViews remoteConnectionViews, MediaStream mediaStream, String conferenceId) {
        mRemoteId = remoteId;
        mContext = context;
        mIceServers = iceServers;
        this.initiator = initiator;
        this.events = events;
        this.parent = parent;
        this.rootEglBase = rootEglBase;
        this.localRender = localRender;

        if (remoteConnectionViews != null) {
            mRemoteConnectionViews = remoteConnectionViews;
            this.remoteUserImage = remoteConnectionViews.getImageView();
            this.remoteRenderers.add(remoteConnectionViews.getSurfaceViewRenderer());
        }
        
        this.mediaStream = mediaStream;
        this.mConferenceId = conferenceId;

        mLocalSdpSent = false;

        dataChannelParameters = new PeerConnectionClient.DataChannelParameters(true,
                -1,
                -1, "",
                false, -1);
        peerConnectionParameters = params;

        setupPeerConnection();
    }

    private void setupPeerConnection() {
        Log.d(TAG, "setupPeerConnection()");
        // is there a connection open?
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }

        peerConnectionClient = PeerConnectionClient.getInstance();

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableNetworkMonitor = true;
        peerConnectionClient.setPeerConnectionFactoryOptions(options);

        peerConnectionClient.createPeerConnectionFactory(
                mContext, peerConnectionParameters, this);

    }

    public void setAudioEnabled(boolean micEnabled) {
        peerConnectionClient.setAudioEnabled(micEnabled);
    }

    public void setVideoEnabled(boolean videoEnabled) {
        peerConnectionClient.setVideoEnabled(videoEnabled);
    }

    public void close() {
        Log.d(TAG, "close()");
        if (peerConnectionClient != null) {
            peerConnectionClient.removeStream(mediaStream);
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        Log.d(TAG, "onLocalDescription()");
        mLocalSdp = sdp;

        if (!mLocalSdpSent && mLocalSdp != null) {
            mLocalSdpSent = true;
            if (initiator) {

                events.sendOfferSdp(mLocalSdp, mRemoteId, mConferenceId);
                Log.d(TAG, "TokenOfferSdp()");
            }
            else {
                events.sendAnswerSdp(mLocalSdp, mRemoteId);
                Log.d(TAG, "sendTokenAnswerSdp()");
            }
        }

    }

    @Override
    public void onIceCandidate(SerializableIceCandidate candidate) {
        Log.d(TAG, "onIceCandidate()");
        mIceCandidates.put(candidate.sdp, candidate);
        if (!mLocalSdpSent && mLocalSdp != null) {
            mLocalSdpSent = true;
            if (initiator) {

                events.sendOfferSdp(mLocalSdp, mRemoteId, mConferenceId);
                Log.d(TAG, "TokenOfferSdp()");
            }
            else {
                events.sendAnswerSdp(mLocalSdp, mRemoteId);
                Log.d(TAG, "sendTokenAnswerSdp()");
            }
        }

        events.sendLocalIceCandidate(candidate, mRemoteId);
        Log.d(TAG, "sendLocalIceCandidate()");

    }

    @Override
    public void onIceCandidatesRemoved(SerializableIceCandidate[] candidates) {
        Log.d(TAG, "onIceCandidatesRemoved()");
        for (SerializableIceCandidate candidate: candidates) {
            if (mIceCandidates.containsKey(candidate.sdp)) {
                mIceCandidates.remove(candidate.sdp);
            }
        }
    }

    @Override
    public void onIceConnected() {
        Log.d(TAG, "onIceConnected()");
        mConnectionState = ConnectionState.ICECONNECTED;

        events.onConnected(mRemoteId);
    }

    @Override
    public void onIceDisconnected() {
        Log.d(TAG, "onIceDisconnected()");
        mConnectionState = ConnectionState.ICEDISCONNECTED;
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.d(TAG, "onPeerConnectionClosed()");
        mConnectionState = ConnectionState.PEERDISCONNECTED;
        events.onConnectionClosed(mRemoteId);
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {
        Log.d(TAG, "onPeerConnectionStatsReady()");
    }

    @Override
    public void onPeerConnectionError(String description) {
        Log.d(TAG, "onPeerConnectionError(" + description + ")");
        events.onError(description, mRemoteId);
        mConnectionState = ConnectionState.PEERDISCONNECTED;
    }

    @Override
    public void onPeerConnectionFactoryCreated() {
        Log.d(TAG, "onPeerConnectionFactoryCreated()");
        signalingParameters = new AppRTCClient.SignalingParameters(mIceServers, false, "", "", "", null, null);
        signalingParameters.dataonly = false;
        VideoCapturer videoCapturer = null;
        if (peerConnectionParameters.videoCallEnabled) {
            //videoCapturer = parent.createVideoCapturer();
        }

        if (peerConnectionClient != null) {
            peerConnectionClient.setMediaStream(mediaStream);
            peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(), localRender,
                    remoteRenderers, videoCapturer, signalingParameters);


        }
    }

    @Override
    public void onPeerConnectionCreated() {
        Log.d(TAG, "onPeerConnectionCreated()");
        mConnectionState = ConnectionState.PEERCONNECTED;

        if (initiator) {
            peerConnectionClient.createOffer();
        }

        if (mRemoteSdp != null) {
            peerConnectionClient.setRemoteDescription(mRemoteSdp, "");
            Log.d(TAG, "peerConnectionClient.setRemoteDescription()");
            mRemoteSdp = null;
        }
    }

    @Override
    public void onRemoteSdpSet() {
        Log.d(TAG, "onRemoteSdpSet()");

        if (!initiator) {
            peerConnectionClient.createAnswer();
        }
    }

    @Override
    public void onVideoEnabled() {
        if (remoteUserImage != null) {
            remoteUserImage.setVisibility(View.GONE);
        }
    }

    @Override
    public void onVideoDisabled() {
        if (remoteUserImage != null) {
            remoteUserImage.setVisibility(View.VISIBLE);
        }
    }

    public void addRemoteIceCandidate(IceCandidate ic) {
        Log.d(TAG, "addRemoteIceCandidate()");
        if (peerConnectionClient != null) {
            peerConnectionClient.addRemoteIceCandidate(ic);
            Log.d(TAG, "peerConnectionClient.addRemoteIceCandidate()");
        }
        else {
            mRemoteIceCandidates.put(ic.sdp, ic);
            Log.d(TAG, "mRemoteIceCandidates");
        }

    }

    public void setRemoteDescription(SessionDescription sd) {
        Log.d(TAG, "setRemoteDescription()");
        if (peerConnectionClient != null) {
            if (mConnectionState == ConnectionState.PEERCONNECTED) {
                peerConnectionClient.setRemoteDescription(sd, "");
                Log.d(TAG, "peerConnectionClient.setRemoteDescription()");
            }
            else {
                mRemoteSdp = sd;
            }
        }
        else {
            Log.e(TAG, "Received remote description for null peer connection");
        }
    }

}
