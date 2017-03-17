/*
 *  Copyright 2014 The WebRTC Project Authors. All rights reserved.
 *
 *  Use of this source code is governed by a BSD-style license
 *  that can be found in the LICENSE file in the root of the source
 *  tree. An additional intellectual property rights grant can be found
 *  in the file PATENTS.  All contributing project authors may
 *  be found in the AUTHORS file in the root of the source tree.
 */

package org.appspot.apprtc;

import org.appspot.apprtc.RoomParametersFetcher.RoomParametersFetcherEvents;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketChannelEvents;
import org.appspot.apprtc.WebSocketChannelClient.WebSocketConnectionState;
import org.appspot.apprtc.service.WebsocketService;
import org.appspot.apprtc.util.AsyncHttpURLConnection;
import org.appspot.apprtc.util.AsyncHttpURLConnection.AsyncHttpEvents;

import android.graphics.Bitmap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Negotiates signaling for chatting with https://appr.tc "rooms".
 * Uses the client<->server specifics of the apprtc AppEngine webapp.
 *
 * <p>To use: create an instance of this object (registering a message handler) and
 * call connectToRoom().  Once room connection is established
 * onConnectedToRoom() callback with room parameters is invoked.
 * Messages to other party (with local Ice candidates and answer SDP) can
 * be sent after WebSocket connection is established.
 */
public class WebSocketRTCClient implements AppRTCClient, WebSocketChannelEvents {
  private static final String TAG = "WSRTCClient";
  private static final String ROOM_JOIN = "join";
  private static final String ROOM_MESSAGE = "message";
  private static final String ROOM_LEAVE = "leave";
  private static final String COMBO_VERSION = "2";
  private String mRoomName = "";

  private enum ConnectionState { NEW, CONNECTED, CLOSED, ERROR }

  private enum MessageType { MESSAGE, LEAVE }

  private final Handler handler;
  private boolean initiator;
  private boolean loopback = false;
  private SignalingEvents events;
  private WebSocketChannelClient wsClient;
  private ConnectionState roomState;
  private RoomConnectionParameters connectionParameters;
  private String messageUrl;
  private String leaveUrl;

  // Spreedbox
  String mId = "";
  String mSid;
  String mIdFrom;

  public WebSocketRTCClient(SignalingEvents events) {
    this.events = events;
    roomState = ConnectionState.NEW;
    final HandlerThread handlerThread = new HandlerThread(TAG);
    handlerThread.start();
    handler = new Handler(handlerThread.getLooper());
  }


  // --------------------------------------------------------------------
  // AppRTCClient interface implementation.
  // Asynchronously connect to an AppRTC room URL using supplied connection
  // parameters, retrieves room parameters and connect to WebSocket server.
  @Override
  public void connectToServer(final String address) {
    this.connectionParameters = connectionParameters;
    handler.post(new Runnable() {
      @Override
      public void run() {
        connectToServerInternal(address);
      }
    });
  }

  // --------------------------------------------------------------------
  // AppRTCClient interface implementation.
  // Asynchronously connect to an AppRTC room URL using supplied connection
  // parameters, retrieves room parameters and connect to WebSocket server.
  @Override
  public void connectToRoom(final String roomName) {
    ;
    handler.post(new Runnable() {
      @Override
      public void run() {
        connectToRoomInternal(roomName);
      }
    });
  }

  @Override
  public void disconnectFromRoom() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        disconnectFromRoomInternal();
        handler.getLooper().quit();
      }
    });
  }

  @Override
  public void sendLeave() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        LeaveRoomInternal();
        handler.getLooper().quit();
      }
    });
  }

    @Override
    public void sendBye(final String to) {
        handler.post(new Runnable() {
            @Override
            public void run() {

                JSONObject jsonByeWrap = new JSONObject();
                jsonPut(jsonByeWrap, "Type", "Bye");

                JSONObject json = new JSONObject();

                JSONObject jsonBye = new JSONObject();
                jsonPut(jsonBye, "To", to);
                jsonPut(jsonBye, "Type", "Bye");
                jsonPut(jsonBye, "Bye", json);

                jsonPut(jsonByeWrap, "Bye", jsonBye);
                jsonPut(jsonBye, "Type", "Bye");

                wsClient.send(jsonByeWrap.toString());



            }
        });
    }

  @Override
  public void sendPostMessage(String username, String password, String url) {
    String message = "Basic " + Base64.encode(new String(username + ":" + password).getBytes(), Base64.NO_WRAP).toString();

    AsyncHttpURLConnection httpConnection =
            new AsyncHttpURLConnection("GET", url, "", new AsyncHttpEvents() {
              @Override
              public void onHttpError(String errorMessage) {
                reportError("GAE POST error: " + errorMessage);
              }

              @Override
              public void onHttpComplete(String response) {
                events.onPostResponse(response);
              }

              @Override
              public void onHttpComplete(Bitmap response) {

              }
            });

    httpConnection.setContentType("application/x-www-form-urlencoded");
    httpConnection.setAuthorization(message);
    httpConnection.send();
  }

  // Connects to server - function runs on a local looper thread.
  private void connectToServerInternal(String address) {
    wsClient = new WebSocketChannelClient(handler, this);

    wsClient.connect("wss://" + address + "/webrtc/ws", "wss://" + address + "/webrtc/ws");

    getWebrtcConfig("https://" + address + "/webrtc/api/v1/config");
  }

  private void getWebrtcConfig(String url) {

    AsyncHttpURLConnection httpConnection =
            new AsyncHttpURLConnection("GET", url, "", new AsyncHttpEvents() {
              @Override
              public void onHttpError(String errorMessage) {
                reportError("getWebrtc error: " + errorMessage);
              }

              @Override
              public void onHttpComplete(String response) {
                events.onConfigResponse(response);
              }

              @Override
              public void onHttpComplete(Bitmap response) {

              }
            });

    httpConnection.setContentType("application/x-www-form-urlencoded");
    httpConnection.send();
  }

  // Connects to room - function runs on a local looper thread.
  private void connectToRoomInternal(final String roomName) {
    handler.post(new Runnable() {
      @Override
      public void run() {

        JSONObject json = new JSONObject();
        jsonPut(json, "Name", roomName);
        jsonPut(json, "Id", roomName); // obsolete may not be needed
        jsonPut(json, "Version", "1.0.0");
        jsonPut(json, "Ua", "Spreedbox Android 1.0");
        jsonPut(json, "Type", "");
        jsonPut(json, "Iid", mId);

        JSONObject jsonRoom = new JSONObject();
        jsonPut(jsonRoom, "Hello", json);
        jsonPut(jsonRoom, "Type", "Hello");

        wsClient.send(jsonRoom.toString());



      }
    });
  }

  // Connects to room - function runs on a local looper thread.
  private void LeaveRoomInternal() {
    handler.post(new Runnable() {
      @Override
      public void run() {

        JSONObject json = new JSONObject();

        JSONObject jsonLeave = new JSONObject();
        jsonPut(jsonLeave, "Leave", json);
        jsonPut(jsonLeave, "Type", "Leave");

        wsClient.send(jsonLeave.toString());
      }
    });
  }

  // Disconnect from room and send bye messages - runs on a local looper thread.
  private void disconnectFromRoomInternal() {
    Log.d(TAG, "Disconnect. Room state: " + roomState);
    if (roomState == ConnectionState.CONNECTED) {
      Log.d(TAG, "Closing room.");
      sendPostMessage(MessageType.LEAVE, leaveUrl, null);
    }
    roomState = ConnectionState.CLOSED;
    if (wsClient != null) {
      wsClient.disconnect(true);
    }
  }

  // Helper functions to get connection, post message and leave message URLs
  private String getConnectionUrl(RoomConnectionParameters connectionParameters) {
    return connectionParameters.roomUrl + "/api/v1/rooms";
  }

  private String getMessageUrl(
      RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
    return "192.168.0.211/webrtc/ws" + "/";
  }

  private String getLeaveUrl(
      RoomConnectionParameters connectionParameters, SignalingParameters signalingParameters) {
    return "192.168.0.211/webrtc/ws" + "/";
  }

  // Callback issued when room parameters are extracted. Runs on local
  // looper thread.
  private void signalingParametersReady(final SignalingParameters signalingParameters) {
    Log.d(TAG, "Room connection completed.");
    if (loopback
        && (!signalingParameters.initiator || signalingParameters.offerSdp != null)) {
      reportError("Loopback room is busy.");
      return;
    }
    if (!loopback && !signalingParameters.initiator
        && signalingParameters.offerSdp == null) {
      Log.w(TAG, "No offer SDP in room response.");
    }
    initiator = signalingParameters.initiator;
    messageUrl = getMessageUrl(connectionParameters, signalingParameters);
    leaveUrl = getLeaveUrl(connectionParameters, signalingParameters);
    Log.d(TAG, "Message URL: " + messageUrl);
    Log.d(TAG, "Leave URL: " + leaveUrl);
    roomState = ConnectionState.CONNECTED;

    // Fire connection and signaling parameters events.
    events.onConnectedToRoom("");

    // Connect and register WebSocket client.
    wsClient.connect(signalingParameters.wssUrl, signalingParameters.wssPostUrl);
    wsClient.register(connectionParameters.roomId, signalingParameters.clientId);
  }

  // Send local offer SDP to the other participant.
  @Override
  public void sendOfferSdp(final SessionDescription sdp, final String to) {
    handler.post(new Runnable() {
      @Override
      public void run() {

          JSONObject jsonOfferWrap = new JSONObject();
          jsonPut(jsonOfferWrap, "Type", "Offer");

          JSONObject json = new JSONObject();
          jsonPut(json, "sdp", sdp.description);
          jsonPut(json, "type", "offer");

          JSONObject jsonOffer = new JSONObject();
          jsonPut(jsonOffer, "To", to);
          jsonPut(jsonOffer, "Type", "Offer");
          jsonPut(jsonOffer, "Offer", json);

          jsonPut(jsonOfferWrap, "Offer", jsonOffer);
          jsonPut(jsonOffer, "Type", "Offer");

          wsClient.send(jsonOfferWrap.toString());



      }
    });
  }

  // Send local answer SDP to the other participant.
  @Override
  public void sendAnswerSdp(final SessionDescription sdp, final String to) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        if (loopback) {
          Log.e(TAG, "Sending answer in loopback mode.");
          return;
        }
        JSONObject jsonAnswerWrap = new JSONObject();
        jsonPut(jsonAnswerWrap, "Type", "Answer");

        JSONObject json = new JSONObject();
        jsonPut(json, "sdp", sdp.description);
        jsonPut(json, "type", "answer");

        JSONObject jsonAnswer = new JSONObject();
        jsonPut(jsonAnswer, "To", to);
        jsonPut(jsonAnswer, "Type", "Answer");
        jsonPut(jsonAnswer, "Answer", json);

        jsonPut(jsonAnswerWrap, "Answer", jsonAnswer);
        jsonPut(jsonAnswer, "Type", "Answer");

        wsClient.send(jsonAnswerWrap.toString());
      }
    });
  }

  // Send Authentication.
  @Override
  public void sendAuthentication(final String userid, final String nonce) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        JSONObject jsonAuthenticationWrap = new JSONObject();
        jsonPut(jsonAuthenticationWrap, "Type", "Authentication");

        JSONObject json = new JSONObject();
        jsonPut(json, "Userid", userid);
        jsonPut(json, "Nonce", nonce);

        JSONObject jsonAuthentication = new JSONObject();
        jsonPut(jsonAuthentication, "Type", "Authentication");
        jsonPut(jsonAuthentication, "Authentication", json);

        jsonPut(jsonAuthenticationWrap, "Authentication", jsonAuthentication);
        jsonPut(jsonAuthentication, "Type", "Authentication");

        wsClient.send(jsonAuthenticationWrap.toString());
      }
    });
  }

  // Send Status.
  @Override
  public void sendStatus(final String displayName, final String buddyPicture) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        JSONObject jsonStatusWrap = new JSONObject();
        jsonPut(jsonStatusWrap, "Type", "Status");

        JSONObject json = new JSONObject();
        jsonPut(json, "displayName", displayName);
        jsonPut(json, "buddyPicture", "data:image/jpeg;base64," + buddyPicture);

        JSONObject jsonStatus = new JSONObject();
        jsonPut(jsonStatus, "Type", "Status");
        jsonPut(jsonStatus, "Status", json);

        jsonPut(jsonStatusWrap, "Status", jsonStatus);
        jsonPut(jsonStatus, "Type", "Status");

        wsClient.send(jsonStatusWrap.toString());
      }
    });
  }

  // Send Ice candidate to the other participant.
  @Override
  public void sendLocalIceCandidate(final SerializableIceCandidate candidate, final String to) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        JSONObject jsonCandidateWrap = new JSONObject();
        jsonPut(jsonCandidateWrap, "Type", "Candidate");


        JSONObject json = new JSONObject();
        jsonPut(json, "type", "candidate");
        jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
        jsonPut(json, "sdpMid", candidate.sdpMid);
        jsonPut(json, "candidate", candidate.sdp);

        JSONObject jsonCandidate = new JSONObject();
        jsonPut(jsonCandidate, "To", to);
        jsonPut(jsonCandidate, "Type", "Candidate");
        jsonPut(jsonCandidate, "Candidate", json);

        jsonPut(jsonCandidateWrap, "Candidate", jsonCandidate);
        jsonPut(jsonCandidate, "Type", "Candidate");


        // Call  sends ice candidates to websocket server.
        wsClient.send(jsonCandidateWrap.toString());

      }
    });
  }

  // Send Ice candidate to the other participant.
  @Override
  public void sendChatMessage(final String message, final String to) {
    handler.post(new Runnable() {
      @Override
      public void run() {

        SecureRandom random = new SecureRandom();
        byte mid[] = new byte[20];
        random.nextBytes(mid);

        JSONObject jsonChatWrap = new JSONObject();
        jsonPut(jsonChatWrap, "Type", "Chat");

        JSONObject json = new JSONObject();

        jsonPut(json, "Message", message);
        jsonPut(json, "NoEcho", true);

        JSONObject jsonChat = new JSONObject();
        if (to.length() != 0) {
          jsonPut(jsonChat, "To", to);
        }
        jsonPut(jsonChat, "Type", "Chat");
        jsonPut(jsonChat, "Chat", json);

        jsonPut(jsonChatWrap, "Chat", jsonChat);


        // Call  sends ice candidates to websocket server.
        wsClient.send(jsonChatWrap.toString());

      }
    });
  }

  // Send removed Ice candidates to the other participant.
  @Override
  public void sendLocalIceCandidateRemovals(final SerializableIceCandidate[] candidates, String to) {
    handler.post(new Runnable() {
      @Override
      public void run() {
        JSONObject json = new JSONObject();
        jsonPut(json, "type", "remove-candidates");
        JSONArray jsonArray = new JSONArray();
        for (final SerializableIceCandidate candidate : candidates) {
          jsonArray.put(toJsonCandidate(candidate));
        }
        jsonPut(json, "candidates", jsonArray);
        if (initiator) {
          // Call initiator sends ice candidates to GAE server.
          if (roomState != ConnectionState.CONNECTED) {
            reportError("Sending ICE candidate removals in non connected state.");
            return;
          }
          sendPostMessage(MessageType.MESSAGE, messageUrl, json.toString());
          if (loopback) {
            events.onRemoteIceCandidatesRemoved(candidates);
          }
        } else {
          // Call receiver sends ice candidates to websocket server.
          wsClient.send(json.toString());
        }
      }
    });
  }

  // --------------------------------------------------------------------
  // WebSocketChannelEvents interface implementation.
  // All events are called by WebSocketChannelClient on a local looper thread
  // (passed to WebSocket client constructor).
  @Override
  public void onWebSocketMessage(final String msg) {
    if (wsClient.getState() != WebSocketConnectionState.REGISTERED) {

      events.onChannelOpen();

      JSONObject json = null;
      try {
        json = new JSONObject(msg);
        String dataText = json.getString("Data");
        if (dataText.length() != 0) {
          json = new JSONObject(dataText);

          String typeText = json.getString("Type");
          if (typeText.equals("Self")) {
            String id = json.getString("Id");
            String oldId = mId;
            mId = id;
            String sid = json.getString("Sid");
            mSid = sid;
            JSONObject hello;

            if (oldId.length() != 0) {
              hello = new JSONObject();
              jsonPut(json, "Name", "");
              jsonPut(json, "Version", "1.0.0");
              jsonPut(json, "Ua", "Spreedbox Android 1.0");
              jsonPut(json, "Type", "");
              jsonPut(json, "Iid", mId);

              JSONObject jsonRoom = new JSONObject();
              jsonPut(jsonRoom, "Hello", json);
              jsonPut(jsonRoom, "Type", "Hello");

            }
            else {
              hello = new JSONObject("{ Type: \"Hello\", Hello: {\"Version\": \"1.0.0\", \"Ua\": \"Spreedbox Android 1.0\", \"Name\": \"\", \"Type\": \"\" } }");
            }

            wsClient.send(hello.toString());
            wsClient.setState(WebSocketConnectionState.REGISTERED);
          }
        }
      } catch (JSONException e) {
        e.printStackTrace();
      }
      return;
    }


    try {
      JSONObject json = new JSONObject(msg);
      String msgText = json.getString("Data");
      mIdFrom = json.optString("From");
      String errorText = json.optString("error");
      if (msgText.length() > 0) {
        json = new JSONObject(msgText);
        String type = json.optString("Type");
        if (type.equals("Welcome")) {
          String roomText = json.getString("Room");
          JSONObject roomJson = new JSONObject(roomText);
          String roomType = roomJson.optString("Type");
          String roomName = "";
          if (roomType.equals("Room")) {
            roomName = roomJson.optString("Name");
            String credentials = roomJson.optString("Credentials");
            mRoomName = roomName;
            events.onConnectedToRoom(roomName);
          }

          String usersText = json.getString("Users");
          JSONArray array = new JSONArray(usersText);
          for (int i = 0; i < array.length(); i++) {
            JSONObject usersJson = array.getJSONObject(i);
            String usersType = usersJson.optString("Type");
            if (usersType.equals("Online")) {
              Iterator<String> it = usersJson.keys();
              String Id = usersJson.optString("Id");
              String userId = usersJson.optString("Userid");
              String status = usersJson.optString("Status");
              if (status.length() != 0) {
                JSONObject statusJson = new JSONObject(status);
                String buddyPicture = statusJson.optString("buddyPicture");
                String displayName = statusJson.optString("displayName");

                if (!mId.equals(Id)) {
                  User user = new User(userId, buddyPicture, displayName, Id);
                  events.onUserEnteredRoom(user, roomName);
                }
              }

          }
          }


        }
        else if (type.equals("Joined")) {
          //{"Type":"Joined","Id":"bktktUup1ReVkLdrrN7cw4iev1ewPKbD4XTZNh5MEFN8PT1nRER5UTFQZ3RtR3FSLUpxZHVFSTU4dXJDbFlsbzBfZGU2ajhBSmdwR2k4d0lRSGQ2SE11QUkxY0c3MmhjZHp2SGJlRWYxfDQ1MDAyNzc4NDE=","Userid":"admin","Ua":"Chrome 56","Prio":100,"Status":{"buddyPicture":"img:NfDqvZdFAyD9EhgwL6vq\/sZjrk6Kw3NIU\/picture.png","displayName":"admin","message":null}}
          JSONObject usersJson = new JSONObject(msgText);
          String usersType = usersJson.optString("Type");
          if (usersType.equals("Joined")) {
            Iterator<String> it = usersJson.keys();
            String Id = usersJson.optString("Id");
            String userId = usersJson.optString("Userid");
            String status = usersJson.optString("Status");
            String buddyPicture = "";
            String displayName = "";

            if (status.length() != 0) {
              JSONObject statusJson = new JSONObject(status);
              buddyPicture = statusJson.optString("buddyPicture");
              displayName = statusJson.optString("displayName");
            }

            if (!mId.equals(Id)) {
              User user = new User(userId, buddyPicture, displayName, Id);
              events.onUserEnteredRoom(user, mRoomName);
            }
          }
        }
        else if (type.equals("Left")) {

          JSONObject usersJson = new JSONObject(msgText);
          String usersType = usersJson.optString("Type");
          if (usersType.equals("Left")) {
            Iterator<String> it = usersJson.keys();
            String Id = usersJson.optString("Id");

            if (!mId.equals(Id)) {
              User user = new User("", "", "", Id);
              events.onUserLeftRoom(user, mRoomName);
            }
          }
        }
        else if (type.equals("Candidate")) {
          String candidateTxt = json.optString("Candidate");
          JSONObject candidateJson = new JSONObject(candidateTxt);
          events.onRemoteIceCandidate(toJavaCandidate(candidateJson));
        } else if (type.equals("remove-candidates")) {
          JSONArray candidateArray = json.getJSONArray("candidates");
          SerializableIceCandidate[] candidates = new SerializableIceCandidate[candidateArray.length()];
          for (int i = 0; i < candidateArray.length(); ++i) {
            candidates[i] = toJavaCandidate(candidateArray.getJSONObject(i));
          }
          events.onRemoteIceCandidatesRemoved(candidates);
        } else if (type.equals("Answer")) {
          String answerTxt = json.optString("Answer");
          JSONObject answerJson = new JSONObject(answerTxt);
            SerializableSessionDescription sdp = new SerializableSessionDescription(
                    SerializableSessionDescription.Type.fromCanonicalForm(type), answerJson.getString("sdp"), mIdFrom);
            events.onRemoteDescription(sdp, mIdFrom, mRoomName);

        } else if (type.equals("Offer")) {
          if (!initiator) {
            String offerTxt = json.optString("Offer");
            JSONObject offerJson = new JSONObject(offerTxt);
            SerializableSessionDescription sdp = new SerializableSessionDescription(
                    SerializableSessionDescription.Type.fromCanonicalForm(type), offerJson.getString("sdp"), mIdFrom);
            events.onRemoteDescription(sdp, mIdFrom, mRoomName);
          } else {
            reportError("Received offer for call receiver: " + msg);
          }
        } else if (type.equals("Bye")) {
          String byeTxt = json.optString("Bye");
          JSONObject byeJson = new JSONObject(byeTxt);
          events.onBye(byeJson.optString("Reason"), mIdFrom, mRoomName);
        } else if (type.equals("Chat")) {
          String chatTxt = json.optString("Chat");
          JSONObject chatJson = new JSONObject(chatTxt);
          String message = chatJson.optString("Message");
          String time = chatJson.optString("Time");
          String status = "";
          String statusTxt = chatJson.optString("Status");
          if (statusTxt != null && !statusTxt.equals("null") && statusTxt.length() > 0) {
            JSONObject jsonStatus = new JSONObject(statusTxt);
            if (jsonStatus.has("Typing")) {
              status = " is Typing";
            }
          }
          events.onChatMessage(message, time, status, mIdFrom, mRoomName);
        }
      } else {
        if (errorText != null && errorText.length() > 0) {
          reportError("WebSocket error message: " + errorText);
        } else {
          reportError("Unexpected WebSocket message: " + msg);
        }
      }
    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  @Override
  public void onWebSocketClose() {
    events.onChannelClose();
  }

  @Override
  public void onWebSocketError(String description) {
    reportError("WebSocket error: " + description);
  }

  // --------------------------------------------------------------------
  // Helper functions.
  private void reportError(final String errorMessage) {
    Log.e(TAG, errorMessage);
    handler.post(new Runnable() {
      @Override
      public void run() {
        if (roomState != ConnectionState.ERROR) {
          roomState = ConnectionState.ERROR;
          events.onChannelError(errorMessage);
        }
      }
    });
  }

  // Put a |key|->|value| mapping in |json|.
  private static void jsonPut(JSONObject json, String key, Object value) {
    try {
      json.put(key, value);
    } catch (JSONException e) {
      throw new RuntimeException(e);
    }
  }

  // Send SDP or ICE candidate to a room server.
  private void sendPostMessage(
      final MessageType messageType, final String url, final String message) {
    String logInfo = url;
    if (message != null) {
      logInfo += ". Message: " + message;
    }
    Log.d(TAG, "C->GAE: " + logInfo);
    AsyncHttpURLConnection httpConnection =
        new AsyncHttpURLConnection("POST", url, message, new AsyncHttpEvents() {
          @Override
          public void onHttpError(String errorMessage) {
            reportError("GAE POST error: " + errorMessage);
          }

          @Override
          public void onHttpComplete(String response) {
            if (messageType == MessageType.MESSAGE) {
              try {
                JSONObject roomJson = new JSONObject(response);
                String result = roomJson.getString("result");
                if (!result.equals("SUCCESS")) {
                  reportError("GAE POST error: " + result);
                }
              } catch (JSONException e) {
                reportError("GAE POST JSON error: " + e.toString());
              }
            }
          }

          @Override
          public void onHttpComplete(Bitmap response) {

          }
        });
    httpConnection.send();
  }

  public static String encode(String key, String data) throws Exception {
    Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
    SecretKeySpec secret_key = new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256");
    sha256_HMAC.init(secret_key);

    return Base64.encodeToString(sha256_HMAC.doFinal(data.getBytes("UTF-8")), Base64.NO_WRAP);
  }

  @Override
   public void sendPatchMessage(
           String username, String password, final String url) {
    long unixTime = (System.currentTimeMillis() / 1000L) + (60 * 60);
    String useridcombo = unixTime + ":" + username;
    JSONObject json = new JSONObject();
    try {
      json.put("id", mId);
      json.put("sid", mSid);
      json.put("useridcombo", useridcombo);
      json.put("secret", encode("4f581d554a691d8c082c686af706010b9e2d26306d595c04803304af69e838d8", useridcombo));
    } catch (JSONException e) {
      e.printStackTrace();
    } catch (Exception e) {
      e.printStackTrace();
    }

    String requestURL = url + mId + "/";

    AsyncHttpURLConnection httpConnection =
            new AsyncHttpURLConnection("PATCH", requestURL, json.toString(), new AsyncHttpEvents() {
              @Override
              public void onHttpError(String errorMessage) {
                reportError("PATCH error: " + errorMessage);
              }

              @Override
              public void onHttpComplete(String response) {
                events.onPatchResponse(response);
              }

              @Override
              public void onHttpComplete(Bitmap response) {

              }
            });
    httpConnection.setContentType("application/json");
    httpConnection.send();
  }

  // Converts a Java candidate to a JSONObject.
  private JSONObject toJsonCandidate(final SerializableIceCandidate candidate) {
    JSONObject json = new JSONObject();
    jsonPut(json, "sdpMLineIndex", candidate.sdpMLineIndex);
    jsonPut(json, "sdpMid", candidate.sdpMid);
    jsonPut(json, "candidate", candidate.sdp);
    return json;
  }

  // Converts a JSON candidate to a Java object.
  SerializableIceCandidate toJavaCandidate(JSONObject json) throws JSONException {
    return new SerializableIceCandidate(
        json.getString("sdpMid"), json.getInt("sdpMLineIndex"), json.getString("candidate"));
  }
}
