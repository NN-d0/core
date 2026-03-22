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
import java.util.Map;

/**
 * APP 端告警分页接口
 * 只负责：
 * 1. 告警分页查询
 */
@RestController
@RequestMapping("/api/core/alarm")
public class AlarmAppController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/page")
    public Map<String, Object> page(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer alarmStatus,
            @RequestParam(required = false) String keyword
    ) {
        int pageNo = (current == null || current < 1) ? 1 : current;
        int pageSize = (size == null || size < 1) ? 10 : size;
        int offset = (pageNo - 1) * pageSize;

        StringBuilder fromWhereSql = new StringBuilder(
                " FROM alarm_record a " +
                        " LEFT JOIN station s ON a.station_id = s.id " +
                        " LEFT JOIN device d ON a.device_id = d.id " +
                        " WHERE 1=1 "
        );

        List<Object> params = new ArrayList<>();

        if (alarmStatus != null) {
            fromWhereSql.append(" AND a.alarm_status = ? ");
            params.add(alarmStatus);
        }

        if (keyword != null && !keyword.trim().isEmpty()) {
            String likeKeyword = "%" + keyword.trim() + "%";
            fromWhereSql.append(" AND (")
                    .append(" a.alarm_no LIKE ? ")
                    .append(" OR a.title LIKE ? ")
                    .append(" OR s.station_name LIKE ? ")
                    .append(" OR d.device_name LIKE ? ")
                    .append(") ");
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
            params.add(likeKeyword);
        }

        String countSql = "SELECT COUNT(*) " + fromWhereSql;
        Long total = jdbcTemplate.queryForObject(countSql, Long.class, params.toArray());

        String listSql =
                "SELECT " +
                        "a.id, " +
                        "a.alarm_no AS alarmNo, " +
                        "a.station_id AS stationId, " +
                        "a.device_id AS deviceId, " +
                        "s.station_name AS stationName, " +
                        "d.device_name AS deviceName, " +
                        "a.alarm_type AS alarmType, " +
                        "a.alarm_level AS alarmLevel, " +
                        "a.title, " +
                        "a.content, " +
                        "a.alarm_status AS alarmStatus, " +
                        "a.handle_note AS handleNote, " +
                        "DATE_FORMAT(a.alarm_time, '%Y-%m-%d %H:%i:%s') AS alarmTime, " +
                        "DATE_FORMAT(a.confirm_time, '%Y-%m-%d %H:%i:%s') AS confirmTime, " +
                        "DATE_FORMAT(a.handle_time, '%Y-%m-%d %H:%i:%s') AS handleTime " +
                        fromWhereSql +
                        " ORDER BY a.alarm_time DESC LIMIT ?, ?";

        List<Object> listParams = new ArrayList<>(params);
        listParams.add(offset);
        listParams.add(pageSize);

        List<Map<String, Object>> records = jdbcTemplate.queryForList(listSql, listParams.toArray());

        Map<String, Object> pageData = new LinkedHashMap<>();
        pageData.put("total", total == null ? 0 : total);
        pageData.put("records", records);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", 200);
        result.put("msg", "success");
        result.put("data", pageData);

        return result;
    }
}