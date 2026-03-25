package com.radio.core.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Core -> Flask AI 的请求对象
 *
 * 注意：
 * Python Flask 当前要求的是 snake_case 字段名，
 * 所以这里用 @JsonProperty 显式映射。
 */
@Data
public class AiPredictRequest {

    @JsonProperty("center_freq_mhz")
    private BigDecimal centerFreqMhz;

    @JsonProperty("bandwidth_khz")
    private BigDecimal bandwidthKhz;

    @JsonProperty("peak_power_dbm")
    private BigDecimal peakPowerDbm;

    @JsonProperty("snr_db")
    private BigDecimal snrDb;

    @JsonProperty("occupied_bandwidth_khz")
    private BigDecimal occupiedBandwidthKhz;

    @JsonProperty("channel_model")
    private String channelModel;

    @JsonProperty("power_points")
    private List<BigDecimal> powerPoints;
}