package com.radio.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监测任务表
 */
@Data
@TableName("monitor_task")
public class MonitorTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String taskName;

    private Long stationId;

    private Long deviceId;

    private BigDecimal freqStartMhz;

    private BigDecimal freqEndMhz;

    private BigDecimal sampleRateKhz;

    private String algorithmMode;

    private Integer taskStatus;

    private String cronExpr;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}