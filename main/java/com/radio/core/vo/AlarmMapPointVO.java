package com.radio.core.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 告警地图点位返回对象
 */
@Data
public class AlarmMapPointVO {

    private Long id;
    private String alarmNo;

    private Long stationId;
    private String stationName;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private String locationText;

    private Long deviceId;
    private String deviceName;

    private String alarmType;
    private String alarmLevel;
    private String title;
    private String content;
    private Integer alarmStatus;
    private LocalDateTime alarmTime;
}