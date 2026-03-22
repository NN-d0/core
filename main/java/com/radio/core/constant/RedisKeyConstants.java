package com.radio.core.constant;

/**
 * Redis Key 常量
 */
public class RedisKeyConstants {

    private RedisKeyConstants() {
    }

    public static final String REALTIME_LATEST_GLOBAL = "radio:realtime:latest:global";
    public static final String ALARM_UNREAD_COUNT = "radio:alarm:unread:count";

    public static String realtimeLatest(Long stationId) {
        return "radio:realtime:latest:" + stationId;
    }

    public static String stationOnline(Long stationId) {
        return "radio:station:online:" + stationId;
    }
}