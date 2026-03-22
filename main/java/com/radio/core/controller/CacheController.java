package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.CoreQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redis 缓存验证接口
 */
@RestController
@RequestMapping("/api/core/cache")
@RequiredArgsConstructor
public class CacheController {

    private final CoreQueryService coreQueryService;

    /**
     * 查询站点在线状态
     */
    @GetMapping("/station-online-status")
    public ApiResponse<Integer> getStationOnlineStatus(@RequestParam Long stationId) {
        return ApiResponse.success(coreQueryService.getStationOnlineStatus(stationId));
    }

    /**
     * 查询未处理告警数
     */
    @GetMapping("/unread-alarm-count")
    public ApiResponse<Integer> getUnreadAlarmCount() {
        return ApiResponse.success(coreQueryService.getUnreadAlarmCount());
    }
}