package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.dto.DeviceSaveRequest;
import com.radio.core.service.CoreManageService;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.DeviceListVO;
import com.radio.core.vo.PageResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 设备接口
 */
@RestController
@RequestMapping("/api/core/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final CoreQueryService coreQueryService;
    private final CoreManageService coreManageService;

    /**
     * 设备列表
     */
    @GetMapping("/list")
    public ApiResponse<List<DeviceListVO>> list(@RequestParam(required = false) Long stationId,
                                                @RequestParam(required = false) Integer runStatus) {
        return ApiResponse.success(coreQueryService.listDevices(stationId, runStatus));
    }

    /**
     * 设备分页列表
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<DeviceListVO>> page(@RequestParam(defaultValue = "1") Long current,
                                                      @RequestParam(defaultValue = "10") Long size,
                                                      @RequestParam(required = false) Long stationId,
                                                      @RequestParam(required = false) Integer runStatus,
                                                      @RequestParam(required = false) String keyword) {
        return ApiResponse.success(coreManageService.pageDevices(current, size, stationId, runStatus, keyword));
    }

    /**
     * 新增设备
     */
    @PostMapping("/create")
    public ApiResponse<Void> create(@Valid @RequestBody DeviceSaveRequest request) {
        coreManageService.createDevice(request);
        return ApiResponse.success("新增成功", null);
    }

    /**
     * 修改设备
     */
    @PutMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody DeviceSaveRequest request) {
        coreManageService.updateDevice(request);
        return ApiResponse.success("修改成功", null);
    }

    /**
     * 删除设备
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        coreManageService.deleteDevice(id);
        return ApiResponse.success("删除成功", null);
    }
}