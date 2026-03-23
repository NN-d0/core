package com.radio.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radio.core.common.ApiResponse;
import com.radio.core.entity.Station;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.RealtimeSpectrumVO;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APP 端监测接口
 *
 * 本次收口目标：
 * 1. 不再在 Controller 中直接使用 JdbcTemplate
 * 2. 站点列表复用 CoreQueryService.listStations(...)
 * 3. 最新监测复用 CoreQueryService.getLatestRealtimeSnapshot(...)
 * 4. 保持 APP 端返回字段结构尽量不变
 */
@RestController
@RequestMapping("/api/core/app/monitor")
@RequiredArgsConstructor
public class AppMonitorController {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CoreQueryService coreQueryService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/stations")
    public ApiResponse<List<Map<String, Object>>> stationList() {
        List<Station> stationList = coreQueryService.listStations(null);
        List<Map<String, Object>> rows = new ArrayList<>();

        int index = 0;
        for (Station station : stationList) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", station.getId());
            row.put("stationName", StringUtils.hasText(station.getStationName()) ? station.getStationName() : ("站点" + station.getId()));
            row.put("latitude", station.getLatitude() != null ? station.getLatitude() : defaultLatitude(index));
            row.put("longitude", station.getLongitude() != null ? station.getLongitude() : defaultLongitude(index));
            row.put("onlineStatus", station.getOnlineStatus() == null ? 1 : station.getOnlineStatus());
            rows.add(row);
            index++;
        }

        return ApiResponse.success(rows);
    }

    @GetMapping("/latest")
    public ApiResponse<Map<String, Object>> latest(@RequestParam Long stationId) {
        if (stationId == null) {
            return ApiResponse.fail(500, "stationId 不能为空");
        }

        RealtimeSpectrumVO vo = coreQueryService.getLatestRealtimeSnapshot(stationId);
        if (vo == null) {
            return ApiResponse.fail(500, "当前站点暂无监测数据");
        }

        Double centerFreq = toDouble(vo.getCenterFreqMhz(), 100.0);
        Double bandwidthKhz = toDouble(vo.getBandwidthKhz(), 200.0);
        Double bandwidthMhz = bandwidthKhz / 1000.0;
        Double powerLevel = toDouble(vo.getPeakPowerDbm(), -55.0);

        List<Map<String, Object>> points = parseSpectrumPoints(
                vo.getPowerPointsJson(),
                centerFreq,
                bandwidthMhz
        );

        boolean generatedFallback = points.isEmpty();
        if (generatedFallback) {
            points = buildFallbackPoints(centerFreq, bandwidthMhz, powerLevel);
        }

        Map<String, Object> peak = findPeak(points);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stationId", vo.getStationId());
        data.put("stationName", safeText(vo.getStationName(), "站点" + vo.getStationId()));
        data.put("deviceId", vo.getDeviceId());
        data.put("deviceName", safeText(vo.getDeviceName(), "设备" + vo.getDeviceId()));
        data.put("aiResult", safeText(vo.getAiLabel(), "未知"));
        data.put("centerFreq", centerFreq);
        data.put("bandwidth", bandwidthMhz);
        data.put("powerLevel", powerLevel);
        data.put("snapshotTime", vo.getCaptureTime() == null ? "" : vo.getCaptureTime().format(DATETIME_FORMATTER));
        data.put("dataSource", generatedFallback ? "拟真补全曲线" : "真实点位");
        data.put("pointCount", points.size());
        data.put("peakFreq", peak.get("freq"));
        data.put("peakPower", peak.get("power"));
        data.put("points", points);

        return ApiResponse.success(data);
    }

    private List<Map<String, Object>> parseSpectrumPoints(String rawPoints, Double centerFreq, Double bandwidthMhz) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (!StringUtils.hasText(rawPoints)) {
            return result;
        }

        try {
            JsonNode root = objectMapper.readTree(rawPoints);
            if (!root.isArray() || root.isEmpty()) {
                return result;
            }

            JsonNode first = root.get(0);

            if (first.isNumber()) {
                List<Double> values = new ArrayList<>();
                for (JsonNode node : root) {
                    values.add(node.asDouble());
                }
                return buildPointsFromPowerArray(values, centerFreq, bandwidthMhz);
            }

            if (first.isObject()) {
                for (JsonNode node : root) {
                    double freq = pickJsonDouble(node, "freq", "x", "frequency");
                    double power = pickJsonDouble(node, "power", "y", "value");
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("freq", round(freq, 4));
                    point.put("power", round(power, 2));
                    result.add(point);
                }
                return result;
            }

            if (first.isArray() && first.size() >= 2) {
                for (JsonNode node : root) {
                    Map<String, Object> point = new LinkedHashMap<>();
                    point.put("freq", round(node.get(0).asDouble(), 4));
                    point.put("power", round(node.get(1).asDouble(), 2));
                    result.add(point);
                }
                return result;
            }
        } catch (Exception ignored) {
        }

        return result;
    }

    private double pickJsonDouble(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            JsonNode value = node.get(field);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }
        return 0.0;
    }

    private List<Map<String, Object>> buildPointsFromPowerArray(List<Double> values, Double centerFreq, Double bandwidthMhz) {
        List<Map<String, Object>> points = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return points;
        }

        double cf = centerFreq == null ? 100.0 : centerFreq;
        double bw = bandwidthMhz == null || bandwidthMhz <= 0 ? 0.2 : bandwidthMhz;
        double start = cf - bw / 2.0;
        double step = bw / Math.max(values.size() - 1, 1);

        for (int i = 0; i < values.size(); i++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("freq", round(start + i * step, 4));
            point.put("power", round(values.get(i), 2));
            points.add(point);
        }

        return points;
    }

    private List<Map<String, Object>> buildFallbackPoints(Double centerFreq, Double bandwidthMhz, Double powerLevel) {
        List<Map<String, Object>> points = new ArrayList<>();
        double cf = centerFreq == null ? 100.0 : centerFreq;
        double bw = bandwidthMhz == null || bandwidthMhz <= 0 ? 0.2 : bandwidthMhz;
        double basePower = powerLevel == null ? -55.0 : powerLevel;

        double start = cf - bw / 2.0;
        double step = bw / 79.0;

        for (int i = 0; i < 80; i++) {
            double freq = start + i * step;
            double x = (i - 40) / 9.5;

            double mainPeak = 16.5 * Math.exp(-Math.pow(x, 2) / 1.8);
            double sidePeak1 = 5.8 * Math.exp(-Math.pow(x + 1.8, 2) / 0.65);
            double sidePeak2 = 4.2 * Math.exp(-Math.pow(x - 2.1, 2) / 0.9);
            double ripple = Math.sin(i / 3.1) * 1.2 + Math.cos(i / 6.3) * 0.85;
            double power = basePower - 14.0 + mainPeak + sidePeak1 + sidePeak2 + ripple;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("freq", round(freq, 4));
            point.put("power", round(power, 2));
            points.add(point);
        }

        return points;
    }

    private Map<String, Object> findPeak(List<Map<String, Object>> points) {
        Map<String, Object> peak = new LinkedHashMap<>();
        peak.put("freq", 0);
        peak.put("power", 0);

        if (points == null || points.isEmpty()) {
            return peak;
        }

        Map<String, Object> maxPoint = points.get(0);
        for (Map<String, Object> point : points) {
            double currentPower = Double.parseDouble(String.valueOf(point.get("power")));
            double maxPower = Double.parseDouble(String.valueOf(maxPoint.get("power")));
            if (currentPower > maxPower) {
                maxPoint = point;
            }
        }

        peak.put("freq", maxPoint.get("freq"));
        peak.put("power", maxPoint.get("power"));
        return peak;
    }

    private Double toDouble(BigDecimal value, Double defaultValue) {
        return value == null ? defaultValue : value.doubleValue();
    }

    private String safeText(String text, String defaultValue) {
        return StringUtils.hasText(text) ? text : defaultValue;
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private BigDecimal defaultLatitude(int index) {
        return BigDecimal.valueOf(round(22.53 + index * 0.015, 6));
    }

    private BigDecimal defaultLongitude(int index) {
        return BigDecimal.valueOf(round(113.93 + index * 0.02, 6));
    }
}