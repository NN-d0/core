package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.AlarmActionService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 告警操作接口
 *
 * 统一后的接口路径：
 * 1. POST /api/core/alarms/confirm
 * 2. POST /api/core/alarms/handle
 */
@RestController
@RequestMapping("/api/core/alarms")
@RequiredArgsConstructor
public class AlarmActionController {

    private final AlarmActionService alarmActionService;

    /**
     * 确认告警：0 -> 1
     */
    @PostMapping("/confirm")
    public ApiResponse<Boolean> confirm(@RequestBody AlarmActionReq req) {
        return ApiResponse.success(
                "告警已确认",
                alarmActionService.confirmAlarm(req == null ? null : req.getAlarmId())
        );
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