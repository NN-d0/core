package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.CorePageQueryService;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.AlarmMapPointVO;
import com.radio.core.vo.OverviewSummaryVO;
import com.radio.core.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 首页总览 / 告警地图接口
 */
@RestController
@RequestMapping("/api/core/overview")
@RequiredArgsConstructor
public class OverviewController {

    private final CoreQueryService coreQueryService;
    private final CorePageQueryService corePageQueryService;

    /**
     * 首页总览统计
     */
    @GetMapping("/summary")
    public ApiResponse<OverviewSummaryVO> summary() {
        return ApiResponse.success(coreQueryService.getOverviewSummary());
    }

    /**
     * 告警地图点位列表
     */
    @GetMapping("/alarm-map")
    public ApiResponse<List<AlarmMapPointVO>> alarmMap(@RequestParam(required = false) Integer alarmStatus) {
        return ApiResponse.success(coreQueryService.listAlarmMapPoints(alarmStatus));
    }

    /**
     * 告警地图表格分页
     */
    @GetMapping("/alarm-map/page")
    public ApiResponse<PageResult<AlarmMapPointVO>> alarmMapPage(@RequestParam(defaultValue = "1") Long current,
                                                                 @RequestParam(defaultValue = "10") Long size,
                                                                 @RequestParam(required = false) Integer alarmStatus) {
        return ApiResponse.success(corePageQueryService.pageAlarmMapPoints(current, size, alarmStatus));
    }
}