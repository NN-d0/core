package com.radio.core.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 采集上报响应对象
 */
@Data
public class CollectReportResponse {

    /**
     * Core 是否已接收并处理本次上报
     */
    private Boolean accepted;

    /**
     * 快照ID
     */
    private Long snapshotId;

    /**
     * 告警ID
     */
    private Long alarmId;

    /**
     * 是否触发告警：0/1
     */
    private Integer alarmFlag;

    /**
     * AI 识别标签
     */
    private String aiLabel;

    /**
     * AI 请求模式：RULE / CNN / AUTO
     */
    private String aiRequestMode;

    /**
     * AI 实际生效模式：RULE / CNN
     */
    private String aiActualMode;

    /**
     * 是否发生 fallback：0-否，1-是
     */
    private Integer aiFallbackUsed;

    /**
     * 实际模型名
     */
    private String aiModelName;

    /**
     * AI 说明 / fallback 原因
     */
    private String aiReason;

    /**
     * 当前任务状态
     * 0-未启动
     * 1-运行中
     * 2-已停止
     */
    private Integer taskStatus;

    private Long stationId;

    private Long deviceId;

    private Long taskId;

    private LocalDateTime captureTime;
}
