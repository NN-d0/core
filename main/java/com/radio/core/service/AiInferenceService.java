package com.radio.core.service;

import com.radio.core.common.ApiResponse;
import com.radio.core.dto.AiPredictRequest;
import com.radio.core.dto.CollectReportRequest;
import com.radio.core.vo.AiPredictResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

/**
 * AI 推理服务
 *
 * 职责：
 * 1. Core 统一调用 Flask /predict
 * 2. 把 CollectReportRequest 转换成 AI 请求对象
 * 3. 支持 model_type / i_points / q_points 透传
 * 4. 当 AI 服务不可用时自动兜底，保证闭环不断
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiInferenceService {

    private static final BigDecimal DEFAULT_POWER_THRESHOLD = new BigDecimal("-30");
    private static final BigDecimal DEFAULT_SNR_THRESHOLD = new BigDecimal("10");

    private final RestTemplate restTemplate;

    @Value("${ai.enabled:true}")
    private boolean aiEnabled;

    @Value("${ai.base-url:http://127.0.0.1:9300}")
    private String aiBaseUrl;

    @Value("${ai.predict-path:/predict}")
    private String aiPredictPath;

    @Value("${ai.default-model-type:cnn}")
    private String defaultModelType;

    /**
     * 调用 AI 预测
     */
    public AiPredictResultVO predict(CollectReportRequest request) {
        if (!aiEnabled) {
            log.warn("AI 服务已禁用，使用 Core 本地兜底规则。stationId={}, deviceId={}, taskId={}",
                    request.getStationId(), request.getDeviceId(), request.getTaskId());
            return buildFallbackResult(request, "AI 服务已禁用，使用 Core 本地兜底规则。");
        }

        try {
            String url = aiBaseUrl + aiPredictPath;
            AiPredictRequest aiRequest = buildAiRequest(request);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<AiPredictRequest> httpEntity = new HttpEntity<>(aiRequest, headers);

            ResponseEntity<ApiResponse<AiPredictResultVO>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    httpEntity,
                    new ParameterizedTypeReference<ApiResponse<AiPredictResultVO>>() {
                    }
            );

            ApiResponse<AiPredictResultVO> body = response.getBody();
            if (body == null) {
                log.warn("AI 返回为空，改用 Core 本地兜底规则。stationId={}, deviceId={}, taskId={}",
                        request.getStationId(), request.getDeviceId(), request.getTaskId());
                return buildFallbackResult(request, "AI 返回为空，使用 Core 本地兜底规则。");
            }

            if (body.getCode() == null || body.getCode() != 200 || body.getData() == null) {
                log.warn("AI 返回非成功结果，stationId={}, deviceId={}, taskId={}, body={}",
                        request.getStationId(), request.getDeviceId(), request.getTaskId(), body);
                return buildFallbackResult(request, "AI 返回非成功结果，使用 Core 本地兜底规则。");
            }

            AiPredictResultVO result = body.getData();
            fillDefaultFields(result, request);
            logPredictSummary(request, result);
            return result;
        } catch (Exception e) {
            log.warn("AI 调用失败，改用 Core 本地兜底规则。stationId={}, deviceId={}, taskId={}, error={}",
                    request.getStationId(), request.getDeviceId(), request.getTaskId(), e.getMessage());
            return buildFallbackResult(request, "AI 调用失败，使用 Core 本地兜底规则：" + e.getMessage());
        }
    }

    private void fillDefaultFields(AiPredictResultVO result, CollectReportRequest request) {
        if (result.getPredictedLabel() == null || result.getPredictedLabel().isBlank()) {
            result.setPredictedLabel(request.getSignalType());
        }
        if (result.getConfidence() == null) {
            result.setConfidence(0.60D);
        }
        if (result.getRiskLevel() == null || result.getRiskLevel().isBlank()) {
            result.setRiskLevel("LOW");
        }
        if (result.getShouldAlarm() == null) {
            result.setShouldAlarm(false);
        }
        if (result.getReason() == null || result.getReason().isBlank()) {
            result.setReason("AI 已返回结果，但未提供解释文本。");
        }
        if (result.getModelName() == null || result.getModelName().isBlank()) {
            result.setModelName("unknown-model");
        }
        if (result.getRequestMode() == null || result.getRequestMode().isBlank()) {
            result.setRequestMode(resolveModelType(request));
        }
        if (result.getInferenceMode() == null || result.getInferenceMode().isBlank()) {
            result.setInferenceMode(Boolean.TRUE.equals(result.getFallbackUsed()) ? "rule" : result.getRequestMode());
        }
        if (result.getFallbackUsed() == null) {
            result.setFallbackUsed(false);
        }
    }

    private void logPredictSummary(CollectReportRequest request, AiPredictResultVO result) {
        String requestMode = safeMode(result.getRequestMode(), resolveModelType(request));
        String actualMode = safeMode(result.getInferenceMode(), requestMode);
        boolean fallbackUsed = Boolean.TRUE.equals(result.getFallbackUsed());

        if (fallbackUsed || !requestMode.equals(actualMode)) {
            log.warn(
                    "AI 推理发生模式偏移。stationId={}, deviceId={}, taskId={}, requestMode={}, actualMode={}, fallbackUsed={}, label={}, confidence={}, riskLevel={}, shouldAlarm={}, model={}, reason={}",
                    request.getStationId(),
                    request.getDeviceId(),
                    request.getTaskId(),
                    requestMode,
                    actualMode,
                    fallbackUsed,
                    result.getPredictedLabel(),
                    result.getConfidence(),
                    result.getRiskLevel(),
                    result.getShouldAlarm(),
                    result.getModelName(),
                    result.getReason()
            );
            return;
        }

        log.info(
                "AI 推理成功。stationId={}, deviceId={}, taskId={}, requestMode={}, actualMode={}, fallbackUsed={}, label={}, confidence={}, riskLevel={}, shouldAlarm={}, model={}",
                request.getStationId(),
                request.getDeviceId(),
                request.getTaskId(),
                requestMode,
                actualMode,
                fallbackUsed,
                result.getPredictedLabel(),
                result.getConfidence(),
                result.getRiskLevel(),
                result.getShouldAlarm(),
                result.getModelName()
        );
    }

    private String safeMode(String mode, String defaultValue) {
        if (mode == null || mode.isBlank()) {
            return defaultValue == null || defaultValue.isBlank() ? "rule" : defaultValue.trim().toLowerCase();
        }
        return mode.trim().toLowerCase();
    }

    private AiPredictRequest buildAiRequest(CollectReportRequest request) {
        AiPredictRequest aiRequest = new AiPredictRequest();
        aiRequest.setCenterFreqMhz(request.getCenterFreqMhz());
        aiRequest.setBandwidthKhz(request.getBandwidthKhz());
        aiRequest.setPeakPowerDbm(request.getPeakPowerDbm());
        aiRequest.setSnrDb(request.getSnrDb());
        aiRequest.setOccupiedBandwidthKhz(request.getOccupiedBandwidthKhz());
        aiRequest.setChannelModel(request.getChannelModel());
        aiRequest.setPowerPoints(request.getPowerPoints());
        aiRequest.setModelType(resolveModelType(request));

        if (hasValidIq(request.getIPoints(), request.getQPoints())) {
            aiRequest.setIPoints(request.getIPoints());
            aiRequest.setQPoints(request.getQPoints());
        }

        return aiRequest;
    }

    private String resolveModelType(CollectReportRequest request) {
        if (request != null && request.getModelType() != null && !request.getModelType().isBlank()) {
            return request.getModelType().trim().toLowerCase();
        }
        return defaultModelType == null || defaultModelType.isBlank()
                ? "cnn"
                : defaultModelType.trim().toLowerCase();
    }

    private boolean hasValidIq(List<BigDecimal> iPoints, List<BigDecimal> qPoints) {
        return iPoints != null
                && qPoints != null
                && !iPoints.isEmpty()
                && !qPoints.isEmpty()
                && iPoints.size() == qPoints.size();
    }

    /**
     * AI 不可用时的最小兜底规则
     */
    private AiPredictResultVO buildFallbackResult(CollectReportRequest request, String reason) {
        boolean highPower = request.getPeakPowerDbm() != null
                && request.getPeakPowerDbm().compareTo(DEFAULT_POWER_THRESHOLD) >= 0;

        boolean lowSnr = request.getSnrDb() != null
                && request.getSnrDb().compareTo(DEFAULT_SNR_THRESHOLD) <= 0;

        boolean shouldAlarm = highPower || lowSnr;

        String riskLevel;
        if (highPower || (request.getSnrDb() != null && request.getSnrDb().compareTo(new BigDecimal("7")) <= 0)) {
            riskLevel = "HIGH";
        } else if (shouldAlarm) {
            riskLevel = "MEDIUM";
        } else {
            riskLevel = "LOW";
        }

        AiPredictResultVO result = new AiPredictResultVO();
        result.setPredictedLabel(request.getSignalType());
        result.setConfidence(0.60D);
        result.setRiskLevel(riskLevel);
        result.setShouldAlarm(shouldAlarm);
        result.setReason(reason);
        result.setModelName("core-fallback-rule");
        result.setRequestMode(resolveModelType(request));
        result.setInferenceMode("rule");
        result.setFallbackUsed(true);
        return result;
    }
}
