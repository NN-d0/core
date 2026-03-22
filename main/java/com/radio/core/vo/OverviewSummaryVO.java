package com.radio.core.vo;

import lombok.Data;

/**
 * 首页总览统计返回对象
 */
@Data
public class OverviewSummaryVO {

    private Integer stationCount;
    private Integer onlineStationCount;
    private Integer offlineStationCount;

    private Integer deviceCount;
    private Integer runningDeviceCount;
    private Integer stopDeviceCount;

    private Integer alarmCount;
    private Integer unreadAlarmCount;
    private Integer confirmedAlarmCount;
    private Integer handledAlarmCount;
}