package com.radio.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备列表返回对象
 */
@Data
public class DeviceListVO {

    private Long id;
    private String deviceCode;
    private String deviceName;
    private Long stationId;
    private String stationName;
    private String deviceType;
    private String ipAddr;
    private Integer runStatus;
    private LocalDateTime lastOnlineTime;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}