package org.appspot.apprtc;


import java.io.Serializable;

public class User implements Serializable{
    String userId;
    String buddyPicture;
    String displayName;
    public String Id;

    public User(String userid, String pic, String name, String id) {
        this.userId = userid;
        this.buddyPicture = pic;
        this.displayName = name;
        this.Id = id;
    }
}