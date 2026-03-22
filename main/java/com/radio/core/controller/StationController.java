package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.entity.Station;
import com.radio.core.service.CoreQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 站点管理接口
 */
@RestController
@RequestMapping("/api/core/stations")
@RequiredArgsConstructor
public class StationController {

    private final CoreQueryService coreQueryService;

    /**
     * 站点列表
     * onlineStatus: 1在线 0离线
     */
    @GetMapping("/list")
    public ApiResponse<List<Station>> list(@RequestParam(required = false) Integer onlineStatus) {
        return ApiResponse.success(coreQueryService.listStations(onlineStatus));
    }
}