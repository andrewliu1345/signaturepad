package com.joesmate.signaturepad.utils;

public class TimedPoint {
    public float x;
    public float y;
    public float w;
    public int a=0;//时间戳
    public long timestamp;

    public TimedPoint set(float x, float y) {
        this.x = x;
        this.y = y;
        this.timestamp = System.currentTimeMillis();
        return this;
    }

    public TimedPoint set(float x, float y, float w) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.timestamp = System.currentTimeMillis();
        return this;
    }

    public TimedPoint set(float x, float y, float w, int a) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.a = a;
        this.timestamp = System.currentTimeMillis();
        return this;
    }

    public float velocityFrom(TimedPoint start) {
        float velocity = distanceTo(start) / (this.timestamp - start.timestamp);
        if (velocity != velocity) return 0f;
        return velocity;
    }

    public float distanceTo(TimedPoint point) {
        return (float) Math.sqrt(Math.pow(point.x - this.x, 2) + Math.pow(point.y - this.y, 2));
    }
}
