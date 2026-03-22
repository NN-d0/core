package com.radio.core.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 站点新增请求
 */
@Data
public class StationSaveRequest {

    @NotBlank(message = "站点编码不能为空")
    private String stationCode;

    @NotBlank(message = "站点名称不能为空")
    private String stationName;

    @NotNull(message = "经度不能为空")
    private BigDecimal longitude;

    @NotNull(message = "纬度不能为空")
    private BigDecimal latitude;

    @NotBlank(message = "位置描述不能为空")
    private String locationText;
}