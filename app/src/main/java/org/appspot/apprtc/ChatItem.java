package org.appspot.apprtc;

/**
 * Created by petere on 3/7/2017.
 */
public class ChatItem {
    String time;
    String displayName;
    String text;
    String buddyPicture;
    String Id;
    boolean outgoing;

    public void setOutgoing() {
        outgoing = true;
    }

    public ChatItem(String time, String displayName, String text, String buddyPicture, String Id) {
        this.time = time;
        this.displayName = displayName;
        this.text = text;
        this.buddyPicture = buddyPicture;
        this.Id = Id;
    }
}
