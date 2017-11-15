package org.appspot.apprtc.util;

import android.util.Log;

import javax.net.ssl.HostnameVerifier ;
import javax.net.ssl.SSLSession;

public class NullHostNameVerifier implements HostnameVerifier {

    private final String mHostname;

    public NullHostNameVerifier(String hostname) {
        mHostname = hostname;
    }

    @Override
    public boolean verify(String hostname, SSLSession session) {
        if (mHostname.equals(hostname)) {
            Log.i("RestUtilImpl", "Approving certificate for " + hostname);
            return true;
        }
        return false;
    }

}