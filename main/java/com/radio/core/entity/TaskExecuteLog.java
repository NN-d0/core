package com.radio.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务执行日志表
 */
@Data
@TableName("task_execute_log")
public class TaskExecuteLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String taskName;

    private Long stationId;

    private String stationName;

    private Long deviceId;

    private String deviceName;

    /**
     * 1=成功 2=失败 3=手动停止
     */
    private Integer execStatus;

    /**
     * SCHEDULER / MANUAL_STOP
     */
    private String triggerType;

    private String execMessage;

    private Long snapshotId;

    private Long alarmId;

    private Long durationMs;

    private LocalDateTime executeTime;

    private LocalDateTime createTime;
}