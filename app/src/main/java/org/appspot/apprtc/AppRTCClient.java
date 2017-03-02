/*
 *  Copyright 2013 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import org.appspot.apprtc.service.WebsocketService;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

/**
 * AppRTCClient is the interface representing an AppRTC client.
 */
public interface AppRTCClient {


  /**
   * Struct holding the connection parameters of an AppRTC room.
   */
  class RoomConnectionParameters {
    public final String roomUrl;
    public final String roomId;
    public final boolean loopback;
    public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback) {
      this.roomUrl = roomUrl;
      this.roomId = roomId;
      this.loopback = loopback;
    }
  }

  void connectToServer(final String address);

  /**
   * Asynchronously connect to an AppRTC room URL using supplied connection
   * parameters. Once connection is established onConnectedToRoom()
   * callback with room parameters is invoked.
   */
  void connectToRoom(String roomName);

  /**
   * Send offer SDP to the other participant.
   */
  void sendOfferSdp(final SessionDescription sdp, String to);

  /**
   * Send answer SDP to the other participant.
   */
  void sendAnswerSdp(final SessionDescription sdp, String to);

  /**
   * Send Ice candidate to the other participant.
   */
  void sendLocalIceCandidate(final SerializableIceCandidate candidate, String to);

  /**
   * Send removed ICE candidates to the other participant.
   */
  void sendLocalIceCandidateRemovals(final SerializableIceCandidate[] candidates, String to);

  /**
   * Disconnect from room.
   */
  void disconnectFromRoom();

  void sendBye(String to);

  void sendPostMessage(String username, String password, final String url);

  void sendPatchMessage(String username, String password, final String url);

  void sendAuthentication(String userid, String nonce);

  void sendStatus(String displayName, String buddyPicture);

  /**
   * Struct holding the signaling parameters of an AppRTC room.
   */
  class SignalingParameters {
    public final List<PeerConnection.IceServer> iceServers;
    public final boolean initiator;
    public final String clientId;
    public final String wssUrl;
    public final String wssPostUrl;
    public SessionDescription offerSdp;
    public final List<IceCandidate> iceCandidates;

    public SignalingParameters(List<PeerConnection.IceServer> iceServers, boolean initiator,
        String clientId, String wssUrl, String wssPostUrl, SessionDescription offerSdp,
        List<IceCandidate> iceCandidates) {
      this.iceServers = iceServers;
      this.initiator = initiator;
      this.clientId = clientId;
      this.wssUrl = wssUrl;
      this.wssPostUrl = wssPostUrl;
      this.offerSdp = offerSdp;
      this.iceCandidates = iceCandidates;
    }
  }

  /**
   * Callback interface for messages delivered on signaling channel.
   *
   * <p>Methods are guaranteed to be invoked on the UI thread of |activity|.
   */
  interface SignalingEvents {
    /**
     * Callback fired once the room's signaling parameters
     * SignalingParameters are extracted.
     */
    void onConnectedToRoom(final String roomName);

    /**
     * Callback fired once remote SDP is received.
     */
    void onRemoteDescription(final SerializableSessionDescription sdp);

    /**
     * Callback fired once remote Ice candidate is received.
     */
    void onRemoteIceCandidate(final SerializableIceCandidate candidate);

    /**
     * Callback fired once remote Ice candidate removals are received.
     */
    void onRemoteIceCandidatesRemoved(final SerializableIceCandidate[] candidates);

    /**
     * Callback fired once channel is closed.
     */
    void onChannelOpen();

    /**
     * Callback fired once channel is closed.
     */
    void onChannelClose();

    /**
     * Callback fired once channel error happened.
     */
    void onChannelError(final String description);

    void onUserEnteredRoom(User user, String room);

    void onUserLeftRoom(User user, String room);

    void onBye(final String reason);

    void sendBye(String mPeerId);


    void onPatchResponse(String response);

    void onPostResponse(String response);

    void onConfigResponse(String response);

    void onChatMessage(String message, String time, String status, String fromId, String roomName);
  }
}
