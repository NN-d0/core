package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.RealtimeSpectrumVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 实时频谱接口
 */
@RestController
@RequestMapping("/api/core/realtime")
@RequiredArgsConstructor
public class RealtimeController {

    private final CoreQueryService coreQueryService;

    /**
     * 查询最新实时频谱数据
     */
    @GetMapping("/latest")
    public ApiResponse<RealtimeSpectrumVO> latest(@RequestParam(required = false) Long stationId) {
        return ApiResponse.success(coreQueryService.getLatestRealtimeSnapshot(stationId));
    }
}