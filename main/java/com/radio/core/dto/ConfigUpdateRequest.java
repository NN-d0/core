package com.radio.core.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 系统参数更新请求
 */
@Data
public class ConfigUpdateRequest {

    @NotBlank(message = "配置键不能为空")
    private String configKey;

    @NotBlank(message = "配置值不能为空")
    private String configValue;
}