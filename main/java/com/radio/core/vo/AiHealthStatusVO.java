package com.radio.core.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 服务健康状态返回对象
 */
@Data
public class AiHealthStatusVO {

    /**
     * Core 侧是否启用 AI
     */
    private Boolean aiEnabled;

    /**
     * Core 当前配置的 AI 基础地址
     */
    private String aiBaseUrl;

    /**
     * Core 当前配置的 AI 健康检查地址
     */
    private String aiHealthUrl;

    /**
     * Flask AI 服务名
     */
    private String service;

    /**
     * Flask AI 服务状态，例如 UP / DOWN
     */
    private String serviceStatus;

    /**
     * 是否成功请求到 Flask /health
     */
    private Boolean serviceUp;

    /**
     * Flask AI 默认模式：rule / cnn / auto
     */
    private String defaultMode;

    /**
     * 是否允许自动回退到规则模型
     */
    private Boolean allowRuleFallback;

    /**
     * 规则模型名称
     */
    private String ruleModelName;

    /**
     * 规则模型是否可用
     */
    private Boolean ruleModelAvailable;

    /**
     * CNN 模型名称
     */
    private String cnnModelName;

    /**
     * CNN 是否已成功加载
     */
    private Boolean cnnModelAvailable;

    /**
     * CNN 检查点路径
     */
    private String cnnCheckpointPath;

    /**
     * CNN 路径是否存在
     */
    private Boolean cnnCheckpointExists;

    /**
     * CNN 路径是否为文件
     */
    private Boolean cnnCheckpointIsFile;

    /**
     * 推理设备，例如 cpu / cuda:0
     */
    private String cnnDevice;

    /**
     * 输入形状
     */
    private List<Integer> cnnInputShape;

    /**
     * CNN 错误信息
     */
    private String cnnError;

    /**
     * 当前是否存在 RULE fallback 风险
     */
    private Boolean fallbackRisk;

    /**
     * 风险等级：LOW / HIGH
     */
    private String fallbackRiskLevel;

    /**
     * 风险原因说明
     */
    private String fallbackRiskReason;

    /**
     * 面向日志/页面的汇总文本
     */
    private String summary;

    /**
     * Core 拉取时间
     */
    private LocalDateTime fetchedAt;
}
