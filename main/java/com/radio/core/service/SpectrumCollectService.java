package com.radio.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radio.core.constant.RedisKeyConstants;
import com.radio.core.dto.CollectReportRequest;
import com.radio.core.entity.AlarmRecord;
import com.radio.core.entity.Device;
import com.radio.core.entity.MonitorTask;
import com.radio.core.entity.SpectrumSnapshot;
import com.radio.core.entity.Station;
import com.radio.core.entity.SysConfig;
import com.radio.core.exception.BusinessException;
import com.radio.core.mapper.AlarmRecordMapper;
import com.radio.core.mapper.DeviceMapper;
import com.radio.core.mapper.MonitorTaskMapper;
import com.radio.core.mapper.SpectrumSnapshotMapper;
import com.radio.core.mapper.StationMapper;
import com.radio.core.mapper.SysConfigMapper;
import com.radio.core.vo.AiPredictResultVO;
import com.radio.core.vo.CollectReportResponse;
import com.radio.core.vo.RealtimeSpectrumVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 频谱采集服务
 *
 * 当前版本职责：
 * 1. 接收 Python 仿真器的上报数据
 * 2. 校验站点 / 设备 / 任务关系
 * 3. 强校验任务是否处于运行中（task_status = 1）
 * 4. 由 Core 统一调用 Flask AI /predict
 * 5. 写入 spectrum_snapshot
 * 6. 根据 AI 结果 / 阈值生成告警
 * 7. 刷新 Redis 最新频谱缓存
 * 8. 刷新未处理告警数缓存
 * 9. 更新站点在线状态和设备最后在线时间
 *
 * 当前系统唯一真实数据链路：
 * Python Simulator -> Core Collect API -> Core AI -> MySQL / Redis / Alarm / WebSocket
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SpectrumCollectService {

    private static final long REALTIME_CACHE_TTL_SECONDS = 120L;
    private static final long STATION_ONLINE_CACHE_TTL_SECONDS = 300L;
    private static final long ALARM_UNREAD_CACHE_TTL_SECONDS = 120L;

    private static final String POWER_THRESHOLD_KEY = "alarm.power.threshold.dbm";
    private static final String SNR_THRESHOLD_KEY = "alarm.snr.threshold.db";

    private static final BigDecimal DEFAULT_POWER_THRESHOLD = new BigDecimal("-30");
    private static final BigDecimal DEFAULT_SNR_THRESHOLD = new BigDecimal("10");

    private static final DateTimeFormatter NORMAL_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ALARM_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final StationMapper stationMapper;
    private final DeviceMapper deviceMapper;
    private final MonitorTaskMapper monitorTaskMapper;
    private final SpectrumSnapshotMapper spectrumSnapshotMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final SysConfigMapper sysConfigMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final AiInferenceService aiInferenceService;
    private final RuntimeStatusSyncService runtimeStatusSyncService;

    /**
     * 接收采集上报
     */
    @Transactional(rollbackFor = Exception.class)
    public CollectReportResponse receiveReport(CollectReportRequest request) {
        validateRequest(request);

        Station station = stationMapper.selectById(request.getStationId());
        if (station == null) {
            throw new BusinessException(404, "站点不存在，stationId=" + request.getStationId());
        }

        Device device = deviceMapper.selectById(request.getDeviceId());
        if (device == null) {
            throw new BusinessException(404, "设备不存在，deviceId=" + request.getDeviceId());
        }

        if (!request.getStationId().equals(device.getStationId())) {
            throw new BusinessException(400, "设备与站点不匹配");
        }

        MonitorTask task = monitorTaskMapper.selectById(request.getTaskId());
        if (task == null) {
            throw new BusinessException(404, "任务不存在，taskId=" + request.getTaskId());
        }

        if (!request.getStationId().equals(task.getStationId())) {
            throw new BusinessException(400, "任务与站点不匹配");
        }

        if (!request.getDeviceId().equals(task.getDeviceId())) {
            throw new BusinessException(400, "任务与设备不匹配");
        }

        // 只有 task_status = 1 的运行中任务，Core 才允许接收仿真器上报
        if (task.getTaskStatus() == null || task.getTaskStatus() != 1) {
            log.warn("拒绝采集上报：任务未运行，taskId={}, taskStatus={}",
                    task.getId(), task.getTaskStatus());
            throw new BusinessException(409, "任务未处于运行中，禁止采集上报，taskId=" + task.getId());
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime captureTime = parseCaptureTime(request.getCaptureTime(), now);

        List<BigDecimal> waterfallRow = (request.getWaterfallRow() == null || request.getWaterfallRow().isEmpty())
                ? request.getPowerPoints()
                : request.getWaterfallRow();

        // 1. 由 Core 主动调用 AI
        AiPredictResultVO aiResult = aiInferenceService.predict(request);

        // 2. AI 标签与推理元信息统一由 Core 决定
        String aiLabel = resolveAiLabel(request, aiResult);
        String aiRequestMode = resolveAiRequestMode(request, aiResult);
        String aiActualMode = resolveAiActualMode(request, aiResult);
        Integer aiFallbackUsed = resolveAiFallbackUsed(aiResult);
        String aiModelName = resolveAiModelName(aiResult);
        String aiReason = resolveAiReason(aiResult);

        // 3. 告警是否触发优先采用 AI 的 shouldAlarm，AI 异常时再走阈值兜底
        int alarmFlag = resolveAlarmFlagByAiOrThreshold(request, aiResult);

        SpectrumSnapshot snapshot = new SpectrumSnapshot();
        snapshot.setStationId(request.getStationId());
        snapshot.setDeviceId(request.getDeviceId());
        snapshot.setTaskId(request.getTaskId());
        snapshot.setCenterFreqMhz(request.getCenterFreqMhz());
        snapshot.setBandwidthKhz(request.getBandwidthKhz());
        snapshot.setSignalType(request.getSignalType().trim());
        snapshot.setChannelModel(resolveChannelModel(request.getChannelModel()));
        snapshot.setPeakPowerDbm(request.getPeakPowerDbm());
        snapshot.setSnrDb(request.getSnrDb());
        snapshot.setOccupiedBandwidthKhz(request.getOccupiedBandwidthKhz());
        snapshot.setAiLabel(aiLabel);
        snapshot.setAiRequestMode(aiRequestMode);
        snapshot.setAiActualMode(aiActualMode);
        snapshot.setAiFallbackUsed(aiFallbackUsed);
        snapshot.setAiModelName(aiModelName);
        snapshot.setAiReason(aiReason);
        snapshot.setAlarmFlag(alarmFlag);
        snapshot.setPowerPointsJson(toJsonArray(request.getPowerPoints()));
        snapshot.setWaterfallRowJson(toJsonArray(waterfallRow));
        snapshot.setCaptureTime(captureTime);
        snapshot.setCreateTime(now);

        spectrumSnapshotMapper.insert(snapshot);

        Long alarmId = null;
        if (alarmFlag == 1) {
            AlarmRecord alarmRecord = buildAlarmRecord(snapshot, station, device, task, aiResult);
            alarmRecordMapper.insert(alarmRecord);
            alarmId = alarmRecord.getId();
        }

        // 采集成功后统一同步设备/站点状态
        runtimeStatusSyncService.syncAfterCollectSuccess(station, device, captureTime);

        RealtimeSpectrumVO realtimeVO = buildRealtimeVO(snapshot, station, device, task);
        cacheLatestRealtime(realtimeVO);
        cacheStationOnlineStatus(station.getId(), 1);
        refreshUnreadAlarmCountCache();

        CollectReportResponse response = new CollectReportResponse();
        response.setAccepted(true);
        response.setSnapshotId(snapshot.getId());
        response.setAlarmId(alarmId);
        response.setAlarmFlag(alarmFlag);
        response.setAiLabel(aiLabel);
        response.setAiRequestMode(aiRequestMode);
        response.setAiActualMode(aiActualMode);
        response.setAiFallbackUsed(aiFallbackUsed);
        response.setAiModelName(aiModelName);
        response.setAiReason(aiReason);
        response.setTaskStatus(task.getTaskStatus());
        response.setStationId(snapshot.getStationId());
        response.setDeviceId(snapshot.getDeviceId());
        response.setTaskId(snapshot.getTaskId());
        response.setCaptureTime(snapshot.getCaptureTime());
        log.info("采集上报成功，snapshotId={}, stationId={}, deviceId={}, taskId={}, taskStatus={}, alarmFlag={}, aiLabel={}, requestMode={}, actualMode={}, fallbackUsed={}, modelName={}",
                snapshot.getId(), snapshot.getStationId(), snapshot.getDeviceId(), snapshot.getTaskId(),
                task.getTaskStatus(), alarmFlag, aiLabel, aiRequestMode, aiActualMode, aiFallbackUsed, aiModelName);

        return response;
    }

    private void validateRequest(CollectReportRequest request) {
        if (request == null) {
            throw new BusinessException(400, "请求体不能为空");
        }
        if (request.getTaskId() == null) {
            throw new BusinessException(400, "当前版本采集上报必须传 taskId");
        }
        if (request.getPowerPoints() == null || request.getPowerPoints().isEmpty()) {
            throw new BusinessException(400, "powerPoints 不能为空");
        }
    }

    private String resolveAiRequestMode(CollectReportRequest request, AiPredictResultVO aiResult) {
        if (aiResult != null && aiResult.getRequestMode() != null && !aiResult.getRequestMode().isBlank()) {
            return normalizeAiMode(aiResult.getRequestMode(), "RULE");
        }
        if (request != null && request.getModelType() != null && !request.getModelType().isBlank()) {
            return normalizeAiMode(request.getModelType(), "RULE");
        }
        return "RULE";
    }

    private String resolveAiActualMode(CollectReportRequest request, AiPredictResultVO aiResult) {
        if (aiResult != null && aiResult.getActualMode() != null && !aiResult.getActualMode().isBlank()) {
            return normalizeAiMode(aiResult.getActualMode(), "RULE");
        }
        if (aiResult != null && aiResult.getInferenceMode() != null && !aiResult.getInferenceMode().isBlank()) {
            return normalizeAiMode(aiResult.getInferenceMode(), "RULE");
        }
        return resolveAiRequestMode(request, aiResult);
    }

    private Integer resolveAiFallbackUsed(AiPredictResultVO aiResult) {
        return (aiResult != null && Boolean.TRUE.equals(aiResult.getFallbackUsed())) ? 1 : 0;
    }

    private String resolveAiModelName(AiPredictResultVO aiResult) {
        if (aiResult == null || aiResult.getModelName() == null || aiResult.getModelName().isBlank()) {
            return "unknown-model";
        }
        return aiResult.getModelName().trim();
    }

    private String resolveAiReason(AiPredictResultVO aiResult) {
        if (aiResult == null || aiResult.getReason() == null || aiResult.getReason().isBlank()) {
            return "";
        }
        return aiResult.getReason().trim();
    }

    private String normalizeAiMode(String mode, String defaultValue) {
        if (mode == null || mode.isBlank()) {
            return defaultValue;
        }

        String value = mode.trim().toUpperCase();
        if ("AI".equals(value)) {
            return "CNN";
        }
        if ("RULE".equals(value) || "CNN".equals(value) || "AUTO".equals(value)) {
            return value;
        }
        return defaultValue;
    }

    private String resolveAiLabel(CollectReportRequest request, AiPredictResultVO aiResult) {
        if (aiResult != null && aiResult.getPredictedLabel() != null && !aiResult.getPredictedLabel().isBlank()) {
            return aiResult.getPredictedLabel().trim();
        }
        if (request.getAiLabel() != null && !request.getAiLabel().isBlank()) {
            return request.getAiLabel().trim();
        }
        return request.getSignalType().trim();
    }

    private String resolveChannelModel(String channelModel) {
        if (channelModel == null || channelModel.isBlank()) {
            return "AWGN";
        }
        return channelModel.trim();
    }

    /**
     * 告警判断：
     * 1. 优先采用 AI shouldAlarm
     * 2. AI 为空或异常时，走 Core 阈值兜底
     */
    private int resolveAlarmFlagByAiOrThreshold(CollectReportRequest request, AiPredictResultVO aiResult) {
        if (aiResult != null && aiResult.getShouldAlarm() != null) {
            return Boolean.TRUE.equals(aiResult.getShouldAlarm()) ? 1 : 0;
        }
        return resolveAlarmFlagByThreshold(request);
    }

    private int resolveAlarmFlagByThreshold(CollectReportRequest request) {
        BigDecimal powerThreshold = getConfigDecimal(POWER_THRESHOLD_KEY, DEFAULT_POWER_THRESHOLD);
        BigDecimal snrThreshold = getConfigDecimal(SNR_THRESHOLD_KEY, DEFAULT_SNR_THRESHOLD);

        boolean highPower = request.getPeakPowerDbm() != null
                && request.getPeakPowerDbm().compareTo(powerThreshold) >= 0;

        boolean lowSnr = request.getSnrDb() != null
                && request.getSnrDb().compareTo(snrThreshold) <= 0;

        return (highPower || lowSnr) ? 1 : 0;
    }

    private AlarmRecord buildAlarmRecord(SpectrumSnapshot snapshot,
                                         Station station,
                                         Device device,
                                         MonitorTask task,
                                         AiPredictResultVO aiResult) {
        LocalDateTime now = LocalDateTime.now();

        BigDecimal powerThreshold = getConfigDecimal(POWER_THRESHOLD_KEY, DEFAULT_POWER_THRESHOLD);
        BigDecimal snrThreshold = getConfigDecimal(SNR_THRESHOLD_KEY, DEFAULT_SNR_THRESHOLD);

        boolean highPower = snapshot.getPeakPowerDbm() != null
                && snapshot.getPeakPowerDbm().compareTo(powerThreshold) >= 0;

        boolean lowSnr = snapshot.getSnrDb() != null
                && snapshot.getSnrDb().compareTo(snrThreshold) <= 0;

        String riskLevel = normalizeAlarmLevel(aiResult == null ? null : aiResult.getRiskLevel());
        String aiReason = aiResult == null ? null : aiResult.getReason();

        AlarmRecord alarm = new AlarmRecord();
        alarm.setAlarmNo(buildAlarmNo(now));
        alarm.setStationId(snapshot.getStationId());
        alarm.setDeviceId(snapshot.getDeviceId());
        alarm.setTaskId(snapshot.getTaskId());
        alarm.setSnapshotId(snapshot.getId());
        alarm.setAlarmStatus(0);
        alarm.setAlarmTime(snapshot.getCaptureTime());
        alarm.setCreateTime(now);
        alarm.setUpdateTime(now);

        if (highPower) {
            alarm.setAlarmType("ILLEGAL_SIGNAL");
            alarm.setAlarmLevel("HIGH");
            alarm.setTitle("疑似高功率异常信号");
            alarm.setContent(String.format(
                    "站点[%s] 设备[%s] 在 %.3f MHz 检测到高功率异常信号。识别结果=%s，峰值功率=%.2f dBm，超过阈值=%s dBm。%s",
                    station.getStationName(),
                    device.getDeviceName(),
                    snapshot.getCenterFreqMhz(),
                    snapshot.getAiLabel(),
                    snapshot.getPeakPowerDbm(),
                    powerThreshold.toPlainString(),
                    safeAiReason(aiReason)
            ));
            return alarm;
        }

        if (lowSnr) {
            alarm.setAlarmType("SNR_LOW");
            alarm.setAlarmLevel("MEDIUM");
            alarm.setTitle("信号质量下降");
            alarm.setContent(String.format(
                    "站点[%s] 设备[%s] 在 %.3f MHz 检测到低信噪比信号。识别结果=%s，当前 SNR=%.2f dB，低于阈值=%s dB。%s",
                    station.getStationName(),
                    device.getDeviceName(),
                    snapshot.getCenterFreqMhz(),
                    snapshot.getAiLabel(),
                    snapshot.getSnrDb(),
                    snrThreshold.toPlainString(),
                    safeAiReason(aiReason)
            ));
            return alarm;
        }

        alarm.setAlarmType("ABNORMAL_SIGNAL");
        alarm.setAlarmLevel(riskLevel);
        alarm.setTitle("异常频谱告警");
        alarm.setContent(String.format(
                "站点[%s] 设备[%s] 在 %.3f MHz 检测到异常频谱。识别结果=%s，风险等级=%s。%s",
                station.getStationName(),
                device.getDeviceName(),
                snapshot.getCenterFreqMhz(),
                snapshot.getAiLabel(),
                riskLevel,
                safeAiReason(aiReason)
        ));
        return alarm;
    }

    private String normalizeAlarmLevel(String riskLevel) {
        if (riskLevel == null || riskLevel.isBlank()) {
            return "MEDIUM";
        }
        String value = riskLevel.trim().toUpperCase();
        if ("HIGH".equals(value)) {
            return "HIGH";
        }
        if ("LOW".equals(value)) {
            return "LOW";
        }
        return "MEDIUM";
    }

    private String safeAiReason(String aiReason) {
        if (aiReason == null || aiReason.isBlank()) {
            return "AI 未返回解释信息。";
        }
        return "AI说明：" + aiReason;
    }

    private RealtimeSpectrumVO buildRealtimeVO(SpectrumSnapshot snapshot,
                                               Station station,
                                               Device device,
                                               MonitorTask task) {
        RealtimeSpectrumVO vo = new RealtimeSpectrumVO();
        vo.setId(snapshot.getId());
        vo.setStationId(snapshot.getStationId());
        vo.setStationName(station == null ? null : station.getStationName());
        vo.setDeviceId(snapshot.getDeviceId());
        vo.setDeviceName(device == null ? null : device.getDeviceName());
        vo.setTaskId(snapshot.getTaskId());
        vo.setTaskName(task == null ? null : task.getTaskName());
        vo.setCenterFreqMhz(snapshot.getCenterFreqMhz());
        vo.setBandwidthKhz(snapshot.getBandwidthKhz());
        vo.setSignalType(snapshot.getSignalType());
        vo.setChannelModel(snapshot.getChannelModel());
        vo.setPeakPowerDbm(snapshot.getPeakPowerDbm());
        vo.setSnrDb(snapshot.getSnrDb());
        vo.setOccupiedBandwidthKhz(snapshot.getOccupiedBandwidthKhz());
        vo.setAiLabel(snapshot.getAiLabel());
        vo.setAiRequestMode(snapshot.getAiRequestMode());
        vo.setAiActualMode(snapshot.getAiActualMode());
        vo.setAiFallbackUsed(snapshot.getAiFallbackUsed());
        vo.setAiModelName(snapshot.getAiModelName());
        vo.setAiReason(snapshot.getAiReason());
        vo.setAlarmFlag(snapshot.getAlarmFlag());
        vo.setPowerPointsJson(snapshot.getPowerPointsJson());
        vo.setWaterfallRowJson(snapshot.getWaterfallRowJson());
        vo.setCaptureTime(snapshot.getCaptureTime());
        vo.setCreateTime(snapshot.getCreateTime());
        return vo;
    }

    private void cacheLatestRealtime(RealtimeSpectrumVO vo) {
        if (vo == null) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(vo);

            if (vo.getStationId() != null) {
                stringRedisTemplate.opsForValue().set(
                        RedisKeyConstants.realtimeLatest(vo.getStationId()),
                        json,
                        REALTIME_CACHE_TTL_SECONDS,
                        TimeUnit.SECONDS
                );
            }

            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.REALTIME_LATEST_GLOBAL,
                    json,
                    REALTIME_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("缓存最新频谱失败：{}", e.getMessage());
        }
    }

    private void cacheStationOnlineStatus(Long stationId, Integer onlineStatus) {
        if (stationId == null || onlineStatus == null) {
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.stationOnline(stationId),
                    String.valueOf(onlineStatus),
                    STATION_ONLINE_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("缓存站点在线状态失败：{}", e.getMessage());
        }
    }

    private void refreshUnreadAlarmCountCache() {
        try {
            Long count = alarmRecordMapper.selectCount(
                    new LambdaQueryWrapper<AlarmRecord>()
                            .eq(AlarmRecord::getAlarmStatus, 0)
            );

            int unread = count == null ? 0 : count.intValue();
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.ALARM_UNREAD_COUNT,
                    String.valueOf(unread),
                    ALARM_UNREAD_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("刷新未处理告警缓存失败：{}", e.getMessage());
        }
    }

    private BigDecimal getConfigDecimal(String configKey, BigDecimal defaultValue) {
        try {
            SysConfig sysConfig = sysConfigMapper.selectOne(
                    new LambdaQueryWrapper<SysConfig>()
                            .eq(SysConfig::getConfigKey, configKey)
                            .last("limit 1")
            );

            if (sysConfig == null || sysConfig.getConfigValue() == null || sysConfig.getConfigValue().isBlank()) {
                return defaultValue;
            }

            return new BigDecimal(sysConfig.getConfigValue().trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String toJsonArray(List<BigDecimal> list) {
        try {
            return objectMapper.writeValueAsString(list == null ? List.of() : list);
        } catch (Exception e) {
            throw new BusinessException(500, "频谱点 JSON 序列化失败");
        }
    }

    private LocalDateTime parseCaptureTime(String text, LocalDateTime defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }

        String value = text.trim();

        try {
            return LocalDateTime.parse(value, NORMAL_TIME_FORMATTER);
        } catch (Exception ignored) {
        }

        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
        }

        throw new BusinessException(400, "captureTime 格式错误，支持 yyyy-MM-dd HH:mm:ss 或 ISO_LOCAL_DATE_TIME");
    }

    private String buildAlarmNo(LocalDateTime now) {
        return "ALARM"
                + now.format(ALARM_NO_TIME_FORMATTER)
                + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}