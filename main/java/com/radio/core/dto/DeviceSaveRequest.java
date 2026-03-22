package com.radio.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 设备新增/修改请求
 */
@Data
public class DeviceSaveRequest {

    private Long id;

    @NotBlank(message = "设备编码不能为空")
    private String deviceCode;

    @NotBlank(message = "设备名称不能为空")
    private String deviceName;

    @NotNull(message = "所属站点不能为空")
    private Long stationId;

    @NotBlank(message = "设备类型不能为空")
    private String deviceType;

    @NotBlank(message = "IP地址不能为空")
    private String ipAddr;

    @NotNull(message = "运行状态不能为空")
    private Integer runStatus;

    private String remark;
}