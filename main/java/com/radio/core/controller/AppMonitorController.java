package com.radio.core.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/core/app/monitor")
public class AppMonitorController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @GetMapping("/stations")
    public Map<String, Object> stationList() {
        try {
            List<String> stationCols = tableColumns("station");
            if (stationCols.isEmpty()) {
                return buildResponse(500, "station 表不存在或无法读取", null);
            }

            String nameCol = pickColumn(stationCols, "station_name", "name", "stationname");
            String latCol = pickColumn(stationCols, "latitude", "lat");
            String lngCol = pickColumn(stationCols, "longitude", "lng", "lon");
            String statusCol = pickColumn(stationCols, "online_status", "station_status", "status");

            StringBuilder sql = new StringBuilder("SELECT id");
            if (nameCol != null) sql.append(", ").append(nameCol).append(" AS stationName");
            if (latCol != null) sql.append(", ").append(latCol).append(" AS latitude");
            if (lngCol != null) sql.append(", ").append(lngCol).append(" AS longitude");
            if (statusCol != null) sql.append(", ").append(statusCol).append(" AS onlineStatus");
            sql.append(" FROM station ORDER BY id ASC");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString());
            for (Map<String, Object> row : rows) {
                if (!row.containsKey("stationName")) {
                    row.put("stationName", "站点" + row.get("id"));
                }
                if (!row.containsKey("onlineStatus")) {
                    row.put("onlineStatus", 1);
                }
            }

            return buildResponse(200, "success", rows);
        } catch (Exception e) {
            return buildResponse(500, "站点列表加载失败：" + e.getMessage(), null);
        }
    }

    @GetMapping("/latest")
    public Map<String, Object> latest(@RequestParam Long stationId) {
        if (stationId == null) {
            return buildResponse(500, "stationId 不能为空", null);
        }

        try {
            List<String> snapshotCols = tableColumns("spectrum_snapshot");
            if (snapshotCols.isEmpty()) {
                return buildResponse(500, "spectrum_snapshot 表不存在或无法读取", null);
            }

            String stationIdCol = pickColumn(snapshotCols, "station_id", "stationid");
            String deviceIdCol = pickColumn(snapshotCols, "device_id", "deviceid");
            String timeCol = pickColumn(snapshotCols, "snapshot_time", "collect_time", "create_time", "update_time");
            String pointsCol = pickColumn(snapshotCols,
                    "spectrum_points",
                    "spectrum_data",
                    "fft_points",
                    "points_json",
                    "spectrum_json",
                    "point_values",
                    "power_points"
            );
            String centerCol = pickColumn(snapshotCols,
                    "center_freq",
                    "center_frequency",
                    "center_freq_mhz",
                    "frequency",
                    "freq"
            );
            String bandwidthCol = pickColumn(snapshotCols,
                    "bandwidth",
                    "band_width",
                    "bandwidth_khz"
            );
            String powerCol = pickColumn(snapshotCols,
                    "power_level",
                    "power_dbm",
                    "peak_power",
                    "signal_power"
            );
            String aiCol = pickColumn(snapshotCols,
                    "ai_result",
                    "predict_result",
                    "predict_label",
                    "signal_type",
                    "modulation_type",
                    "identify_result"
            );

            if (stationIdCol == null) {
                return buildResponse(500, "spectrum_snapshot 表中未找到站点字段", null);
            }

            StringBuilder selectSql = new StringBuilder("SELECT id");
            if (stationIdCol != null) selectSql.append(", ").append(stationIdCol).append(" AS stationId");
            if (deviceIdCol != null) selectSql.append(", ").append(deviceIdCol).append(" AS deviceId");
            if (timeCol != null) selectSql.append(", ").append(timeCol).append(" AS snapshotTime");
            if (pointsCol != null) selectSql.append(", ").append(pointsCol).append(" AS rawPoints");
            if (centerCol != null) selectSql.append(", ").append(centerCol).append(" AS centerFreq");
            if (bandwidthCol != null) selectSql.append(", ").append(bandwidthCol).append(" AS bandwidth");
            if (powerCol != null) selectSql.append(", ").append(powerCol).append(" AS powerLevel");
            if (aiCol != null) selectSql.append(", ").append(aiCol).append(" AS aiResult");

            selectSql.append(" FROM spectrum_snapshot WHERE ").append(stationIdCol).append(" = ? ");

            if (timeCol != null) {
                selectSql.append(" ORDER BY ").append(timeCol).append(" DESC ");
            } else {
                selectSql.append(" ORDER BY id DESC ");
            }
            selectSql.append(" LIMIT 1");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(selectSql.toString(), stationId);
            if (rows.isEmpty()) {
                return buildResponse(500, "当前站点暂无监测数据", null);
            }

            Map<String, Object> row = rows.get(0);

            Double centerFreq = toDouble(row.get("centerFreq"), 100.0);
            Double bandwidth = toDouble(row.get("bandwidth"), 0.2);
            Double powerLevel = toDouble(row.get("powerLevel"), -55.0);
            String snapshotTime = stringValue(row.get("snapshotTime"), "");

            List<Map<String, Object>> points = parseSpectrumPoints(
                    row.get("rawPoints"),
                    centerFreq,
                    bandwidth,
                    powerLevel
            );

            boolean generatedFallback = row.get("rawPoints") == null || points.isEmpty();
            if (generatedFallback) {
                points = buildDynamicSyntheticPoints(centerFreq, bandwidth, powerLevel, snapshotTime);
            }

            Map<String, Object> peak = findPeak(points);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("stationId", stationId);
            data.put("stationName", queryStationName(stationId));
            data.put("deviceId", row.get("deviceId"));
            data.put("deviceName", queryDeviceName(row.get("deviceId")));
            data.put("aiResult", stringValue(row.get("aiResult"), "未知"));
            data.put("centerFreq", centerFreq);
            data.put("bandwidth", bandwidth);
            data.put("powerLevel", powerLevel);
            data.put("snapshotTime", snapshotTime);
            data.put("dataSource", generatedFallback ? "动态拟真曲线" : "真实点位");
            data.put("pointCount", points.size());
            data.put("peakFreq", peak.get("freq"));
            data.put("peakPower", peak.get("power"));
            data.put("points", points);

            return buildResponse(200, "success", data);
        } catch (Exception e) {
            return buildResponse(500, "最新监测加载失败：" + e.getMessage(), null);
        }
    }

    private List<String> tableColumns(String tableName) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW COLUMNS FROM " + tableName);
        List<String> cols = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Object field = row.get("Field");
            if (field != null) {
                cols.add(String.valueOf(field).toLowerCase(Locale.ROOT));
            }
        }
        return cols;
    }

    private String pickColumn(List<String> cols, String... candidates) {
        for (String candidate : candidates) {
            String lower = candidate.toLowerCase(Locale.ROOT);
            for (String col : cols) {
                if (col.equals(lower)) {
                    return col;
                }
            }
        }
        return null;
    }

    private String queryStationName(Long stationId) {
        if (stationId == null) return "";
        try {
            List<String> stationCols = tableColumns("station");
            String nameCol = pickColumn(stationCols, "station_name", "name", "stationname");
            if (nameCol == null) return "站点" + stationId;
            String sql = "SELECT " + nameCol + " FROM station WHERE id = ? LIMIT 1";
            Object val = jdbcTemplate.queryForObject(sql, Object.class, stationId);
            return stringValue(val, "站点" + stationId);
        } catch (Exception e) {
            return "站点" + stationId;
        }
    }

    private String queryDeviceName(Object deviceId) {
        if (deviceId == null) return "";
        try {
            Long id = Long.parseLong(String.valueOf(deviceId));
            List<String> deviceCols = tableColumns("device");
            String nameCol = pickColumn(deviceCols, "device_name", "name", "devicename");
            if (nameCol == null) return "设备" + id;
            String sql = "SELECT " + nameCol + " FROM device WHERE id = ? LIMIT 1";
            Object val = jdbcTemplate.queryForObject(sql, Object.class, id);
            return stringValue(val, "设备" + id);
        } catch (Exception e) {
            return "设备";
        }
    }

    private Double toDouble(Object obj, Double defaultValue) {
        if (obj == null) return defaultValue;
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String stringValue(Object obj, String defaultValue) {
        if (obj == null) return defaultValue;
        String str = String.valueOf(obj).trim();
        return StringUtils.hasText(str) ? str : defaultValue;
    }

    private List<Map<String, Object>> parseSpectrumPoints(
            Object rawPoints,
            Double centerFreq,
            Double bandwidth,
            Double powerLevel
    ) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (rawPoints == null) {
            return result;
        }

        String text = String.valueOf(rawPoints).trim();
        if (!StringUtils.hasText(text)) {
            return result;
        }

        try {
            if (text.startsWith("[")) {
                JsonNode root = objectMapper.readTree(text);

                if (root.isArray() && root.size() > 0) {
                    JsonNode first = root.get(0);

                    if (first.isNumber()) {
                        List<Double> values = new ArrayList<>();
                        for (JsonNode node : root) {
                            values.add(node.asDouble());
                        }
                        return buildPointsFromPowerArray(values, centerFreq, bandwidth);
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
                }
            }

            if (text.contains(",")) {
                String[] arr = text.split(",");
                List<Double> values = new ArrayList<>();
                for (String item : arr) {
                    if (StringUtils.hasText(item)) {
                        values.add(Double.parseDouble(item.trim()));
                    }
                }
                if (!values.isEmpty()) {
                    return buildPointsFromPowerArray(values, centerFreq, bandwidth);
                }
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

    private List<Map<String, Object>> buildPointsFromPowerArray(List<Double> values, Double centerFreq, Double bandwidth) {
        List<Map<String, Object>> points = new ArrayList<>();
        if (values == null || values.isEmpty()) {
            return points;
        }

        double bw = bandwidth == null || bandwidth <= 0 ? 0.2 : bandwidth;
        double start = centerFreq - bw / 2.0;
        double step = bw / Math.max(values.size() - 1, 1);

        for (int i = 0; i < values.size(); i++) {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("freq", round(start + i * step, 4));
            point.put("power", round(values.get(i), 2));
            points.add(point);
        }
        return points;
    }

    private List<Map<String, Object>> buildDynamicSyntheticPoints(
            Double centerFreq,
            Double bandwidth,
            Double powerLevel,
            String snapshotTime
    ) {
        List<Map<String, Object>> points = new ArrayList<>();

        double cf = centerFreq == null ? 100.0 : centerFreq;
        double bw = bandwidth == null || bandwidth <= 0 ? 0.2 : bandwidth;
        double basePower = powerLevel == null ? -55.0 : powerLevel;

        long seed = parseSeed(snapshotTime);
        double phase = (seed % 3600) / 3600.0 * Math.PI * 2;
        double drift = ((seed % 17) - 8) / 220.0;
        double sideShift = ((seed % 29) - 14) / 300.0;
        double start = cf - bw / 2.0;
        double step = bw / 79.0;

        for (int i = 0; i < 80; i++) {
            double freq = start + i * step;
            double x = (i - 40) / 9.5;

            double mainPeak = 17.5 * Math.exp(-Math.pow(x - drift * 4, 2) / 1.7);
            double sidePeak1 = 6.8 * Math.exp(-Math.pow(x + 1.9 + sideShift * 2, 2) / 0.55);
            double sidePeak2 = 4.6 * Math.exp(-Math.pow(x - 2.15 - sideShift * 1.5, 2) / 0.85);
            double ripple = Math.sin(i / 3.2 + phase) * 1.35 + Math.cos(i / 6.5 + phase * 0.7) * 0.9;
            double envelope = Math.sin((i / 79.0) * Math.PI * 2 + phase * 0.35) * 0.65;

            double power = basePower - 15.0 + mainPeak + sidePeak1 + sidePeak2 + ripple + envelope;

            Map<String, Object> point = new LinkedHashMap<>();
            point.put("freq", round(freq, 4));
            point.put("power", round(power, 2));
            points.add(point);
        }

        return points;
    }

    private long parseSeed(String snapshotTime) {
        if (!StringUtils.hasText(snapshotTime)) {
            return System.currentTimeMillis() / 1000;
        }

        try {
            LocalDateTime time = LocalDateTime.parse(snapshotTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            return time.atZone(ZoneId.systemDefault()).toEpochSecond();
        } catch (Exception e) {
            return Math.abs(snapshotTime.hashCode());
        }
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

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }

    private Map<String, Object> buildResponse(int code, String msg, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", msg);
        result.put("data", data);
        return result;
    }
}