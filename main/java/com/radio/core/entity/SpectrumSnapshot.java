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