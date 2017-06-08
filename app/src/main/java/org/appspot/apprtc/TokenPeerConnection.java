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

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Checksum;

/**
 * Created by petere on 3/27/2017.
 */

public class TokenPeerConnection implements PeerConnectionClient.PeerConnectionEvents, PeerConnectionClient.DataChannelCallback{

    private static final String TAG = "TokenPeerConnection";
    private static final int BINARY_HEADER_SIZE = 12;
    public static final long CHUNK_SIZE = 30000;
    private final Context mContext;
    private String mToken;
    private String mRemoteId;
    private String mConnectionId;
    private FileInfo fileInfo;
    private PeerConnectionClient peerConnectionClient = null;
    private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    private PeerConnectionClient.DataChannelParameters dataChannelParameters;
    private List<PeerConnection.IceServer> mIceServers;
    private SessionDescription mLocalSdp;
    private AppRTCClient.SignalingParameters signalingParameters;
    private boolean initiator;
    private HashMap<String, SerializableIceCandidate> mIceCandidates = new HashMap<String, SerializableIceCandidate>();
    private HashMap<String, IceCandidate> mRemoteIceCandidates = new HashMap<String, IceCandidate>();
    private InputStream inputStream;
    private FileOutputStream mDownloadStream;
    private int mChunkIndex = 0;
    private long mDownloadedBytes;
    private int mDownloadIndex;
    private long totalSize = 0;
    private boolean mLocalSdpSent;
    private SessionDescription mRemoteSdp;

    enum ConnectionState {
        IDLE,
        PEERCONNECTED,
        PEERDISCONNECTED,
        ICECONNECTED,
        ICEDISCONNECTED
    }

    interface TokenPeerConnectionEvents {
        void onDownloadedBytes(int index, long bytes, String to);
        void onDownloadComplete(int mDownloadIndex, String to);

        void TokenOfferSdp(SessionDescription localSdp, String token, String connectionId, String remoteId);

        void sendTokenAnswerSdp(SessionDescription localSdp, String token, String connectionId, String remoteId);

        void sendLocalIceCandidate(SerializableIceCandidate candidate, String token, String connectionId, String remoteId);

        void onDownloadPath(int index, String path, String to);

        void onError(String description, int index, String to);
    }

    TokenPeerConnectionEvents events;
    ConnectionState mConnectionState = ConnectionState.IDLE;

    TokenPeerConnection(Context context, TokenPeerConnectionEvents events, boolean initiator, String token, String remoteId, String connectionId, List<PeerConnection.IceServer> iceServers, int downloadIndex) {
        mToken = token;
        mRemoteId = remoteId;
        mConnectionId = connectionId;
        mContext = context;
        mIceServers = iceServers;
        this.initiator = initiator;
        this.events = events;
        mDownloadIndex = downloadIndex;

        mLocalSdpSent = false;

        dataChannelParameters = new PeerConnectionClient.DataChannelParameters(true,
                -1,
                -1, "",
                false, -1);
        peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(false, false, false, 0, 0, 0, 0, "", false, false, 0, "", true, false, false, true, true, true, false, dataChannelParameters);

        setupPeerConnection();
    }

    public void setFileInfo(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

    private void setupPeerConnection() {
        Log.d(TAG, "setupPeerConnection()");
        // is there a connection open?
        if (peerConnectionClient != null) {
            peerConnectionClient.close();
            peerConnectionClient = null;
        }

        peerConnectionClient = PeerConnectionClient.getInstance();
        peerConnectionClient.createPeerConnectionFactory(
                mContext, peerConnectionParameters, this);
        peerConnectionClient.setDataChannelCallback(this);

    }

    public void close() {
        Log.d(TAG, "close()");
        if (peerConnectionClient != null) {
            JSONObject json = new JSONObject();
            try {
                json.put("m", "bye");
            } catch (JSONException e) {
                e.printStackTrace();
            }

            peerConnectionClient.sendDataChannelMessage(json.toString());
            peerConnectionClient.close();
            peerConnectionClient = null;
        }
    }

    @Override
    public void onBinaryMessage(DataChannel.Buffer buffer) {
        Log.d(TAG, "onBinaryMessage()");
        // write the stream to file
        ByteBuffer data = buffer.data;
        int length = data.capacity() - BINARY_HEADER_SIZE;
        final byte[] bytes = new byte[length];
        byte[] header = new byte[BINARY_HEADER_SIZE];
        data.get(header, 0, BINARY_HEADER_SIZE);
        data.get(bytes, 0, length);
        ByteBuffer wrapped = ByteBuffer.wrap(bytes); // big-endian by default
        wrapped.order(ByteOrder.LITTLE_ENDIAN);
        long crc32 = CalculateCRC32ChecksumForByteArray(bytes);
        char version = wrapped.getChar();
        wrapped.get(3); // skip reserved
        int sequenceNum = wrapped.getInt();
        int checksum = wrapped.getInt();

        try {
            mDownloadStream.write(bytes);
            mDownloadedBytes += length;
            events.onDownloadedBytes(mDownloadIndex, mDownloadedBytes, mRemoteId);
        } catch (IOException e) {
            e.printStackTrace();
        }

        if (mChunkIndex < Integer.valueOf(fileInfo.chunks)) {
            JSONObject json = new JSONObject();
            try {
                json.put("m", "r");
                json.put("i", mChunkIndex++);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            peerConnectionClient.sendDataChannelMessage(json.toString());
        }
        else {
            try {
                mDownloadStream.close();
                events.onDownloadComplete(mDownloadIndex, mRemoteId);

                JSONObject json = new JSONObject();
                try {
                    json.put("m", "bye");
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                peerConnectionClient.sendDataChannelMessage(json.toString());
                peerConnectionClient.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onStateChange(DataChannel.State state) {
        if (state == DataChannel.State.OPEN) {
            if (initiator) {
                mChunkIndex = 0;
                JSONObject json = new JSONObject();
                try {
                    json.put("m", "r");
                    json.put("i", mChunkIndex++);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                File sdCard = Environment.getExternalStorageDirectory();
                File dir = new File(sdCard.getAbsolutePath() + "/Download");
                dir.mkdirs();
                File file = new File(dir, fileInfo.name);

                events.onDownloadPath(mDownloadIndex, file.getAbsolutePath(), mRemoteId);

                try {
                    mDownloadStream = new FileOutputStream(file);
                    mDownloadedBytes = 0;
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }
                peerConnectionClient.sendDataChannelMessage(json.toString());
            }
        }
    }

    @Override
    public void onTextMessage(String text) {
        Log.d(TAG, "onTextMessage(" + text + ")");
        // parse json
        try {
            JSONObject json = new JSONObject(text);
            String m = json.optString("m");
            if (m.equals("r")) {
                String chunk = json.optString("i");
                // read the chunk from the file and send it
                String path = new String(Base64.decode(mToken.substring(5), Base64.DEFAULT));
                Uri uri = Uri.parse(path);
                totalSize = getContentSize(uri, mContext);
                inputStream = mContext.getContentResolver().openInputStream(uri);
                long index = Long.valueOf(chunk);
                long start = index * CHUNK_SIZE;
                inputStream.skip(start);
                long readSize = CHUNK_SIZE;
                int bytesRead = 0;
                if (start + readSize > totalSize) {
                    readSize = totalSize - start;
                }
                byte[] data = new byte[(int)readSize];
                try {
                    bytesRead = inputStream.read(data);
                    Log.d(TAG, "Read " + bytesRead + " from " + start);
                }
                catch (IndexOutOfBoundsException e) {
                    e.printStackTrace();
                }
                inputStream.close();
                long checksum = CalculateCRC32ChecksumForByteArray(data);
                ByteBuffer header = ByteBuffer.allocate(BINARY_HEADER_SIZE);
                header.order(ByteOrder.LITTLE_ENDIAN);
                header.putInt(0); // version + reserved
                header.putInt((int)index); // sequence number
                header.putInt((int)checksum); // checksum

                ByteArrayOutputStream outputStream = new ByteArrayOutputStream( );
                outputStream.write(header.array());
                outputStream.write(data);

                peerConnectionClient.sendDataChannelBinary(outputStream.toByteArray());
            }
            else if (m.equals("bye")) {
                if (inputStream != null) {
                    inputStream.close();
                    inputStream = null;
                    peerConnectionClient.close();
                    peerConnectionClient = null;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private long CalculateCRC32ChecksumForByteArray(byte[] bytes) {

        Checksum checksum = new CRC32();

        // update the current checksum with the specified array of bytes
        checksum.update(bytes, 0, bytes.length);

        // get the current checksum value
        long checksumValue = checksum.getValue();

        return checksumValue;

    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        Log.d(TAG, "onLocalDescription()");
        mLocalSdp = sdp;

        if (!mLocalSdpSent && mLocalSdp != null) {
            mLocalSdpSent = true;
            if (initiator) {

                events.TokenOfferSdp(mLocalSdp, mToken, mConnectionId, mRemoteId);
                Log.d(TAG, "TokenOfferSdp()");
            }
            else {
                events.sendTokenAnswerSdp(mLocalSdp, mToken, mConnectionId, mRemoteId);
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

                events.TokenOfferSdp(mLocalSdp, mToken, mConnectionId, mRemoteId);
                Log.d(TAG, "TokenOfferSdp()");
            }
            else {
                events.sendTokenAnswerSdp(mLocalSdp, mToken, mConnectionId, mRemoteId);
                Log.d(TAG, "sendTokenAnswerSdp()");
            }
        }

        events.sendLocalIceCandidate(candidate, mToken, mConnectionId, mRemoteId);
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
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {
        Log.d(TAG, "onPeerConnectionStatsReady()");
    }

    @Override
    public void onPeerConnectionError(String description) {
        Log.d(TAG, "onPeerConnectionError(" + description + ")");
        if (mDownloadIndex != -1) {
            events.onError(description, mDownloadIndex, mRemoteId);
        }
        mConnectionState = ConnectionState.PEERDISCONNECTED;
    }

    @Override
    public void onPeerConnectionFactoryCreated() {
        Log.d(TAG, "onPeerConnectionFactoryCreated()");
        signalingParameters = new AppRTCClient.SignalingParameters(mIceServers, false, "", "", "", null, null);
        signalingParameters.dataonly = true;
        if (peerConnectionClient != null) {
            peerConnectionClient.createPeerConnection(signalingParameters);


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
            peerConnectionClient.setRemoteDescription(mRemoteSdp, mToken);
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

    }

    @Override
    public void onVideoDisabled() {

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
                peerConnectionClient.setRemoteDescription(sd, mToken);
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
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    static public long getContentSize(Uri uri, Context context) {
        long ret = 0;
        // The query, since it only applies to a single document, will only return
        // one row. There's no need to filter, sort, or select fields, since we want
        // all fields for one document.
        Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null, null);

        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {

                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                String displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                // If the size is unknown, the value stored is null.  But since an
                // int can't be null in Java, the behavior is implementation-specific,
                // which is just a fancy term for "unpredictable".  So as
                // a rule, check if it's null before assigning to an int.  This will
                // happen often:  The storage API allows for remote files, whose
                // size might not be locally known.
                String size = null;
                if (!cursor.isNull(sizeIndex)) {
                    // Technically the column stores an int, but cursor.getString()
                    // will do the conversion automatically.
                    size = cursor.getString(sizeIndex);
                } else {
                    size = "0";
                }
                ret = Long.valueOf(size);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return ret;
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    static public String getContentName(Uri uri, Context context) {
        String ret = "";
        // The query, since it only applies to a single document, will only return
        // one row. There's no need to filter, sort, or select fields, since we want
        // all fields for one document.
        Cursor cursor = context.getContentResolver()
                .query(uri, null, null, null, null, null);

        try {
            // moveToFirst() returns false if the cursor has 0 rows.  Very handy for
            // "if there's anything to look at, look at it" conditionals.
            if (cursor != null && cursor.moveToFirst()) {

                // Note it's called "Display Name".  This is
                // provider-specific, and might not necessarily be the file name.
                String displayName = cursor.getString(
                        cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));

                ret = displayName;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return ret;
    }
}
