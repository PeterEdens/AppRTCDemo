package org.appspot.apprtc;

import java.io.Serializable;

/**
 * Created by petere on 3/7/2017.
 */
public class ChatItem implements Serializable{
    String time;
    public String displayName;
    String text;
    public String buddyPicture;
    public String Id;
    boolean outgoing;
    FileInfo fileinfo;
    String to; // receipient of message
    int notificationId = 0;

    public void setOutgoing() {
        outgoing = true;
    }
    public long getFilesize() {
        if (fileinfo != null) {
            return Long.valueOf(fileinfo.size);
        }
        return 0;
    }

    public void setPercentDownloaded(int percentDownloaded) {
        if (fileinfo != null) {
            fileinfo.setPercentDownloaded(percentDownloaded);
        }
    }

    public ChatItem(String time, String displayName, String text, String buddyPicture, String Id, String to) {
        this.time = time;
        this.displayName = displayName;
        this.text = text;
        this.buddyPicture = buddyPicture;
        this.Id = Id;
        this.to = to;
    }

    public ChatItem(String time, String displayName, FileInfo fileinfo, String buddyPicture, String Id) {
        this.time = time;
        this.displayName = displayName;
        this.fileinfo = fileinfo;
        this.buddyPicture = buddyPicture;
        this.Id = Id;
        this.to = Id;
    }

    public void setDownloadPath(String path) {
        fileinfo.setDownloadPath(path);
    }

    public String getDownloadPath() {
        return fileinfo.getDownloadPath();
    }

    public void setDownloadComplete() {
        fileinfo.setDownloadState(FileInfo.DownloadState.COMPLETED);
    }

    public void setDownloadFailed(String description) {
        if (fileinfo != null) {
            fileinfo.setErrorMessage(description);
        }
    }

    public String getRecipient() {
        return to;
    }

    public void setNotificationId(int notificationId) {
        this.notificationId = notificationId;
    }

    public int getNotificationId() {
        return notificationId;
    }
}
