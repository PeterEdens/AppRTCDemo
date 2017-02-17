package org.appspot.apprtc;


public class User {
    String userId;
    String buddyPicture;
    String displayName;
    String Id;

    public User(String userid, String pic, String name, String id) {
        this.userId = userid;
        this.buddyPicture = pic;
        this.displayName = name;
        this.Id = id;
    }
}