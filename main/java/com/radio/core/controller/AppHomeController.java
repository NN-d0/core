package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.CorePageQueryService;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.AlarmListVO;
import com.radio.core.vo.OverviewSummaryVO;
import com.radio.core.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APP 首页接口
 *
 * 本次收口目标：
 * 1. 不再在 Controller 中直接使用 JdbcTemplate
 * 2. 首页统计复用 CoreQueryService.getOverviewSummary()
 * 3. 最新告警复用 CorePageQueryService.pageAlarms(...)
 * 4. 告警详情复用 CoreQueryService.getAlarmDetail(...)
 * 5. 保持原有接口路径不变，避免 APP 端额外改动
 */
@RestController
@RequestMapping("/api/core/app/home")
@RequiredArgsConstructor
public class AppHomeController {

    private final CoreQueryService coreQueryService;
    private final CorePageQueryService corePageQueryService;

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        OverviewSummaryVO vo = coreQueryService.getOverviewSummary();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("stationCount", safeInt(vo.getStationCount()));
        data.put("onlineStationCount", safeInt(vo.getOnlineStationCount()));
        data.put("offlineStationCount", safeInt(vo.getOfflineStationCount()));

        data.put("deviceCount", safeInt(vo.getDeviceCount()));
        data.put("runningDeviceCount", safeInt(vo.getRunningDeviceCount()));
        data.put("stopDeviceCount", safeInt(vo.getStopDeviceCount()));

        data.put("alarmCount", safeInt(vo.getAlarmCount()));
        data.put("pendingAlarmCount", safeInt(vo.getUnreadAlarmCount()));
        data.put("confirmedAlarmCount", safeInt(vo.getConfirmedAlarmCount()));
        data.put("handledAlarmCount", safeInt(vo.getHandledAlarmCount()));

        return ApiResponse.success(data);
    }

    @GetMapping("/latest-alarms")
    public ApiResponse<List<AlarmListVO>> latestAlarms(@RequestParam(defaultValue = "5") Integer size) {
        long pageSize = (size == null || size < 1) ? 5L : Math.min(size, 10);
        PageResult<AlarmListVO> pageResult = corePageQueryService.pageAlarms(1L, pageSize, null, null, null);
        return ApiResponse.success(pageResult.getRecords());
    }

    @GetMapping("/alarm/detail")
    public ApiResponse<AlarmListVO> alarmDetail(@RequestParam Long id) {
        return ApiResponse.success(coreQueryService.getAlarmDetail(id));
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}