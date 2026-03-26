package com.radio.core.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 实时频谱返回对象
 */
@Data
public class RealtimeSpectrumVO {

    private Long id;

    private Long stationId;
    private String stationName;

    private Long deviceId;
    private String deviceName;

    private Long taskId;
    private String taskName;

    private BigDecimal centerFreqMhz;
    private BigDecimal bandwidthKhz;
    private String signalType;
    private String channelModel;
    private BigDecimal peakPowerDbm;
    private BigDecimal snrDb;
    private BigDecimal occupiedBandwidthKhz;
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
     * AI 解释文本 / fallback 原因
     */
    private String aiReason;

    private Integer alarmFlag;

    private String powerPointsJson;
    private String waterfallRowJson;

    private LocalDateTime captureTime;
    private LocalDateTime createTime;
}
