package com.radio.core.controller;

import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 告警操作接口
 * 1. 确认告警
 * 2. 处理告警
 */
@RestController
@RequestMapping({"/api/core/alarm", "/core/alarm", "/alarm"})
public class AlarmActionController {

    private static final String TABLE_NAME = "alarm_record";

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 确认告警：0 -> 1
     */
    @PostMapping("/confirm")
    public Map<String, Object> confirm(@RequestBody AlarmActionReq req) {
        if (req == null || req.getAlarmId() == null) {
            return buildResponse(500, "alarmId不能为空", null);
        }

        LocalDateTime now = LocalDateTime.now();

        String sql = "UPDATE " + TABLE_NAME + " " +
                "SET alarm_status = 1, confirm_time = ?, update_time = ? " +
                "WHERE id = ? AND alarm_status = 0";

        int updated = jdbcTemplate.update(sql, now, now, req.getAlarmId());

        if (updated > 0) {
            return buildResponse(200, "告警已确认", updated);
        }

        return buildResponse(500, "告警确认失败，可能该告警已不是未处理状态", 0);
    }

    /**
     * 处理告警：0/1 -> 2
     */
    @PostMapping("/handle")
    public Map<String, Object> handle(@RequestBody AlarmActionReq req) {
        if (req == null || req.getAlarmId() == null) {
            return buildResponse(500, "alarmId不能为空", null);
        }

        LocalDateTime now = LocalDateTime.now();

        String sql = "UPDATE " + TABLE_NAME + " " +
                "SET alarm_status = 2, handle_time = ?, handle_note = ?, update_time = ? " +
                "WHERE id = ? AND alarm_status IN (0, 1)";

        int updated = jdbcTemplate.update(
                sql,
                now,
                req.getHandleNote(),
                now,
                req.getAlarmId()
        );

        if (updated > 0) {
            return buildResponse(200, "告警已处理", updated);
        }

        return buildResponse(500, "告警处理失败，可能该告警已被处理", 0);
    }

    private Map<String, Object> buildResponse(int code, String msg, Object data) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("code", code);
        result.put("msg", msg);
        result.put("data", data);
        return result;
    }

    @Data
    public static class AlarmActionReq {
        private Long alarmId;
        private String handleNote;
    }
}