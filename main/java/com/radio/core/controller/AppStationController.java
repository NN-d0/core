package com.radio.core.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/core/app/station/status")
public class AppStationController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    public Map<String, Object> list() {
        try {
            List<String> stationCols = tableColumns("station");
            if (stationCols.isEmpty()) {
                return buildResponse(500, "station 表不存在或无法读取", null);
            }

            String stationNameCol = pickColumn(stationCols, "station_name", "name", "stationname");
            String latCol = pickColumn(stationCols, "latitude", "lat");
            String lngCol = pickColumn(stationCols, "longitude", "lng", "lon");
            String stationStatusCol = pickColumn(stationCols, "online_status", "station_status", "status");

            StringBuilder sql = new StringBuilder("SELECT id");
            if (stationNameCol != null) sql.append(", ").append(stationNameCol).append(" AS stationName");
            if (latCol != null) sql.append(", ").append(latCol).append(" AS latitude");
            if (lngCol != null) sql.append(", ").append(lngCol).append(" AS longitude");
            if (stationStatusCol != null) sql.append(", ").append(stationStatusCol).append(" AS onlineStatus");
            sql.append(" FROM station ORDER BY id ASC");

            List<Map<String, Object>> stations = jdbcTemplate.queryForList(sql.toString());

            List<String> deviceCols = tableColumns("device");
            String deviceStationIdCol = pickColumn(deviceCols, "station_id", "stationid");
            String deviceStatusCol = pickColumn(deviceCols, "device_status", "status", "run_status");

            List<String> snapshotCols = tableColumns("spectrum_snapshot");
            String snapshotStationIdCol = pickColumn(snapshotCols, "station_id", "stationid");
            String snapshotTimeCol = pickColumn(snapshotCols, "snapshot_time", "collect_time", "create_time", "update_time");

            int index = 0;
            for (Map<String, Object> station : stations) {
                Long stationId = toLong(station.get("id"), 0L);

                if (station.get("stationName") == null || String.valueOf(station.get("stationName")).trim().isEmpty()) {
                    station.put("stationName", "站点" + stationId);
                }

                if (station.get("onlineStatus") == null) {
                    station.put("onlineStatus", 1);
                }

                // 如果经纬度为空，则给一个默认展示坐标，保证 APP 地图页最小可演示
                if (station.get("latitude") == null || station.get("longitude") == null) {
                    double baseLat = 22.53;
                    double baseLng = 113.93;
                    station.put("latitude", round(baseLat + index * 0.015, 6));
                    station.put("longitude", round(baseLng + index * 0.02, 6));
                }

                long deviceCount = 0;
                long runningDeviceCount = 0;

                if (deviceStationIdCol != null) {
                    String countSql = "SELECT COUNT(*) FROM device WHERE " + deviceStationIdCol + " = ?";
                    Long count = jdbcTemplate.queryForObject(countSql, Long.class, stationId);
                    deviceCount = count == null ? 0 : count;

                    if (deviceStatusCol != null) {
                        String runningSql = "SELECT COUNT(*) FROM device WHERE " + deviceStationIdCol + " = ? AND " + deviceStatusCol + " = 1";
                        Long runningCount = jdbcTemplate.queryForObject(runningSql, Long.class, stationId);
                        runningDeviceCount = runningCount == null ? 0 : runningCount;
                    }
                }

                String latestSnapshotTime = "";
                if (snapshotStationIdCol != null && snapshotTimeCol != null) {
                    try {
                        String latestSql = "SELECT DATE_FORMAT(MAX(" + snapshotTimeCol + "), '%Y-%m-%d %H:%i:%s') " +
                                "FROM spectrum_snapshot WHERE " + snapshotStationIdCol + " = ?";
                        String latest = jdbcTemplate.queryForObject(latestSql, String.class, stationId);
                        latestSnapshotTime = latest == null ? "" : latest;
                    } catch (Exception ignored) {
                    }
                }

                station.put("deviceCount", deviceCount);
                station.put("runningDeviceCount", runningDeviceCount);
                station.put("latestSnapshotTime", latestSnapshotTime);

                index++;
            }

            return buildResponse(200, "success", stations);
        } catch (Exception e) {
            return buildResponse(500, "站点状态加载失败：" + e.getMessage(), null);
        }
    }

    private List<String> tableColumns(String tableName) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList("SHOW COLUMNS FROM " + tableName);
            List<String> cols = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                Object field = row.get("Field");
                if (field != null) {
                    cols.add(String.valueOf(field).toLowerCase(Locale.ROOT));
                }
            }
            return cols;
        } catch (Exception e) {
            return new ArrayList<>();
        }
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

    private Long toLong(Object obj, Long defaultValue) {
        if (obj == null) return defaultValue;
        try {
            return Long.parseLong(String.valueOf(obj));
        } catch (Exception e) {
            return defaultValue;
        }
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