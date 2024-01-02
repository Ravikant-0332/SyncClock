package com.app.syncclock;

public class CustomClock {

    public static final int IDLE = 0;
    public static final int RUNNING = 1;
    public static final int STOP = 2;
    private String id="";
    private String time="";
    private String uid1="";
    private String uid2="";
    private boolean availability=true;

    private int state = IDLE;

    public CustomClock(){};

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getUid1() {
        return uid1;
    }

    public void setUid1(String uid1) {
        this.uid1 = uid1;
    }

    public String getUid2() {
        return uid2;
    }

    public void setUid2(String uid2) {
        this.uid2 = uid2;
    }

    public boolean getAvailability() {
        return availability;
    }

    public void setAvailability(boolean availability) {
        this.availability = availability;
    }
}
