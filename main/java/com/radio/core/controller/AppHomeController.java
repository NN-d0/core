package com.radio.core.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/core/app/home")
public class AppHomeController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        try {
            Map<String, Object> data = new LinkedHashMap<>();

            long stationCount = countTable("station");
            long onlineStationCount = countStationByStatus(1);
            long offlineStationCount = Math.max(stationCount - onlineStationCount, 0);

            long deviceCount = countTable("device");
            long runningDeviceCount = countDeviceByStatus(1);
            long stopDeviceCount = Math.max(deviceCount - runningDeviceCount, 0);

            long alarmCount = countTable("alarm_record");
            long pendingAlarmCount = countAlarmByStatus(0);
            long confirmedAlarmCount = countAlarmByStatus(1);
            long handledAlarmCount = countAlarmByStatus(2);

            data.put("stationCount", stationCount);
            data.put("onlineStationCount", onlineStationCount);
            data.put("offlineStationCount", offlineStationCount);

            data.put("deviceCount", deviceCount);
            data.put("runningDeviceCount", runningDeviceCount);
            data.put("stopDeviceCount", stopDeviceCount);

            data.put("alarmCount", alarmCount);
            data.put("pendingAlarmCount", pendingAlarmCount);
            data.put("confirmedAlarmCount", confirmedAlarmCount);
            data.put("handledAlarmCount", handledAlarmCount);

            return buildResponse(200, "success", data);
        } catch (Exception e) {
            return buildResponse(500, "首页统计加载失败：" + e.getMessage(), null);
        }
    }

    @GetMapping("/latest-alarms")
    public Map<String, Object> latestAlarms(@RequestParam(defaultValue = "5") Integer size) {
        try {
            int pageSize = (size == null || size < 1) ? 5 : Math.min(size, 10);

            List<String> stationCols = tableColumns("station");
            List<String> deviceCols = tableColumns("device");

            String stationNameCol = pickColumn(stationCols, "station_name", "name", "stationname");
            String deviceNameCol = pickColumn(deviceCols, "device_name", "name", "devicename");

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append("a.id, ")
                    .append("a.alarm_no AS alarmNo, ")
                    .append("a.station_id AS stationId, ")
                    .append("a.device_id AS deviceId, ");

            if (stationNameCol != null) {
                sql.append("s.").append(stationNameCol).append(" AS stationName, ");
            } else {
                sql.append("'' AS stationName, ");
            }

            if (deviceNameCol != null) {
                sql.append("d.").append(deviceNameCol).append(" AS deviceName, ");
            } else {
                sql.append("'' AS deviceName, ");
            }

            sql.append("a.alarm_type AS alarmType, ")
                    .append("a.alarm_level AS alarmLevel, ")
                    .append("a.title, ")
                    .append("a.content, ")
                    .append("a.alarm_status AS alarmStatus, ")
                    .append("DATE_FORMAT(a.alarm_time, '%Y-%m-%d %H:%i:%s') AS alarmTime ")
                    .append("FROM alarm_record a ")
                    .append("LEFT JOIN station s ON a.station_id = s.id ")
                    .append("LEFT JOIN device d ON a.device_id = d.id ")
                    .append("ORDER BY a.alarm_time DESC ")
                    .append("LIMIT ").append(pageSize);

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString());
            return buildResponse(200, "success", rows);
        } catch (Exception e) {
            return buildResponse(500, "最新告警加载失败：" + e.getMessage(), null);
        }
    }

    @GetMapping("/alarm/detail")
    public Map<String, Object> alarmDetail(@RequestParam Long id) {
        if (id == null) {
            return buildResponse(500, "id 不能为空", null);
        }

        try {
            List<String> stationCols = tableColumns("station");
            List<String> deviceCols = tableColumns("device");

            String stationNameCol = pickColumn(stationCols, "station_name", "name", "stationname");
            String deviceNameCol = pickColumn(deviceCols, "device_name", "name", "devicename");

            StringBuilder sql = new StringBuilder();
            sql.append("SELECT ")
                    .append("a.id, ")
                    .append("a.alarm_no AS alarmNo, ")
                    .append("a.station_id AS stationId, ")
                    .append("a.device_id AS deviceId, ");

            if (stationNameCol != null) {
                sql.append("s.").append(stationNameCol).append(" AS stationName, ");
            } else {
                sql.append("'' AS stationName, ");
            }

            if (deviceNameCol != null) {
                sql.append("d.").append(deviceNameCol).append(" AS deviceName, ");
            } else {
                sql.append("'' AS deviceName, ");
            }

            sql.append("a.alarm_type AS alarmType, ")
                    .append("a.alarm_level AS alarmLevel, ")
                    .append("a.title, ")
                    .append("a.content, ")
                    .append("a.alarm_status AS alarmStatus, ")
                    .append("a.handle_note AS handleNote, ")
                    .append("DATE_FORMAT(a.alarm_time, '%Y-%m-%d %H:%i:%s') AS alarmTime, ")
                    .append("DATE_FORMAT(a.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirmTime, ")
                    .append("DATE_FORMAT(a.handle_time, '%Y-%m-%d %H:%i:%s') AS handleTime ")
                    .append("FROM alarm_record a ")
                    .append("LEFT JOIN station s ON a.station_id = s.id ")
                    .append("LEFT JOIN device d ON a.device_id = d.id ")
                    .append("WHERE a.id = ? LIMIT 1");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), id);
            if (rows.isEmpty()) {
                return buildResponse(500, "未找到该告警详情", null);
            }

            return buildResponse(200, "success", rows.get(0));
        } catch (Exception e) {
            return buildResponse(500, "告警详情加载失败：" + e.getMessage(), null);
        }
    }

    private long countTable(String tableName) {
        try {
            Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + tableName, Long.class);
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countStationByStatus(int targetStatus) {
        try {
            List<String> cols = tableColumns("station");
            String statusCol = pickColumn(cols, "online_status", "station_status", "status");
            if (statusCol == null) {
                return 0;
            }
            String sql = "SELECT COUNT(*) FROM station WHERE " + statusCol + " = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, targetStatus);
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countDeviceByStatus(int targetStatus) {
        try {
            List<String> cols = tableColumns("device");
            String statusCol = pickColumn(cols, "device_status", "status", "run_status");
            if (statusCol == null) {
                return 0;
            }
            String sql = "SELECT COUNT(*) FROM device WHERE " + statusCol + " = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, targetStatus);
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
        }
    }

    private long countAlarmByStatus(int targetStatus) {
        try {
            String sql = "SELECT COUNT(*) FROM alarm_record WHERE alarm_status = ?";
            Long count = jdbcTemplate.queryForObject(sql, Long.class, targetStatus);
            return count == null ? 0 : count;
        } catch (Exception e) {
            return 0;
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

    private Map<String, Object> buildResponse(int code, String msg, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", msg);
        result.put("data", data);
        return result;
    }
}