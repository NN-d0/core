package com.radio.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 告警记录表
 */
@Data
@TableName("alarm_record")
public class AlarmRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String alarmNo;

    private Long stationId;

    private Long deviceId;

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