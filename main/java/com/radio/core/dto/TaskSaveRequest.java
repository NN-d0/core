package com.radio.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 任务新增/修改请求
 */
@Data
public class TaskSaveRequest {

    private Long id;

    @NotBlank(message = "任务名称不能为空")
    private String taskName;

    @NotNull(message = "所属站点不能为空")
    private Long stationId;

    @NotNull(message = "所属设备不能为空")
    private Long deviceId;

    @NotNull(message = "起始频率不能为空")
    private BigDecimal freqStartMhz;

    @NotNull(message = "结束频率不能为空")
    private BigDecimal freqEndMhz;

    @NotNull(message = "采样率不能为空")
    private BigDecimal sampleRateKhz;

    @NotBlank(message = "算法模式不能为空")
    private String algorithmMode;

    @NotNull(message = "任务状态不能为空")
    private Integer taskStatus;

    @NotBlank(message = "调度表达式不能为空")
    private String cronExpr;
}