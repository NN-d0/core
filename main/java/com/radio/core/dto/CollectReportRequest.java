package com.radio.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 采集上报请求对象
 */
@Data
public class CollectReportRequest {

    /**
     * 站点ID
     */
    @NotNull(message = "stationId 不能为空")
    private Long stationId;

    /**
     * 设备ID
     */
    @NotNull(message = "deviceId 不能为空")
    private Long deviceId;

    /**
     * 任务ID
     *
     * 当前版本要求必须传入：
     * 因为 Core 要校验该任务是否处于运行中（task_status = 1）
     */
    @NotNull(message = "taskId 不能为空")
    private Long taskId;

    /**
     * 中心频率 MHz
     */
    @NotNull(message = "centerFreqMhz 不能为空")
    private BigDecimal centerFreqMhz;

    /**
     * 带宽 kHz
     */
    @NotNull(message = "bandwidthKhz 不能为空")
    private BigDecimal bandwidthKhz;

    /**
     * 信号制式：AM/FM/BPSK/QPSK/16QAM
     */
    @NotBlank(message = "signalType 不能为空")
    private String signalType;

    /**
     * 信道模型：AWGN / Rayleigh / CarrierOffset / SampleRateError / PathLoss
     */
    private String channelModel;

    /**
     * 峰值功率 dBm
     */
    @NotNull(message = "peakPowerDbm 不能为空")
    private BigDecimal peakPowerDbm;

    /**
     * 信噪比 dB
     */
    @NotNull(message = "snrDb 不能为空")
    private BigDecimal snrDb;

    /**
     * 占用带宽 kHz
     */
    private BigDecimal occupiedBandwidthKhz;

    /**
     * AI 标签
     *
     * 当前主链路里，最终以 Core 调 Flask AI 的结果为准。
     * 这里保留字段只是为了兼容历史调用，不再作为主来源。
     */
    private String aiLabel;

    /**
     * 是否强制触发告警：0/1
     * 当前主链路优先以 AI shouldAlarm 为准，兜底再走 Core 阈值
     */
    private Integer alarmFlag;

    /**
     * 频谱折线点
     */
    @NotEmpty(message = "powerPoints 不能为空")
    private List<BigDecimal> powerPoints;

    /**
     * 瀑布图单行数据
     * 可为空；为空时默认复用 powerPoints
     */
    private List<BigDecimal> waterfallRow;

    /**
     * 采集时间
     * 支持：
     * 1. yyyy-MM-dd HH:mm:ss
     * 2. ISO_LOCAL_DATE_TIME
     * 不传则默认当前时间
     */
    private String captureTime;
}