package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.service.CorePageQueryService;
import com.radio.core.vo.AlarmListVO;
import com.radio.core.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * APP 端告警分页接口
 *
 * 本次收口目标：
 * 1. 不再在 Controller 中直接使用 JdbcTemplate
 * 2. 统一复用 CorePageQueryService.pageAlarms(...)
 * 3. 保持 APP 端接口路径不变，避免前端额外改动
 * 4. 返回格式统一为 ApiResponse
 */
@RestController
@RequestMapping("/api/core/alarm")
@RequiredArgsConstructor
public class AlarmAppController {

    private final CorePageQueryService corePageQueryService;

    /**
     * APP 端告警分页
     *
     * 说明：
     * - 当前 APP 端只需要状态 + 关键字筛选
     * - stationId 这里先固定传 null
     * - 复用 PC 端已有分页查询逻辑，减少重复代码
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<AlarmListVO>> page(
            @RequestParam(defaultValue = "1") Long current,
            @RequestParam(defaultValue = "10") Long size,
            @RequestParam(required = false) Integer alarmStatus,
            @RequestParam(required = false) String keyword
    ) {
        return ApiResponse.success(
                corePageQueryService.pageAlarms(current, size, alarmStatus, null, keyword)
        );
    }
}