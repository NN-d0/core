package com.radio.core.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 历史回放列表返回对象
 */
@Data
public class HistorySnapshotVO {

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
    private Integer alarmFlag;

    private String powerPointsJson;
    private String waterfallRowJson;

    private LocalDateTime captureTime;
    private LocalDateTime createTime;
}