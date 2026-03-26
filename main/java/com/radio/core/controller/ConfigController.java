package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.dto.ConfigUpdateRequest;
import com.radio.core.entity.SysConfig;
import com.radio.core.service.AiHealthService;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.AiHealthStatusVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统参数接口
 */
@RestController
@RequestMapping("/api/core/configs")
@RequiredArgsConstructor
public class ConfigController {

    private final CoreQueryService coreQueryService;
    private final AiHealthService aiHealthService;

    /**
     * 系统参数列表
     */
    @GetMapping("/list")
    public ApiResponse<List<SysConfig>> list() {
        return ApiResponse.success(coreQueryService.listConfigs());
    }

    /**
     * AI 健康状态
     */
    @GetMapping("/ai-health")
    public ApiResponse<AiHealthStatusVO> aiHealth() {
        return ApiResponse.success(aiHealthService.getAiHealthStatus(true));
    }

    /**
     * 更新配置值
     */
    @PostMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody ConfigUpdateRequest request) {
        coreQueryService.updateConfig(request);
        return ApiResponse.success("保存成功", null);
    }
}
