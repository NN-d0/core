package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.dto.CollectReportRequest;
import com.radio.core.service.SpectrumCollectService;
import com.radio.core.vo.CollectReportResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * 采集上报接口
 *
 * 用途：
 * 1. Python 仿真器通过 HTTP 向 Core 上报实时频谱数据
 * 2. Core 统一负责入库、缓存、告警判断
 * 3. 后续可在这里继续接入 AI 服务
 */
@RestController
@RequestMapping("/api/core/open/collect")
@RequiredArgsConstructor
@Validated
public class CollectController {

    private final SpectrumCollectService spectrumCollectService;

    /**
     * 频谱采集上报
     */
    @PostMapping("/report")
    public ApiResponse<CollectReportResponse> report(@Valid @RequestBody CollectReportRequest request) {
        CollectReportResponse response = spectrumCollectService.receiveReport(request);
        return ApiResponse.success("采集上报成功", response);
    }
}