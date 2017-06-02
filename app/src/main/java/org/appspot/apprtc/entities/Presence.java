package org.appspot.apprtc.entities;

import java.util.Locale;

public class Presence implements Comparable {

	public enum Status {
		CHAT, ONLINE, AWAY, XA, DND, OFFLINE;

		public String toShowString() {
			switch(this) {
				case CHAT: return "chat";
				case AWAY: return "away";
				case XA:   return "xa";
				case DND:  return "dnd";
			}
			return null;
		}

		public static Status fromShowString(String show) {
			if (show == null) {
				return ONLINE;
			} else {
				switch (show.toLowerCase(Locale.US)) {
					case "away":
						return AWAY;
					case "xa":
						return XA;
					case "dnd":
						return DND;
					case "chat":
						return CHAT;
					default:
						return ONLINE;
				}
			}
		}
	}

	private final Status status;
	private final String ver;
	private final String hash;
	private final String message;

	private Presence(Status status, String ver, String hash, String message) {
		this.status = status;
		this.ver = ver;
		this.hash = hash;
		this.message = message;
	}

	public int compareTo(Object other) {
		return this.status.compareTo(((Presence)other).status);
	}

	public Status getStatus() {
		return this.status;
	}

	public boolean hasCaps() {
		return ver != null && hash != null;
	}

	public String getVer() {
		return this.ver;
	}

	public String getHash() {
		return this.hash;
	}

	public String getMessage() {
		return this.message;
	}

}
