package org.appspot.apprtc;


import java.io.Serializable;

public class User implements Serializable{
    String userId;
    String buddyPicture;
    public String displayName;
    public String Id;
    public String message;

    public User(String userid, String pic, String name, String id) {
        this.userId = userid;
        this.buddyPicture = pic;
        this.displayName = name;
        this.Id = id;
    }
}