package com.radio.core.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 任务列表返回对象
 */
@Data
public class TaskListVO {

    private Long id;
    private String taskName;
    private Long stationId;
    private String stationName;
    private Long deviceId;
    private String deviceName;
    private BigDecimal freqStartMhz;
    private BigDecimal freqEndMhz;
    private BigDecimal sampleRateKhz;
    private String algorithmMode;
    private Integer taskStatus;
    private String cronExpr;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}