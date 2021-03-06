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

import java.util.ArrayList;
import java.util.List;

/**
 * AppRTCClient is the interface representing an AppRTC client.
 */
public interface AppRTCClient {

  String getRoomName();

    String getId();

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


  void sendSelf();

  void connectToServer(final String address, String roomName);

  /**
   * Asynchronously connect to an AppRTC room URL using supplied connection
   * parameters. Once connection is established onConnectedToRoom()
   * callback with room parameters is invoked.
   */
  void connectToRoom(String roomName);

  void connectToRoom(String roomName, String pin);

  void unlockRoom(String roomName);

  void lockRoom(String roomName, String pin);

  /**
   * Send offer SDP to the other participant.
   */
  void sendOfferSdp(final SessionDescription sdp, String to);

  void sendTokenOffer(final SessionDescription sdp, final String token, final String id, final String to);

  void sendConferenceOffer(final SessionDescription sdp, String to, String conferenceId);
  /**
   * Send answer SDP to the other participant.
   */
  void sendAnswerSdp(final SessionDescription sdp, String to);

  void sendTokenAnswer(final SessionDescription sdp, final String token, final String id, final String to);

  void sendConference(final String conferenceId, final ArrayList<String> userIds);

  /**
   * Send Ice candidate to the other participant.
   */
  void sendLocalIceCandidate(final SerializableIceCandidate candidate, String token, String id, String to);

  /**
   * Send removed ICE candidates to the other participant.
   */
  void sendLocalIceCandidateRemovals(final SerializableIceCandidate[] candidates, String to);

  /**
   * Disconnect from room.
   */
  void disconnectFromRoom();

  void sendLeave();

  void sendBye(String to);

  void sendBye(String to, String reason);

  void sendPostMessage(String username, String password, final String url);

  void sendPatchMessage(String username, String password, final String url);

  void sendAuthentication(String userid, String nonce);

  void sendStatus(String displayName, String buddyPicture, String message);

  void sendChatMessage(String message, String to);

  void sendFileMessage(final String message, final long size, final String name, final String mime, final String to);

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
    public String token;
    public boolean dataonly;
    public boolean screenshare;

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
    void onRemoteDescription(final SerializableSessionDescription sdp, String token, String id, String conferenceId, String fromId, String roomName, String type);

    /**
     * Callback fired once remote Ice candidate is received.
     */
    void onRemoteIceCandidate(final SerializableIceCandidate candidate, String id, String token);

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

    void clearRoomUsers(String room);

    void onUserEnteredRoom(User user, String room);

    void onUserLeftRoom(User user, String room);

    void onBye(final String reason, String fromId, String roomName);

    void sendBye(String mPeerId);

    void sendBye(String mPeerId, String reason);


    void onPatchResponse(String response);

    void onPostResponse(String response);

    void onConfigResponse(String response);

    void onChatMessage(String message, String time, String status, String to, String fromId, String roomName);

    void onFileMessage(String time, String id, String chunks, String name, String size, String filetype, String mIdFrom, String mRoomName);

    void onAddTurnServer(String url, String username, String password);

    void onAddStunServer(String url, String username, String password);

    void onSelf();

    void onIdChanged(String id, String sid);

    void onTurnTtl(int ttl);

      void onConferenceUser(String roomName, String conferenceId, String id);

      void onError(String code, String message, String roomName);

      void onScreenShare(String userId, String id, String roomName);
  }
}
