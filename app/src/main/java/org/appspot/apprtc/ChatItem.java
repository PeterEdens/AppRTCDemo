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
    FileInfo fileinfo;

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

    public ChatItem(String time, String displayName, String text, String buddyPicture, String Id) {
        this.time = time;
        this.displayName = displayName;
        this.text = text;
        this.buddyPicture = buddyPicture;
        this.Id = Id;
    }

    public ChatItem(String time, String displayName, FileInfo fileinfo, String buddyPicture, String Id) {
        this.time = time;
        this.displayName = displayName;
        this.fileinfo = fileinfo;
        this.buddyPicture = buddyPicture;
        this.Id = Id;
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
}
