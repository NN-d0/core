package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.CorePageQueryService;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.HistorySnapshotVO;
import com.radio.core.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 历史与回放接口
 */
@RestController
@RequestMapping("/api/core/history")
@RequiredArgsConstructor
public class HistoryController {

    private final CoreQueryService coreQueryService;
    private final CorePageQueryService corePageQueryService;

    /**
     * 历史快照全量列表
     */
    @GetMapping("/list")
    public ApiResponse<List<HistorySnapshotVO>> list(@RequestParam(required = false) Long stationId,
                                                     @RequestParam(required = false) String signalType,
                                                     @RequestParam(required = false) String startTime,
                                                     @RequestParam(required = false) String endTime) {
        return ApiResponse.success(coreQueryService.listHistorySnapshots(stationId, signalType, startTime, endTime));
    }

    /**
     * 历史快照分页列表
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<HistorySnapshotVO>> page(@RequestParam(defaultValue = "1") Long current,
                                                           @RequestParam(defaultValue = "10") Long size,
                                                           @RequestParam(required = false) Long stationId,
                                                           @RequestParam(required = false) String signalType,
                                                           @RequestParam(required = false) String startTime,
                                                           @RequestParam(required = false) String endTime) {
        return ApiResponse.success(corePageQueryService.pageHistorySnapshots(current, size, stationId, signalType, startTime, endTime));
    }
}