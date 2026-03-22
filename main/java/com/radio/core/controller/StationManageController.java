package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.dto.StationSaveRequest;
import com.radio.core.service.CoreManageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 站点管理接口
 */
@RestController
@RequestMapping("/api/core/stations")
@RequiredArgsConstructor
public class StationManageController {

    private final CoreManageService coreManageService;

    /**
     * 新增站点
     */
    @PostMapping("/create")
    public ApiResponse<Long> create(@Valid @RequestBody StationSaveRequest request) {
        return ApiResponse.success(coreManageService.createStation(request));
    }
}