package com.radio.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radio.core.constant.RedisKeyConstants;
import com.radio.core.entity.AlarmRecord;
import com.radio.core.entity.Device;
import com.radio.core.entity.MonitorTask;
import com.radio.core.entity.SpectrumSnapshot;
import com.radio.core.entity.Station;
import com.radio.core.entity.TaskExecuteLog;
import com.radio.core.mapper.AlarmRecordMapper;
import com.radio.core.mapper.DeviceMapper;
import com.radio.core.mapper.SpectrumSnapshotMapper;
import com.radio.core.mapper.StationMapper;
import com.radio.core.mapper.TaskExecuteLogMapper;
import com.radio.core.vo.RealtimeSpectrumVO;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 任务执行服务（最小可执行版）
 *
 * 作用：
 * 1. 执行运行中任务
 * 2. 生成频谱快照
 * 3. 刷新 Redis 最新频谱缓存
 * 4. 更新站点/设备在线状态
 * 5. 生成告警
 * 6. 写入任务执行日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDispatchService {

    private static final long REALTIME_CACHE_TTL_SECONDS = 120L;
    private static final long STATION_ONLINE_CACHE_TTL_SECONDS = 300L;
    private static final long ALARM_UNREAD_CACHE_TTL_SECONDS = 120L;

    private static final String[] SIGNAL_TYPES = {"AM", "FM", "BPSK", "QPSK", "16QAM"};
    private static final String[] CHANNEL_MODELS = {"AWGN", "瑞利衰落", "载波频偏", "采样率误差", "路损模型"};

    private static final DateTimeFormatter ALARM_NO_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 控制同一任务的告警生成频率，避免每次执行都刷一条告警
     */
    private final ConcurrentHashMap<Long, Long> lastAlarmTimeMap = new ConcurrentHashMap<>();

    private final StationMapper stationMapper;
    private final DeviceMapper deviceMapper;
    private final SpectrumSnapshotMapper spectrumSnapshotMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final TaskExecuteLogMapper taskExecuteLogMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 执行一次任务
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecuteResult executeTask(MonitorTask task) {
        long startMs = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        Station station = null;
        Device device = null;

        try {
            if (task == null || task.getId() == null) {
                throw new IllegalArgumentException("任务不能为空");
            }

            station = stationMapper.selectById(task.getStationId());
            if (station == null) {
                throw new IllegalArgumentException("站点不存在，stationId=" + task.getStationId());
            }

            device = deviceMapper.selectById(task.getDeviceId());
            if (device == null) {
                throw new IllegalArgumentException("设备不存在，deviceId=" + task.getDeviceId());
            }

            GeneratedSpectrumData generated = buildGeneratedSpectrum(task);

            SpectrumSnapshot snapshot = new SpectrumSnapshot();
            snapshot.setStationId(task.getStationId());
            snapshot.setDeviceId(task.getDeviceId());
            snapshot.setTaskId(task.getId());
            snapshot.setCenterFreqMhz(generated.getCenterFreqMhz());
            snapshot.setBandwidthKhz(generated.getBandwidthKhz());
            snapshot.setSignalType(generated.getSignalType());
            snapshot.setChannelModel(generated.getChannelModel());
            snapshot.setPeakPowerDbm(generated.getPeakPowerDbm());
            snapshot.setSnrDb(generated.getSnrDb());
            snapshot.setOccupiedBandwidthKhz(generated.getOccupiedBandwidthKhz());
            snapshot.setAiLabel(generated.getAiLabel());
            snapshot.setAlarmFlag(generated.getAlarmFlag());
            snapshot.setPowerPointsJson(generated.getPowerPointsJson());
            snapshot.setWaterfallRowJson(generated.getWaterfallRowJson());
            snapshot.setCaptureTime(now);
            snapshot.setCreateTime(now);
            spectrumSnapshotMapper.insert(snapshot);

            updateStationAndDeviceOnlineStatus(station, device, now);

            RealtimeSpectrumVO realtimeVO = buildRealtimeVO(snapshot, station, device, task);
            cacheLatestRealtime(realtimeVO);

            Long alarmId = null;
            if (generated.getAlarmFlag() != null && generated.getAlarmFlag() == 1) {
                alarmId = createAlarmIfNeeded(task, station, device, snapshot, now);
            }

            long durationMs = System.currentTimeMillis() - startMs;

            TaskExecuteLog logEntity = buildLogEntity(
                    task, station, device,
                    1,
                    "SCHEDULER",
                    alarmId == null ? "执行成功，已生成频谱快照" : "执行成功，已生成频谱快照并触发告警",
                    snapshot.getId(),
                    alarmId,
                    durationMs,
                    now
            );
            taskExecuteLogMapper.insert(logEntity);

            ExecuteResult result = new ExecuteResult();
            result.setSuccess(true);
            result.setSnapshotId(snapshot.getId());
            result.setAlarmId(alarmId);
            result.setDurationMs(durationMs);
            result.setMessage(logEntity.getExecMessage());

            log.info("任务执行成功：taskId={}, taskName={}, snapshotId={}, alarmId={}",
                    task.getId(), task.getTaskName(), snapshot.getId(), alarmId);

            return result;
        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - startMs;

            TaskExecuteLog logEntity = buildLogEntity(
                    task, station, device,
                    2,
                    "SCHEDULER",
                    "执行失败：" + e.getMessage(),
                    null,
                    null,
                    durationMs,
                    now
            );
            taskExecuteLogMapper.insert(logEntity);

            log.error("任务执行失败：taskId={}, taskName={}, error={}",
                    task == null ? null : task.getId(),
                    task == null ? null : task.getTaskName(),
                    e.getMessage(),
                    e);

            throw e;
        }
    }

    /**
     * 记录“手动停止”日志
     */
    public void recordManualStopLog(MonitorTask task) {
        LocalDateTime now = LocalDateTime.now();
        Station station = null;
        Device device = null;

        if (task != null) {
            if (task.getStationId() != null) {
                station = stationMapper.selectById(task.getStationId());
            }
            if (task.getDeviceId() != null) {
                device = deviceMapper.selectById(task.getDeviceId());
            }
        }

        TaskExecuteLog logEntity = buildLogEntity(
                task,
                station,
                device,
                3,
                "MANUAL_STOP",
                "任务已手动停止，后续不再自动执行",
                null,
                null,
                0L,
                now
        );
        taskExecuteLogMapper.insert(logEntity);
    }

    private TaskExecuteLog buildLogEntity(MonitorTask task,
                                          Station station,
                                          Device device,
                                          Integer execStatus,
                                          String triggerType,
                                          String execMessage,
                                          Long snapshotId,
                                          Long alarmId,
                                          Long durationMs,
                                          LocalDateTime executeTime) {
        TaskExecuteLog entity = new TaskExecuteLog();
        entity.setTaskId(task == null ? null : task.getId());
        entity.setTaskName(task == null ? "" : safeText(task.getTaskName(), ""));
        entity.setStationId(station == null ? null : station.getId());
        entity.setStationName(station == null ? "" : safeText(station.getStationName(), ""));
        entity.setDeviceId(device == null ? null : device.getId());
        entity.setDeviceName(device == null ? "" : safeText(device.getDeviceName(), ""));
        entity.setExecStatus(execStatus);
        entity.setTriggerType(triggerType);
        entity.setExecMessage(execMessage);
        entity.setSnapshotId(snapshotId);
        entity.setAlarmId(alarmId);
        entity.setDurationMs(durationMs);
        entity.setExecuteTime(executeTime);
        entity.setCreateTime(executeTime);
        return entity;
    }

    private GeneratedSpectrumData buildGeneratedSpectrum(MonitorTask task) {
        long taskId = task.getId() == null ? 0L : task.getId();
        long slot = System.currentTimeMillis() / 5000L;

        double freqStart = safeDouble(task.getFreqStartMhz(), 87.0);
        double freqEnd = safeDouble(task.getFreqEndMhz(), 108.0);
        double centerFreq = round((freqStart + freqEnd) / 2.0, 3);
        double bandwidthKhz = round((freqEnd - freqStart) * 1000.0, 3);
        if (bandwidthKhz <= 0) {
            bandwidthKhz = 200.0;
        }

        String signalType = SIGNAL_TYPES[(int) ((taskId + slot) % SIGNAL_TYPES.length)];
        String channelModel = CHANNEL_MODELS[(int) ((taskId + slot) % CHANNEL_MODELS.length)];

        int phase = (int) ((taskId + slot) % 8);

        double peakPower;
        switch (phase) {
            case 0 -> peakPower = -42.0;
            case 1 -> peakPower = -45.5;
            case 2 -> peakPower = -49.0;
            case 3 -> peakPower = -53.5;
            case 4 -> peakPower = -57.5;
            case 5 -> peakPower = -55.0;
            case 6 -> peakPower = -47.2;
            default -> peakPower = -60.0;
        }

        double snr = round(8 + ((taskId + phase) % 12) + ThreadLocalRandom.current().nextDouble(0.2, 1.8), 2);
        double occupiedBandwidthKhz = round(bandwidthKhz * (0.45 + (phase * 0.04)), 2);
        if (occupiedBandwidthKhz > bandwidthKhz) {
            occupiedBandwidthKhz = bandwidthKhz * 0.92;
        }

        int alarmFlag = peakPower >= -48.0 ? 1 : 0;
        String aiLabel = signalType;

        List<Double> powerValues = buildPowerCurve(peakPower, phase);
        String powerPointsJson = toJson(powerValues);
        String waterfallRowJson = toJson(buildWaterfallRow(powerValues));

        GeneratedSpectrumData result = new GeneratedSpectrumData();
        result.setCenterFreqMhz(big(centerFreq));
        result.setBandwidthKhz(big(bandwidthKhz));
        result.setSignalType(signalType);
        result.setChannelModel(channelModel);
        result.setPeakPowerDbm(big(peakPower));
        result.setSnrDb(big(snr));
        result.setOccupiedBandwidthKhz(big(occupiedBandwidthKhz));
        result.setAiLabel(aiLabel);
        result.setAlarmFlag(alarmFlag);
        result.setPowerPointsJson(powerPointsJson);
        result.setWaterfallRowJson(waterfallRowJson);
        return result;
    }

    private List<Double> buildPowerCurve(double peakPowerDbm, int phase) {
        List<Double> values = new ArrayList<>();
        int pointCount = 80;
        double baseFloor = -78.0 + ThreadLocalRandom.current().nextDouble(-1.5, 1.5);
        double widthFactor = 7.5 + phase * 0.6;

        for (int i = 0; i < pointCount; i++) {
            double x = (i - pointCount / 2.0) / widthFactor;

            double mainPeak = 22.0 * Math.exp(-(x * x) / 1.7);
            double sidePeak1 = 5.6 * Math.exp(-Math.pow(x + 2.0, 2) / 0.8);
            double sidePeak2 = 4.3 * Math.exp(-Math.pow(x - 2.2, 2) / 0.95);
            double ripple = Math.sin(i / 3.2) * 1.1 + Math.cos(i / 6.4) * 0.75;
            double randomNoise = ThreadLocalRandom.current().nextDouble(-0.9, 0.9);

            double normalizedPeak = peakPowerDbm + 60.0;
            double power = baseFloor + normalizedPeak + mainPeak + sidePeak1 + sidePeak2 + ripple + randomNoise;
            values.add(round(power, 2));
        }

        return values;
    }

    private List<Double> buildWaterfallRow(List<Double> powerValues) {
        List<Double> row = new ArrayList<>();
        if (powerValues == null || powerValues.isEmpty()) {
            return row;
        }

        for (Double item : powerValues) {
            double value = item == null ? -80.0 : item;
            row.add(round(value + ThreadLocalRandom.current().nextDouble(-0.8, 0.8), 2));
        }
        return row;
    }

    private void updateStationAndDeviceOnlineStatus(Station station, Device device, LocalDateTime now) {
        station.setOnlineStatus(1);
        station.setUpdateTime(now);
        stationMapper.updateById(station);

        device.setRunStatus(1);
        device.setLastOnlineTime(now);
        device.setUpdateTime(now);
        deviceMapper.updateById(device);

        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.stationOnline(station.getId()),
                    "1",
                    STATION_ONLINE_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("更新站点在线缓存失败，stationId={}, error={}", station.getId(), e.getMessage());
        }
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
            log.warn("刷新实时缓存失败，stationId={}, error={}", vo.getStationId(), e.getMessage());
        }
    }

    private Long createAlarmIfNeeded(MonitorTask task,
                                     Station station,
                                     Device device,
                                     SpectrumSnapshot snapshot,
                                     LocalDateTime now) {
        long taskId = task.getId();
        long nowMs = System.currentTimeMillis();
        Long lastMs = lastAlarmTimeMap.get(taskId);

        if (lastMs != null && nowMs - lastMs < 60_000) {
            return null;
        }

        AlarmRecord alarm = new AlarmRecord();
        alarm.setAlarmNo(buildAlarmNo(taskId, now));
        alarm.setStationId(station.getId());
        alarm.setDeviceId(device.getId());
        alarm.setTaskId(task.getId());
        alarm.setSnapshotId(snapshot.getId());
        alarm.setAlarmType("SIGNAL_ABNORMAL");
        alarm.setAlarmLevel(resolveAlarmLevel(snapshot.getPeakPowerDbm()));
        alarm.setTitle(task.getTaskName() + " 频谱异常告警");
        alarm.setContent(buildAlarmContent(station, device, task, snapshot));
        alarm.setAlarmStatus(0);
        alarm.setAlarmTime(now);
        alarm.setCreateTime(now);
        alarm.setUpdateTime(now);
        alarmRecordMapper.insert(alarm);

        lastAlarmTimeMap.put(taskId, nowMs);
        refreshUnreadAlarmCountCache();
        return alarm.getId();
    }

    private String buildAlarmNo(Long taskId, LocalDateTime now) {
        return "ALM" + now.format(ALARM_NO_TIME_FORMATTER) + String.format("%03d", taskId % 1000);
    }

    private String resolveAlarmLevel(BigDecimal peakPowerDbm) {
        double power = safeDouble(peakPowerDbm, -60.0);
        if (power >= -45.0) {
            return "HIGH";
        }
        if (power >= -50.0) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private String buildAlarmContent(Station station,
                                     Device device,
                                     MonitorTask task,
                                     SpectrumSnapshot snapshot) {
        return "站点【" + safeText(station.getStationName(), "未知站点")
                + "】设备【" + safeText(device.getDeviceName(), "未知设备")
                + "】执行任务【" + safeText(task.getTaskName(), "未知任务")
                + "】时检测到异常信号，中心频率="
                + safeText(snapshot.getCenterFreqMhz(), "-")
                + "MHz，峰值功率="
                + safeText(snapshot.getPeakPowerDbm(), "-")
                + "dBm，识别结果="
                + safeText(snapshot.getAiLabel(), "-")
                + "。";
    }

    private void refreshUnreadAlarmCountCache() {
        try {
            Long count = alarmRecordMapper.selectCount(
                    new LambdaQueryWrapper<AlarmRecord>().eq(AlarmRecord::getAlarmStatus, 0)
            );

            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.ALARM_UNREAD_COUNT,
                    String.valueOf(count == null ? 0 : count),
                    ALARM_UNREAD_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("刷新未处理告警缓存失败，error={}", e.getMessage());
        }
    }

    private RealtimeSpectrumVO buildRealtimeVO(SpectrumSnapshot snapshot,
                                               Station station,
                                               Device device,
                                               MonitorTask task) {
        RealtimeSpectrumVO vo = new RealtimeSpectrumVO();
        vo.setId(snapshot.getId());
        vo.setStationId(snapshot.getStationId());
        vo.setStationName(station.getStationName());
        vo.setDeviceId(snapshot.getDeviceId());
        vo.setDeviceName(device.getDeviceName());
        vo.setTaskId(snapshot.getTaskId());
        vo.setTaskName(task.getTaskName());
        vo.setCenterFreqMhz(snapshot.getCenterFreqMhz());
        vo.setBandwidthKhz(snapshot.getBandwidthKhz());
        vo.setSignalType(snapshot.getSignalType());
        vo.setChannelModel(snapshot.getChannelModel());
        vo.setPeakPowerDbm(snapshot.getPeakPowerDbm());
        vo.setSnrDb(snapshot.getSnrDb());
        vo.setOccupiedBandwidthKhz(snapshot.getOccupiedBandwidthKhz());
        vo.setAiLabel(snapshot.getAiLabel());
        vo.setAlarmFlag(snapshot.getAlarmFlag());
        vo.setPowerPointsJson(snapshot.getPowerPointsJson());
        vo.setWaterfallRowJson(snapshot.getWaterfallRowJson());
        vo.setCaptureTime(snapshot.getCaptureTime());
        vo.setCreateTime(snapshot.getCreateTime());
        return vo;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "[]";
        }
    }

    private BigDecimal big(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private double safeDouble(BigDecimal value, double defaultValue) {
        return value == null ? defaultValue : value.doubleValue();
    }

    private String safeText(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private double round(double value, int scale) {
        BigDecimal bigDecimal = BigDecimal.valueOf(value);
        return bigDecimal.setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    @Data
    public static class ExecuteResult {
        private boolean success;
        private Long snapshotId;
        private Long alarmId;
        private Long durationMs;
        private String message;
    }

    @Data
    private static class GeneratedSpectrumData {
        private BigDecimal centerFreqMhz;
        private BigDecimal bandwidthKhz;
        private String signalType;
        private String channelModel;
        private BigDecimal peakPowerDbm;
        private BigDecimal snrDb;
        private BigDecimal occupiedBandwidthKhz;
        private String aiLabel;
        private Integer alarmFlag;
        private String powerPointsJson;
        private String waterfallRowJson;
    }
}