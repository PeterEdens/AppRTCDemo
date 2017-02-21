package org.appspot.apprtc;


import org.webrtc.SessionDescription;

import java.io.Serializable;

public class SerializableSessionDescription implements Serializable {
    public SessionDescription.Type type;
    public String description;

    public SerializableSessionDescription() {
    }

    public SerializableSessionDescription(SessionDescription.Type type, String description) {
        this.type = type;
        this.description = description;
    }

    public static enum Type {
        OFFER,
        PRANSWER,
        ANSWER;

        private Type() {
        }

        public String canonicalForm() {
            return this.name().toLowerCase();
        }

        public static SessionDescription.Type fromCanonicalForm(String canonical) {
            return (SessionDescription.Type)valueOf(SessionDescription.Type.class, canonical.toUpperCase());
        }
    }
}