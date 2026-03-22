package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.CorePageQueryService;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.AlarmListVO;
import com.radio.core.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 告警接口
 */
@RestController
@RequestMapping("/api/core/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final CoreQueryService coreQueryService;
    private final CorePageQueryService corePageQueryService;

    /**
     * 告警全量列表
     */
    @GetMapping("/list")
    public ApiResponse<List<AlarmListVO>> list(@RequestParam(required = false) Integer alarmStatus) {
        return ApiResponse.success(coreQueryService.listAlarms(alarmStatus));
    }

    /**
     * 告警分页列表
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AlarmListVO>> page(@RequestParam(defaultValue = "1") Long current,
                                                     @RequestParam(defaultValue = "10") Long size,
                                                     @RequestParam(required = false) Integer alarmStatus,
                                                     @RequestParam(required = false) Long stationId,
                                                     @RequestParam(required = false) String keyword) {
        return ApiResponse.success(corePageQueryService.pageAlarms(current, size, alarmStatus, stationId, keyword));
    }
}