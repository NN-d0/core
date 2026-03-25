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
     * 兼容字段：允许上报方传入 AI 标签
     * 当前主链路最终仍以 Core 调 Flask 返回结果为准
     */
    private String aiLabel;

    /**
     * 兼容字段：允许上报方传入 alarmFlag
     * 当前主链路最终仍优先以 AI shouldAlarm / Core 阈值为准
     */
    private Integer alarmFlag;

    /**
     * 频谱折线点
     */
    @NotEmpty(message = "powerPoints 不能为空")
    private List<BigDecimal> powerPoints;

    /**
     * 瀑布图单行数据
     */
    private List<BigDecimal> waterfallRow;

    /**
     * 采集时间
     * 支持：
     * 1. yyyy-MM-dd HH:mm:ss
     * 2. ISO_LOCAL_DATE_TIME
     */
    private String captureTime;

    /**
     * AI 模型模式：
     * rule / cnn / auto
     *
     * 当前如果不传，由 Core 配置 ai.default-model-type 决定
     */
    private String modelType;

    /**
     * I 通道点列
     * 当存在且与 qPoints 配对时，Core 会把它一起传给 Flask /predict
     */
    private List<BigDecimal> iPoints;

    /**
     * Q 通道点列
     */
    private List<BigDecimal> qPoints;
}