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
     * 实际生效的模型名，例如 rule-model-v1 / 1dcnn-v1 / core-fallback-rule
     */
    @JsonProperty("model_name")
    private String modelName;

    /**
     * AI 请求模式：RULE / CNN / AUTO
     */
    @JsonProperty("request_mode")
    private String requestMode;

    /**
     * 推理细分模式：rule / cnn
     */
    @JsonProperty("inference_mode")
    private String inferenceMode;

    /**
     * 实际使用的模式：RULE / CNN
     */
    @JsonProperty("actual_mode")
    private String actualMode;

    /**
     * 是否发生回退
     */
    @JsonProperty("fallback_used")
    private Boolean fallbackUsed;

    /**
     * 回退原因
     */
    @JsonProperty("fallback_reason")
    private String fallbackReason;

    /**
     * 阈值信息
     */
    private Map<String, Object> thresholds;
}