package com.radio.core.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * Flask AI /predict 返回的 data 对象
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiPredictResultVO {

    /**
     * 预测标签，例如 AM / FM / BPSK / QPSK / 16QAM
     */
    @JsonProperty("predicted_label")
    private String predictedLabel;

    /**
     * 置信度
     */
    private Double confidence;

    /**
     * 风险等级：LOW / MEDIUM / HIGH
     */
    @JsonProperty("risk_level")
    private String riskLevel;

    /**
     * 是否建议告警
     */
    @JsonProperty("should_alarm")
    private Boolean shouldAlarm;

    /**
     * 解释原因
     */
    private String reason;

    /**
     * 模型名，例如 rule-model-v1
     */
    @JsonProperty("model_name")
    private String modelName;

    /**
     * 阈值信息
     */
    private Map<String, Object> thresholds;
}