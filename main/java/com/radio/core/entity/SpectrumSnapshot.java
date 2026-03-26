package com.radio.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 频谱快照表
 */
@Data
@TableName("spectrum_snapshot")
public class SpectrumSnapshot {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long stationId;

    private Long deviceId;

    private Long taskId;

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
     * 本次实际使用的模型名
     */
    private String aiModelName;

    /**
     * AI 解释文本 / fallback 原因
     */
    private String aiReason;

    private Integer alarmFlag;

    /**
     * MySQL JSON 字段
     */
    private String powerPointsJson;

    /**
     * MySQL JSON 字段
     */
    private String waterfallRowJson;

    private LocalDateTime captureTime;

    private LocalDateTime createTime;
}
