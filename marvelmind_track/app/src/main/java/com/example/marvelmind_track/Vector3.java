package com.example.marvelmind_track;

/**
 * Created by ideog on 20.11.2017.
 */


public class Vector3 {
    public float x;
    public float y;
    public float z;

    public Vector3(float x, float y, float z) {
        this.set(x, y, z);
    }

    public Vector3(Vector3 v) {
        this.set(v);
    }

    public Vector3() {
        this.set(0, 0, 0);
    }

    public Vector3 set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        return this;
    }

    public Vector3 set(Vector3 v) {
        x = v.x;
        y = v.y;
        z = v.z;
        return this;
    }

    public void normalize() {
        float norm = norm();
        x = x / norm;
        y = y / norm;
        z = z / norm;
    }

    public Vector3 add(Vector3 v) {
        Vector3 v1 = this;
        Vector3 v2 = v;
        v1.x += v2.x;
        v1.y += v2.y;
        v1.z += v2.z;
        return v1;
    }

    public Vector3 minus(Vector3 v) {
        Vector3 v1 = this;
        Vector3 v2 = v;
        v1.x -= v2.x;
        v1.y -= v2.y;
        v1.z -= v2.z;
        return v1;
    }

    public Vector3 mul1d(float a) {
        Vector3 v = this;
        v.x *= a;
        v.y *= a;
        v.z *= a;
        return v;
    }

    public Vector3 cross(Vector3 v) {
        Vector3 v1 = this;
        Vector3 v2 = v;
        v.x = v1.y * v2.z - v1.z * v2.y;
        v.y = v1.z * v2.x - v1.x * v2.z;
        v.z = v1.x * v2.y - v1.y * v2.x;
        return v;
    }

    public float dot(Vector3 v) {
        Vector3 v1 = this;
        Vector3 v2 = v;
        return v1.x * v2.x + v1.y * v2.y + v1.z * v2.z;
    }


    public float norm() {
        return (float) Math.sqrt(x * x + y * y + z * z);
    }

    public boolean equal(Vector3 v) {
        Vector3 v1 = new Vector3(v);
        Vector3 v2 = new Vector3(this);
        return ((v1.x - v2.x) + (v1.y - v2.y) + (v1.z - v2.z)) == 0;
    }

    public String ToString() {
        return String.valueOf(x) + "," + String.valueOf(y) + "," + String.valueOf(z);
    }
}