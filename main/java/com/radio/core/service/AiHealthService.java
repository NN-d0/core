package com.radio.core.service;

import com.radio.core.common.ApiResponse;
import com.radio.core.vo.AiHealthStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 健康检查服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiHealthService {

    private final RestTemplate restTemplate;

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${ai.base-url:http://127.0.0.1:9300}")
    private String aiBaseUrl;

    @Value("${ai.health-path:/health}")
    private String aiHealthPath;

    /**
     * 查询 Flask AI /health，并转换为前端可直接展示的结构
     */
    public AiHealthStatusVO getAiHealthStatus(boolean writeLog) {
        AiHealthStatusVO status = new AiHealthStatusVO();
        status.setAiEnabled(aiEnabled);
        status.setAiBaseUrl(aiBaseUrl);
        status.setAiHealthUrl(buildHealthUrl());
        status.setFetchedAt(LocalDateTime.now());

        if (!aiEnabled) {
            status.setService("radio-spectrum-ai");
            status.setServiceStatus("DISABLED");
            status.setServiceUp(false);
            status.setDefaultMode("rule");
            status.setAllowRuleFallback(true);
            status.setRuleModelName("core-fallback-rule");
            status.setRuleModelAvailable(true);
            status.setCnnModelName("1dcnn-v1");
            status.setCnnModelAvailable(false);
            status.setCnnCheckpointExists(false);
            status.setCnnCheckpointIsFile(false);
            status.setFallbackRisk(false);
            status.setFallbackRiskLevel("LOW");
            status.setFallbackRiskReason("Core 已显式关闭 AI 调用，当前系统固定走规则链路，不存在 CNN->RULE 自动回退风险。");
            status.setSummary("ai.enabled=false，Core 当前不会调用 Flask AI，系统固定走 RULE。");
            if (writeLog) {
                log.warn("AI 健康检查：{}", status.getSummary());
            }
            return status;
        }

        try {
            ResponseEntity<ApiResponse<Map<String, Object>>> response = restTemplate.exchange(
                    buildHealthUrl(),
                    HttpMethod.GET,
                    null,
                    new ParameterizedTypeReference<ApiResponse<Map<String, Object>>>() {
                    }
            );

            ApiResponse<Map<String, Object>> body = response.getBody();
            if (body == null || body.getCode() == null || body.getCode() != 200 || body.getData() == null) {
                status.setService("radio-spectrum-ai");
                status.setServiceStatus("DOWN");
                status.setServiceUp(false);
                status.setFallbackRisk(true);
                status.setFallbackRiskLevel("HIGH");
                status.setFallbackRiskReason("Core 无法从 Flask /health 获取有效响应，当前无法确认 CNN 状态，存在 RULE 兜底风险。");
                status.setSummary("Flask /health 返回无效，Core 无法确认 CNN 是否可用。");
                if (writeLog) {
                    log.warn("AI 健康检查失败：{}", status.getSummary());
                }
                return status;
            }

            fillFromHealthPayload(status, body.getData());

            if (writeLog) {
                if (Boolean.TRUE.equals(status.getFallbackRisk())) {
                    log.warn("AI 健康检查：{}", status.getSummary());
                } else {
                    log.info("AI 健康检查：{}", status.getSummary());
                }
            }

            return status;
        } catch (Exception e) {
            status.setService("radio-spectrum-ai");
            status.setServiceStatus("DOWN");
            status.setServiceUp(false);
            status.setFallbackRisk(true);
            status.setFallbackRiskLevel("HIGH");
            status.setFallbackRiskReason("Core 调用 Flask /health 失败：" + e.getMessage());
            status.setSummary("Flask /health 不可达，当前无法确认 CNN 是否可用，存在 RULE 兜底风险。");
            if (writeLog) {
                log.warn("AI 健康检查异常，url={}, error={}", buildHealthUrl(), e.getMessage());
            }
            return status;
        }
    }

    private void fillFromHealthPayload(AiHealthStatusVO status, Map<String, Object> data) {
        status.setService(asString(data.get("service"), "radio-spectrum-ai"));
        status.setServiceStatus(asString(data.get("status"), "UP"));
        status.setServiceUp(true);
        status.setDefaultMode(asString(data.get("default_mode"), "auto"));
        status.setAllowRuleFallback(asBoolean(data.get("allow_rule_fallback"), true));

        Map<String, Object> ruleModel = asMap(data.get("rule_model"));
        status.setRuleModelName(asString(ruleModel.get("name"), "rule-model-v1"));
        status.setRuleModelAvailable(asBoolean(ruleModel.get("available"), true));

        Map<String, Object> cnnModel = asMap(data.get("cnn_model"));
        status.setCnnModelName(asString(cnnModel.get("name"), "1dcnn-v1"));
        status.setCnnModelAvailable(asBoolean(cnnModel.get("available"), false));
        status.setCnnCheckpointPath(asString(cnnModel.get("checkpoint_path"), ""));
        status.setCnnCheckpointExists(asBoolean(cnnModel.get("checkpoint_exists"), false));
        status.setCnnCheckpointIsFile(asBoolean(cnnModel.get("checkpoint_is_file"), false));
        status.setCnnDevice(asString(cnnModel.get("device"), ""));
        status.setCnnInputShape(asIntegerList(cnnModel.get("input_shape")));
        status.setCnnError(asString(cnnModel.get("error"), ""));

        Map<String, Object> fallbackRisk = asMap(data.get("fallback_risk"));
        status.setFallbackRisk(asBoolean(fallbackRisk.get("has_risk"), false));
        status.setFallbackRiskLevel(asString(fallbackRisk.get("level"), "LOW"));
        status.setFallbackRiskReason(asString(fallbackRisk.get("reason"), ""));

        String summary = asString(data.get("summary"), "");
        if (summary.isBlank()) {
            summary = String.format(
                    "status=%s, defaultMode=%s, cnnAvailable=%s, checkpointExists=%s, fallbackRisk=%s",
                    status.getServiceStatus(),
                    status.getDefaultMode(),
                    boolText(status.getCnnModelAvailable()),
                    boolText(status.getCnnCheckpointExists()),
                    boolText(status.getFallbackRisk())
            );
        }
        status.setSummary(summary);
    }

    private String buildHealthUrl() {
        return aiBaseUrl + aiHealthPath;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object obj) {
        if (obj instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String asString(Object obj, String defaultValue) {
        return obj == null ? defaultValue : String.valueOf(obj);
    }

    private Boolean asBoolean(Object obj, boolean defaultValue) {
        if (obj == null) {
            return defaultValue;
        }
        if (obj instanceof Boolean b) {
            return b;
        }
        return Boolean.parseBoolean(String.valueOf(obj));
    }

    private List<Integer> asIntegerList(Object obj) {
        List<Integer> result = new ArrayList<>();
        if (obj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                try {
                    result.add(Integer.parseInt(String.valueOf(item)));
                } catch (Exception ignored) {
                }
            }
        }
        return result;
    }

    private String boolText(Boolean value) {
        return Boolean.TRUE.equals(value) ? "true" : "false";
    }
}
