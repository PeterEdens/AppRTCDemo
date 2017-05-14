
package de.tavendo.autobahn;

import android.net.SSLCertificateSocketFactory;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import org.appspot.apprtc.util.TLSSocketFactory;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.Socket;
import java.net.URI;
import java.security.GeneralSecurityException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;

import javax.net.SocketFactory;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import de.tavendo.autobahn.WebSocket;
import de.tavendo.autobahn.WebSocketException;
import de.tavendo.autobahn.WebSocketMessage;
import de.tavendo.autobahn.WebSocketOptions;
import de.tavendo.autobahn.WebSocketReader;
import de.tavendo.autobahn.WebSocketWriter;

/**
 * Created by petere on 2/15/2017.
 */

public class WebSocketConnectionCustom  extends WebSocketConnection implements WebSocket {
    private static final String TAG = WebSocketConnectionCustom.class.getName();
    private static final String WS_URI_SCHEME = "ws";
    private static final String WSS_URI_SCHEME = "wss";
    private static final String WS_WRITER = "WebSocketWriter";
    private static final String WS_READER = "WebSocketReader";
    private final Handler mHandler;
    private WebSocketReader mWebSocketReader;
    private WebSocketWriter mWebSocketWriter;
    private Socket mSocket;
    private WebSocketConnectionCustom.SocketThread mSocketThread;
    private URI mWebSocketURI;
    private String[] mWebSocketSubprotocols;
    private WeakReference<WebSocketConnectionObserver> mWebSocketConnectionObserver;
    private WebSocketOptions mWebSocketOptions;
    private boolean mPreviousConnection = false;

    public WebSocketConnectionCustom() {
        Log.d(TAG, "WebSocket connection created.");
        this.mHandler = new WebSocketConnectionCustom.ThreadHandler(this);
    }

    public void sendTextMessage(String payload) {
        this.mWebSocketWriter.forward(new WebSocketMessage.TextMessage(payload));
    }

    public void sendRawTextMessage(byte[] payload) {
        this.mWebSocketWriter.forward(new WebSocketMessage.RawTextMessage(payload));
    }

    public void sendBinaryMessage(byte[] payload) {
        this.mWebSocketWriter.forward(new WebSocketMessage.BinaryMessage(payload));
    }

    public boolean isConnected() {
        return this.mSocket != null && this.mSocket.isConnected() && !this.mSocket.isClosed();
    }

    private void failConnection(WebSocketConnectionObserver.WebSocketCloseNotification code, String reason) {
        Log.d(TAG, "fail connection [code = " + code + ", reason = " + reason);
        if(this.mWebSocketReader != null) {
            this.mWebSocketReader.quit();

            try {
                this.mWebSocketReader.join();
            } catch (InterruptedException var5) {
                var5.printStackTrace();
            }
        } else {
            Log.d(TAG, "mReader already NULL");
        }

        if(this.mWebSocketWriter != null) {
            this.mWebSocketWriter.forward(new WebSocketMessage.Quit());

            try {
                this.mWebSocketWriter.join();
            } catch (InterruptedException var4) {
                var4.printStackTrace();
            }
        } else {
            Log.d(TAG, "mWriter already NULL");
        }

        if(this.mSocket != null) {
            this.mSocketThread.getHandler().post(new Runnable() {
                public void run() {
                    WebSocketConnectionCustom.this.mSocketThread.stopConnection();
                }
            });
        } else {
            Log.d(TAG, "mTransportChannel already NULL");
        }

        this.mSocketThread.getHandler().post(new Runnable() {
            public void run() {
                Looper.myLooper().quit();
            }
        });
        this.onClose(code, reason);
        Log.d(TAG, "worker threads stopped");
    }

    public void connect(URI webSocketURI, WebSocketConnectionObserver connectionObserver) throws WebSocketException {
        this.connect(webSocketURI, connectionObserver, new WebSocketOptions());
    }

    public void connect(URI webSocketURI, WebSocketConnectionObserver connectionObserver, WebSocketOptions options) throws WebSocketException {
        this.connect(webSocketURI, (String[])null, connectionObserver, options);
    }

    public void connect(URI webSocketURI, String[] subprotocols, WebSocketConnectionObserver connectionObserver, WebSocketOptions options) throws WebSocketException {
        if(this.isConnected()) {
            throw new WebSocketException("already connected");
        } else if(webSocketURI == null) {
            throw new WebSocketException("WebSockets URI null.");
        } else {
            this.mWebSocketURI = webSocketURI;
            if(!this.mWebSocketURI.getScheme().equals("ws") && !this.mWebSocketURI.getScheme().equals("wss")) {
                throw new WebSocketException("unsupported scheme for WebSockets URI");
            } else {
                this.mWebSocketSubprotocols = subprotocols;
                this.mWebSocketConnectionObserver = new WeakReference(connectionObserver);
                this.mWebSocketOptions = new WebSocketOptions(options);
                this.connect();
            }
        }
    }

    public void disconnect() {
        if(this.mWebSocketWriter != null && this.mWebSocketWriter.isAlive()) {
            this.mWebSocketWriter.forward(new WebSocketMessage.Close());
        } else {
            Log.d(TAG, "Could not send WebSocket Close .. writer already null");
        }

        this.mPreviousConnection = false;
    }

    public boolean reconnect() {
        if(!this.isConnected() && this.mWebSocketURI != null) {
            this.connect();
            return true;
        } else {
            return false;
        }
    }

    private void connect() {
        this.mSocketThread = new WebSocketConnectionCustom.SocketThread(this.mWebSocketURI, this.mWebSocketOptions);
        this.mSocketThread.start();
        WebSocketConnectionCustom.SocketThread e = this.mSocketThread;
        synchronized(this.mSocketThread) {
            try {
                this.mSocketThread.wait();
            } catch (InterruptedException var6) {
                ;
            }
        }

        this.mSocketThread.getHandler().post(new Runnable() {
            public void run() {
                WebSocketConnectionCustom.this.mSocketThread.startConnection();
            }
        });
        e = this.mSocketThread;
        synchronized(this.mSocketThread) {
            try {
                this.mSocketThread.wait();
            } catch (InterruptedException var4) {
                ;
            }
        }

        this.mSocket = this.mSocketThread.getSocket();
        if(this.mSocket == null) {
            this.onClose(WebSocketConnectionObserver.WebSocketCloseNotification.CANNOT_CONNECT, this.mSocketThread.getFailureMessage());
        } else if(this.mSocket.isConnected()) {
            try {
                this.createReader();
                this.createWriter();
                WebSocketMessage.ClientHandshake e1 = new WebSocketMessage.ClientHandshake(this.mWebSocketURI, (URI)null, this.mWebSocketSubprotocols);
                this.mWebSocketWriter.forward(e1);
            } catch (Exception var3) {
                this.onClose(WebSocketConnectionObserver.WebSocketCloseNotification.INTERNAL_ERROR, var3.getLocalizedMessage());
            }
        } else {
            this.onClose(WebSocketConnectionObserver.WebSocketCloseNotification.CANNOT_CONNECT, "could not connect to WebSockets server");
        }

    }

    protected boolean scheduleReconnect() {
        int interval = this.mWebSocketOptions.getReconnectInterval();
        boolean shouldReconnect = this.mSocket != null && this.mSocket.isConnected() && this.mPreviousConnection && interval > 0;
        if(shouldReconnect) {
            Log.d(TAG, "WebSocket reconnection scheduled");
            this.mHandler.postDelayed(new Runnable() {
                public void run() {
                    Log.d(WebSocketConnectionCustom.TAG, "WebSocket reconnecting...");
                    WebSocketConnectionCustom.this.reconnect();
                }
            }, (long)interval);
        }

        return shouldReconnect;
    }

    private void onClose(WebSocketConnectionObserver.WebSocketCloseNotification code, String reason) {
        boolean reconnecting = false;
        if(code == WebSocketConnectionObserver.WebSocketCloseNotification.CANNOT_CONNECT || code == WebSocketConnectionObserver.WebSocketCloseNotification.CONNECTION_LOST) {
            reconnecting = this.scheduleReconnect();
        }

        WebSocketConnectionObserver webSocketObserver = (WebSocketConnectionObserver)this.mWebSocketConnectionObserver.get();
        if(webSocketObserver != null) {
            try {
                if(reconnecting) {
                    webSocketObserver.onClose(WebSocketConnectionObserver.WebSocketCloseNotification.RECONNECT, reason);
                } else {
                    webSocketObserver.onClose(code, reason);
                }
            } catch (Exception var6) {
                var6.printStackTrace();
            }
        } else {
            Log.d(TAG, "WebSocketObserver null");
        }

    }

    protected void processAppMessage(Object message) {
    }

    protected void createWriter() {
        this.mWebSocketWriter = new WebSocketWriter(this.mHandler, this.mSocket, this.mWebSocketOptions, "WebSocketWriter");
        this.mWebSocketWriter.start();
        WebSocketWriter var1 = this.mWebSocketWriter;
        synchronized(this.mWebSocketWriter) {
            try {
                this.mWebSocketWriter.wait();
            } catch (InterruptedException var3) {
                ;
            }
        }

        Log.d(TAG, "WebSocket writer created and started.");
    }

    protected void createReader() {
        this.mWebSocketReader = new WebSocketReader(this.mHandler, this.mSocket, this.mWebSocketOptions, "WebSocketReader");
        this.mWebSocketReader.start();
        WebSocketReader var1 = this.mWebSocketReader;
        synchronized(this.mWebSocketReader) {
            try {
                this.mWebSocketReader.wait();
            } catch (InterruptedException var3) {
                ;
            }
        }

        Log.d(TAG, "WebSocket reader created and started.");
    }

    private void handleMessage(Message message) {
        WebSocketConnectionObserver webSocketObserver = (WebSocketConnectionObserver)this.mWebSocketConnectionObserver.get();
        if(message.obj instanceof WebSocketMessage.TextMessage) {
            WebSocketMessage.TextMessage error = (WebSocketMessage.TextMessage)message.obj;
            if(webSocketObserver != null) {
                webSocketObserver.onTextMessage(error.mPayload);
            } else {
                Log.d(TAG, "could not call onTextMessage() .. handler already NULL");
            }
        } else if(message.obj instanceof WebSocketMessage.RawTextMessage) {
            WebSocketMessage.RawTextMessage error1 = (WebSocketMessage.RawTextMessage)message.obj;
            if(webSocketObserver != null) {
                webSocketObserver.onRawTextMessage(error1.mPayload);
            } else {
                Log.d(TAG, "could not call onRawTextMessage() .. handler already NULL");
            }
        } else if(message.obj instanceof WebSocketMessage.BinaryMessage) {
            WebSocketMessage.BinaryMessage error2 = (WebSocketMessage.BinaryMessage)message.obj;
            if(webSocketObserver != null) {
                webSocketObserver.onBinaryMessage(error2.mPayload);
            } else {
                Log.d(TAG, "could not call onBinaryMessage() .. handler already NULL");
            }
        } else if(message.obj instanceof WebSocketMessage.Ping) {
            WebSocketMessage.Ping error3 = (WebSocketMessage.Ping)message.obj;
            Log.d(TAG, "WebSockets Ping received");
            WebSocketMessage.Pong pong = new WebSocketMessage.Pong();
            pong.mPayload = error3.mPayload;
            this.mWebSocketWriter.forward(pong);
        } else if(message.obj instanceof WebSocketMessage.Pong) {
            WebSocketMessage.Pong error4 = (WebSocketMessage.Pong)message.obj;
            Log.d(TAG, "WebSockets Pong received" + error4.mPayload);
        } else if(message.obj instanceof WebSocketMessage.Close) {
            WebSocketMessage.Close error5 = (WebSocketMessage.Close)message.obj;
            Log.d(TAG, "WebSockets Close received (" + error5.getCode() + " - " + error5.getReason() + ")");
            this.mWebSocketWriter.forward(new WebSocketMessage.Close(1000));
        } else if(message.obj instanceof WebSocketMessage.ServerHandshake) {
            WebSocketMessage.ServerHandshake error6 = (WebSocketMessage.ServerHandshake)message.obj;
            Log.d(TAG, "opening handshake received");
            if(error6.mSuccess) {
                if(webSocketObserver != null) {
                    webSocketObserver.onOpen();
                } else {
                    Log.d(TAG, "could not call onOpen() .. handler already NULL");
                }

                this.mPreviousConnection = true;
            }
        } else if(message.obj instanceof WebSocketMessage.ConnectionLost) {
            this.failConnection(WebSocketConnectionObserver.WebSocketCloseNotification.CONNECTION_LOST, "WebSockets connection lost");
        } else if(message.obj instanceof WebSocketMessage.ProtocolViolation) {
            this.failConnection(WebSocketConnectionObserver.WebSocketCloseNotification.PROTOCOL_ERROR, "WebSockets protocol violation");
        } else if(message.obj instanceof WebSocketMessage.Error) {
            WebSocketMessage.Error error7 = (WebSocketMessage.Error)message.obj;
            this.failConnection(WebSocketConnectionObserver.WebSocketCloseNotification.INTERNAL_ERROR, "WebSockets internal error (" + error7.mException.toString() + ")");
        } else if(message.obj instanceof WebSocketMessage.ServerError) {
            WebSocketMessage.ServerError error8 = (WebSocketMessage.ServerError)message.obj;
            this.failConnection(WebSocketConnectionObserver.WebSocketCloseNotification.SERVER_ERROR, "Server error " + error8.mStatusCode + " (" + error8.mStatusMessage + ")");
        } else {
            this.processAppMessage(message.obj);
        }

    }

    public static class SocketThread extends Thread {
        private static final String WS_CONNECTOR = "WebSocketConnector";
        private final URI mWebSocketURI;
        private Socket mSocket = null;
        private String mFailureMessage = null;
        private Handler mHandler;

        public SocketThread(URI uri, WebSocketOptions options) {
            this.setName("WebSocketConnector");
            this.mWebSocketURI = uri;
        }

        public void run() {
            Looper.prepare();
            this.mHandler = new Handler();
            synchronized(this) {
                this.notifyAll();
            }

            Looper.loop();
            Log.d(WebSocketConnectionCustom.TAG, "SocketThread exited.");
        }

        public void startConnection() {
            try {
                String e = this.mWebSocketURI.getHost();
                int port = this.mWebSocketURI.getPort();
                if(port == -1) {
                    if(this.mWebSocketURI.getScheme().equals("wss")) {
                        port = 443;
                    } else {
                        port = 80;
                    }
                }

                SocketFactory factory = null;
                if(this.mWebSocketURI.getScheme().equalsIgnoreCase("wss")) {
                    //SSLTrustManager sslTrustManager = new SSLTrustManager();

                    //factory = sslTrustManager.GetSocketFactory();

                    // Create a trust manager that does not validate certificate chains
                    X509TrustManager[] trustAllCerts = new X509TrustManager[] {
                            new X509TrustManager() {
                                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                    return new X509Certificate[0];
                                }
                                public void checkClientTrusted(
                                        java.security.cert.X509Certificate[] certs, String authType) {
                                }
                                public void checkServerTrusted(
                                        java.security.cert.X509Certificate[] certs, String authType) {
                                }
                            }
                    };
                    // Install the all-trusting trust manager
                    SSLSocketFactory noSSLv3Factory = null;
                    try {
                        SSLContext sc = SSLContext.getInstance("TLSv1");
                        sc.init(null, trustAllCerts, new java.security.SecureRandom());
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.KITKAT) {
                            noSSLv3Factory = new TLSSocketFactory(trustAllCerts, new java.security.SecureRandom());
                        } else {
                            noSSLv3Factory = sc.getSocketFactory();
                        }
                        factory = noSSLv3Factory;
                    } catch (GeneralSecurityException ex) {
                    }

                } else {
                    factory = SocketFactory.getDefault();
                }

                this.mSocket = factory.createSocket(e, port);
            } catch (IOException var5) {
                this.mFailureMessage = var5.getLocalizedMessage();
            }

            synchronized(this) {
                this.notifyAll();
            }
        }

        public void stopConnection() {
            try {
                this.mSocket.close();
                this.mSocket = null;
            } catch (IOException var2) {
                this.mFailureMessage = var2.getLocalizedMessage();
            }

        }

        public Handler getHandler() {
            return this.mHandler;
        }

        public Socket getSocket() {
            return this.mSocket;
        }

        public String getFailureMessage() {
            return this.mFailureMessage;
        }
    }

    private static class ThreadHandler extends Handler {
        private final WeakReference<WebSocketConnectionCustom> mWebSocketConnection;

        public ThreadHandler(WebSocketConnectionCustom webSocketConnection) {
            this.mWebSocketConnection = new WeakReference(webSocketConnection);
        }

        public void handleMessage(Message message) {
            WebSocketConnectionCustom webSocketConnection = (WebSocketConnectionCustom)this.mWebSocketConnection.get();
            if(webSocketConnection != null) {
                webSocketConnection.handleMessage(message);
            }

        }
    }
}
