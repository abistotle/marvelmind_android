package com.example.marvelmind_track;

public class MarvelmindPos {
    public int address = 0;
    public Vector3 pos = new Vector3();
    public float timestamp = 0;
    public boolean oriented= false;
    public float angle= 0.0f;

    public MarvelmindPos(int address, float x, float y, float z, float t) {
        this.set(address, x, y, z, t, false, 0.0f);
    }

    public MarvelmindPos(int address, Vector3 v, float t) {
        this.set(address, v.x, v.y, v.z, t, false, 0.0f);
    }

    public MarvelmindPos() {
        this.set(0, 0, 0, 0, 0, false, 0.0f);
    }

    public MarvelmindPos set(int address, float x, float y, float z, float t, boolean oriented_, float angle_) {
        this.address = address;
        this.pos.set(x, y, z);
        this.timestamp = t;
        this.oriented= oriented_;
        this.angle= angle;
        return this;
    }

//    public MarvelmindPos set(int address, Vector3 v, float t) {
//        this.address = address;
//        this.pos.set(v);
//        this.timestamp = t;
//        return this;
//    }

    public MarvelmindPos set(MarvelmindPos mp) {
        this.address = mp.address;
        this.pos.set(mp.pos);
        this.timestamp = mp.timestamp;
        this.oriented= mp.oriented;
        this.angle= mp.angle;
        return this;
    }
}
