package com.radio.core.service;

import com.radio.core.common.ApiResponse;
import com.radio.core.dto.AiPredictRequest;
import com.radio.core.dto.CollectReportRequest;
import com.radio.core.vo.AiPredictResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * AI 推理服务
 *
 * 职责：
 * 1. Core 统一调用 Flask /predict
 * 2. 把 AI 返回结果转换成系统内部对象
 * 3. 当 AI 服务不可用时，自动兜底，保证系统闭环不断
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

    /**
     * 调用 AI 预测
     */
    public AiPredictResultVO predict(CollectReportRequest request) {
        if (!aiEnabled) {
            log.warn("AI 服务已禁用，使用 Core 本地兜底规则。");
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
                log.warn("AI 返回为空，改用 Core 本地兜底规则。");
                return buildFallbackResult(request, "AI 返回为空，使用 Core 本地兜底规则。");
            }

            if (body.getCode() == null || body.getCode() != 200 || body.getData() == null) {
                log.warn("AI 返回非成功结果，body={}", body);
                return buildFallbackResult(request, "AI 返回非成功结果，使用 Core 本地兜底规则。");
            }

            AiPredictResultVO result = body.getData();

            // 补齐兜底字段，避免空值影响后续入库
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

            log.info("AI 推理成功，label={}, confidence={}, riskLevel={}, shouldAlarm={}, model={}",
                    result.getPredictedLabel(),
                    result.getConfidence(),
                    result.getRiskLevel(),
                    result.getShouldAlarm(),
                    result.getModelName());

            return result;
        } catch (Exception e) {
            log.warn("AI 调用失败，改用 Core 本地兜底规则。error={}", e.getMessage());
            return buildFallbackResult(request, "AI 调用失败，使用 Core 本地兜底规则：" + e.getMessage());
        }
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
        return aiRequest;
    }

    /**
     * AI 不可用时的最小兜底规则
     *
     * 说明：
     * - 这里只是“保系统不断”
     * - 真正的主要识别逻辑应以 Flask AI 为准
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
        return result;
    }
}