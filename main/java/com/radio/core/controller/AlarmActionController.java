package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.AlarmActionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 告警操作接口
 * 1. 确认告警
 * 2. 处理告警
 *
 * 本次修复：
 * - Controller 不再直接使用 JdbcTemplate
 * - 统一收口到 AlarmActionService
 * - 返回格式统一为 ApiResponse
 */
@RestController
@RequestMapping({"/api/core/alarm", "/core/alarm", "/alarm"})
@RequiredArgsConstructor
public class AlarmActionController {

    private final AlarmActionService alarmActionService;

    /**
     * 确认告警：0 -> 1
     */
    @PostMapping("/confirm")
    public ApiResponse<Boolean> confirm(@RequestBody AlarmActionReq req) {
        return ApiResponse.success("告警已确认", alarmActionService.confirmAlarm(req == null ? null : req.getAlarmId()));
    }

    /**
     * 处理告警：0/1 -> 2
     */
    @PostMapping("/handle")
    public ApiResponse<Boolean> handle(@RequestBody AlarmActionReq req) {
        return ApiResponse.success(
                "告警已处理",
                alarmActionService.handleAlarm(
                        req == null ? null : req.getAlarmId(),
                        req == null ? null : req.getHandleNote()
                )
        );
    }

    @Data
    public static class AlarmActionReq {
        private Long alarmId;
        private String handleNote;
    }
}