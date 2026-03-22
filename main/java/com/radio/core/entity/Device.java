package com.radio.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 监测设备表
 */
@Data
@TableName("device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String deviceCode;

    private String deviceName;

    private Long stationId;

    private String deviceType;

    private String ipAddr;

    private Integer runStatus;

    private LocalDateTime lastOnlineTime;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}