package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.AppStationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * APP 端站点状态接口
 *
 * 本次收口目标：
 * 1. Controller 不再直接使用 JdbcTemplate
 * 2. 查询逻辑统一收口到 AppStationService
 * 3. 保持接口路径不变，避免 APP 端额外改动
 */
@RestController
@RequestMapping("/api/core/app/station/status")
@RequiredArgsConstructor
public class AppStationController {

    private final AppStationService appStationService;

    @GetMapping("/list")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(appStationService.listStationStatus());
    }
}