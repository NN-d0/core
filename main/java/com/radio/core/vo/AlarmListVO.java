package com.radio.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警列表返回对象
 */
@Data
public class AlarmListVO {

    private Long id;
    private String alarmNo;
    private Long stationId;
    private String stationName;
    private Long deviceId;
    private String deviceName;
    private Long taskId;
    private Long snapshotId;
    private String alarmType;
    private String alarmLevel;
    private String title;
    private String content;
    private Integer alarmStatus;
    private Long handleUserId;
    private String handleNote;
    private LocalDateTime alarmTime;
    private LocalDateTime confirmTime;
    private LocalDateTime handleTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}