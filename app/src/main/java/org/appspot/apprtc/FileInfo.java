package org.appspot.apprtc;

import java.io.Serializable;


/**
 * Created by petere on 3/22/2017.
 */

public class FileInfo implements Serializable {
    String id;
    String chunks;
    String name;
    String size;
    String type;
    int percentDownloaded;
    String downloadPath;

    public enum DownloadState {
        IDLE,
        DOWNLOADING,
        COMPLETED,
        FAILED
    }

    private DownloadState downloadState = DownloadState.IDLE;

    public DownloadState getDownloadState() {
        return downloadState;
    }

    public void setDownloadState(DownloadState state) {
        downloadState = state;
    }

    public FileInfo(String id, String chunks, String name, String size, String type) {
        this.id = id;
        this.chunks = chunks;
        this.name = name;
        this.size = size;
        this.type = type;
    }

    public void setPercentDownloaded(int downloaded) {
        this.percentDownloaded = downloaded;
    }

    public void setDownloadPath(String path) {
        downloadPath = path;
    }

    public String getDownloadPath() {
        return downloadPath;
    }
}
