package com.radio.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.radio.core.constant.RedisKeyConstants;
import com.radio.core.dto.ConfigUpdateRequest;
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
import com.radio.core.vo.AlarmListVO;
import com.radio.core.vo.AlarmMapPointVO;
import com.radio.core.vo.DeviceListVO;
import com.radio.core.vo.HistorySnapshotVO;
import com.radio.core.vo.OverviewSummaryVO;
import com.radio.core.vo.RealtimeSpectrumVO;
import com.radio.core.vo.TaskListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 核心查询服务
 */
@Service
@RequiredArgsConstructor
public class CoreQueryService {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 最新频谱缓存 TTL
     */
    private static final long REALTIME_CACHE_TTL_SECONDS = 120L;

    /**
     * 站点在线状态缓存 TTL
     */
    private static final long STATION_ONLINE_CACHE_TTL_SECONDS = 300L;

    /**
     * 未处理告警数缓存 TTL
     */
    private static final long ALARM_UNREAD_CACHE_TTL_SECONDS = 120L;

    private final StationMapper stationMapper;
    private final DeviceMapper deviceMapper;
    private final AlarmRecordMapper alarmRecordMapper;
    private final MonitorTaskMapper monitorTaskMapper;
    private final SysConfigMapper sysConfigMapper;
    private final SpectrumSnapshotMapper spectrumSnapshotMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 查询站点列表
     */
    public List<Station> listStations(Integer onlineStatus) {
        LambdaQueryWrapper<Station> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(onlineStatus != null, Station::getOnlineStatus, onlineStatus)
                .orderByAsc(Station::getId);

        List<Station> stationList = stationMapper.selectList(wrapper);
        if (stationList == null || stationList.isEmpty()) {
            return Collections.emptyList();
        }

        for (Station station : stationList) {
            cacheStationOnlineStatus(station.getId(), station.getOnlineStatus());
        }

        return stationList;
    }

    /**
     * 查询设备列表
     */
    public List<DeviceListVO> listDevices(Long stationId, Integer runStatus) {
        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, Device::getStationId, stationId)
                .eq(runStatus != null, Device::getRunStatus, runStatus)
                .orderByAsc(Device::getId);

        List<Device> deviceList = deviceMapper.selectList(wrapper);
        if (deviceList == null || deviceList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> stationIds = deviceList.stream()
                .map(Device::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> stationNameMap = buildStationNameMap(stationIds);

        List<DeviceListVO> result = new ArrayList<>();
        for (Device device : deviceList) {
            DeviceListVO vo = new DeviceListVO();
            vo.setId(device.getId());
            vo.setDeviceCode(device.getDeviceCode());
            vo.setDeviceName(device.getDeviceName());
            vo.setStationId(device.getStationId());
            vo.setStationName(stationNameMap.get(device.getStationId()));
            vo.setDeviceType(device.getDeviceType());
            vo.setIpAddr(device.getIpAddr());
            vo.setRunStatus(device.getRunStatus());
            vo.setLastOnlineTime(device.getLastOnlineTime());
            vo.setRemark(device.getRemark());
            vo.setCreateTime(device.getCreateTime());
            vo.setUpdateTime(device.getUpdateTime());
            result.add(vo);
        }

        return result;
    }

    /**
     * 查询告警列表
     */
    public List<AlarmListVO> listAlarms(Integer alarmStatus) {
        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(alarmStatus != null, AlarmRecord::getAlarmStatus, alarmStatus)
                .orderByDesc(AlarmRecord::getAlarmTime)
                .orderByDesc(AlarmRecord::getId);

        List<AlarmRecord> alarmList = alarmRecordMapper.selectList(wrapper);
        if (alarmList == null || alarmList.isEmpty()) {
            refreshUnreadAlarmCountCache();
            return Collections.emptyList();
        }

        Set<Long> stationIds = alarmList.stream()
                .map(AlarmRecord::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> deviceIds = alarmList.stream()
                .map(AlarmRecord::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> stationNameMap = buildStationNameMap(stationIds);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(deviceIds);

        List<AlarmListVO> result = new ArrayList<>();
        for (AlarmRecord alarm : alarmList) {
            AlarmListVO vo = new AlarmListVO();
            vo.setId(alarm.getId());
            vo.setAlarmNo(alarm.getAlarmNo());
            vo.setStationId(alarm.getStationId());
            vo.setStationName(stationNameMap.get(alarm.getStationId()));
            vo.setDeviceId(alarm.getDeviceId());
            vo.setDeviceName(deviceNameMap.get(alarm.getDeviceId()));
            vo.setTaskId(alarm.getTaskId());
            vo.setSnapshotId(alarm.getSnapshotId());
            vo.setAlarmType(alarm.getAlarmType());
            vo.setAlarmLevel(alarm.getAlarmLevel());
            vo.setTitle(alarm.getTitle());
            vo.setContent(alarm.getContent());
            vo.setAlarmStatus(alarm.getAlarmStatus());
            vo.setHandleUserId(alarm.getHandleUserId());
            vo.setHandleNote(alarm.getHandleNote());
            vo.setAlarmTime(alarm.getAlarmTime());
            vo.setConfirmTime(alarm.getConfirmTime());
            vo.setHandleTime(alarm.getHandleTime());
            vo.setCreateTime(alarm.getCreateTime());
            vo.setUpdateTime(alarm.getUpdateTime());
            result.add(vo);
        }

        refreshUnreadAlarmCountCache();
        return result;
    }

    /**
     * 查询任务列表
     */
    public List<TaskListVO> listTasks(Long stationId, Integer taskStatus) {
        LambdaQueryWrapper<MonitorTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, MonitorTask::getStationId, stationId)
                .eq(taskStatus != null, MonitorTask::getTaskStatus, taskStatus)
                .orderByAsc(MonitorTask::getId);

        List<MonitorTask> taskList = monitorTaskMapper.selectList(wrapper);
        if (taskList == null || taskList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> stationIds = taskList.stream()
                .map(MonitorTask::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> deviceIds = taskList.stream()
                .map(MonitorTask::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> stationNameMap = buildStationNameMap(stationIds);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(deviceIds);

        List<TaskListVO> result = new ArrayList<>();
        for (MonitorTask task : taskList) {
            TaskListVO vo = new TaskListVO();
            vo.setId(task.getId());
            vo.setTaskName(task.getTaskName());
            vo.setStationId(task.getStationId());
            vo.setStationName(stationNameMap.get(task.getStationId()));
            vo.setDeviceId(task.getDeviceId());
            vo.setDeviceName(deviceNameMap.get(task.getDeviceId()));
            vo.setFreqStartMhz(task.getFreqStartMhz());
            vo.setFreqEndMhz(task.getFreqEndMhz());
            vo.setSampleRateKhz(task.getSampleRateKhz());
            vo.setAlgorithmMode(task.getAlgorithmMode());
            vo.setTaskStatus(task.getTaskStatus());
            vo.setCronExpr(task.getCronExpr());
            vo.setCreateTime(task.getCreateTime());
            vo.setUpdateTime(task.getUpdateTime());
            result.add(vo);
        }

        return result;
    }

    /**
     * 查询系统参数列表
     */
    public List<SysConfig> listConfigs() {
        LambdaQueryWrapper<SysConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByAsc(SysConfig::getId);
        return sysConfigMapper.selectList(wrapper);
    }

    /**
     * 更新系统参数
     */
    public void updateConfig(ConfigUpdateRequest request) {
        SysConfig config = sysConfigMapper.selectOne(
                new LambdaQueryWrapper<SysConfig>()
                        .eq(SysConfig::getConfigKey, request.getConfigKey())
                        .last("limit 1")
        );

        if (config == null) {
            throw new BusinessException(404, "配置项不存在");
        }

        config.setConfigValue(request.getConfigValue());
        sysConfigMapper.updateById(config);
    }

    /**
     * 查询历史快照列表
     */
    public List<HistorySnapshotVO> listHistorySnapshots(Long stationId, String signalType, String startTime, String endTime) {
        LocalDateTime startDateTime = parseDateTime(startTime);
        LocalDateTime endDateTime = parseDateTime(endTime);

        LambdaQueryWrapper<SpectrumSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, SpectrumSnapshot::getStationId, stationId)
                .eq(signalType != null && !signalType.isBlank(), SpectrumSnapshot::getSignalType, signalType)
                .ge(startDateTime != null, SpectrumSnapshot::getCaptureTime, startDateTime)
                .le(endDateTime != null, SpectrumSnapshot::getCaptureTime, endDateTime)
                .orderByDesc(SpectrumSnapshot::getCaptureTime)
                .orderByDesc(SpectrumSnapshot::getId);

        List<SpectrumSnapshot> snapshotList = spectrumSnapshotMapper.selectList(wrapper);
        if (snapshotList == null || snapshotList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> stationIds = snapshotList.stream()
                .map(SpectrumSnapshot::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> deviceIds = snapshotList.stream()
                .map(SpectrumSnapshot::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> taskIds = snapshotList.stream()
                .map(SpectrumSnapshot::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> stationNameMap = buildStationNameMap(stationIds);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(deviceIds);
        Map<Long, String> taskNameMap = buildTaskNameMap(taskIds);

        List<HistorySnapshotVO> result = new ArrayList<>();
        for (SpectrumSnapshot snapshot : snapshotList) {
            HistorySnapshotVO vo = new HistorySnapshotVO();
            vo.setId(snapshot.getId());
            vo.setStationId(snapshot.getStationId());
            vo.setStationName(stationNameMap.get(snapshot.getStationId()));
            vo.setDeviceId(snapshot.getDeviceId());
            vo.setDeviceName(deviceNameMap.get(snapshot.getDeviceId()));
            vo.setTaskId(snapshot.getTaskId());
            vo.setTaskName(taskNameMap.get(snapshot.getTaskId()));
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
            result.add(vo);
        }

        return result;
    }

    /**
     * 查询最新实时频谱数据（Redis 优先，DB 兜底）
     */
    public RealtimeSpectrumVO getLatestRealtimeSnapshot(Long stationId) {
        RealtimeSpectrumVO cacheValue = getRealtimeSnapshotFromCache(stationId);
        if (cacheValue != null) {
            return cacheValue;
        }

        LambdaQueryWrapper<SpectrumSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, SpectrumSnapshot::getStationId, stationId)
                .orderByDesc(SpectrumSnapshot::getCaptureTime)
                .orderByDesc(SpectrumSnapshot::getId)
                .last("limit 1");

        SpectrumSnapshot snapshot = spectrumSnapshotMapper.selectOne(wrapper);
        if (snapshot == null) {
            return null;
        }

        Map<Long, String> stationNameMap = buildStationNameMap(
                snapshot.getStationId() == null ? Collections.emptySet() : Collections.singleton(snapshot.getStationId())
        );

        Map<Long, String> deviceNameMap = buildDeviceNameMap(
                snapshot.getDeviceId() == null ? Collections.emptySet() : Collections.singleton(snapshot.getDeviceId())
        );

        Map<Long, String> taskNameMap = buildTaskNameMap(
                snapshot.getTaskId() == null ? Collections.emptySet() : Collections.singleton(snapshot.getTaskId())
        );

        RealtimeSpectrumVO vo = new RealtimeSpectrumVO();
        vo.setId(snapshot.getId());
        vo.setStationId(snapshot.getStationId());
        vo.setStationName(stationNameMap.get(snapshot.getStationId()));
        vo.setDeviceId(snapshot.getDeviceId());
        vo.setDeviceName(deviceNameMap.get(snapshot.getDeviceId()));
        vo.setTaskId(snapshot.getTaskId());
        vo.setTaskName(taskNameMap.get(snapshot.getTaskId()));
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

        cacheLatestRealtime(vo);
        return vo;
    }

    /**
     * 查询站点在线状态（Redis 优先）
     */
    public Integer getStationOnlineStatus(Long stationId) {
        if (stationId == null) {
            throw new BusinessException(400, "stationId 不能为空");
        }

        try {
            String cacheValue = stringRedisTemplate.opsForValue().get(RedisKeyConstants.stationOnline(stationId));
            if (cacheValue != null) {
                return Integer.parseInt(cacheValue);
            }
        } catch (Exception ignored) {
        }

        Station station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(404, "站点不存在");
        }

        cacheStationOnlineStatus(station.getId(), station.getOnlineStatus());
        return station.getOnlineStatus();
    }

    /**
     * 查询未处理告警数（Redis 优先）
     */
    public Integer getUnreadAlarmCount() {
        try {
            String cacheValue = stringRedisTemplate.opsForValue().get(RedisKeyConstants.ALARM_UNREAD_COUNT);
            if (cacheValue != null) {
                return Integer.parseInt(cacheValue);
            }
        } catch (Exception ignored) {
        }

        return refreshUnreadAlarmCountCache();
    }

    /**
     * 首页总览统计（部分使用 Redis）
     */
    public OverviewSummaryVO getOverviewSummary() {
        OverviewSummaryVO vo = new OverviewSummaryVO();

        List<Station> stationList = stationMapper.selectList(
                new LambdaQueryWrapper<Station>().orderByAsc(Station::getId)
        );

        int stationCount = stationList == null ? 0 : stationList.size();
        int onlineStationCount = 0;

        if (stationList != null) {
            for (Station station : stationList) {
                Integer onlineStatus = getStationOnlineStatus(station.getId());
                if (onlineStatus != null && onlineStatus == 1) {
                    onlineStationCount++;
                }
            }
        }

        int offlineStationCount = Math.max(stationCount - onlineStationCount, 0);

        int deviceCount = countToInt(deviceMapper.selectCount(new LambdaQueryWrapper<>()));
        int runningDeviceCount = countToInt(
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getRunStatus, 1))
        );
        int stopDeviceCount = countToInt(
                deviceMapper.selectCount(new LambdaQueryWrapper<Device>().eq(Device::getRunStatus, 0))
        );

        int alarmCount = countToInt(alarmRecordMapper.selectCount(new LambdaQueryWrapper<>()));
        int unreadAlarmCount = getUnreadAlarmCount();
        int confirmedAlarmCount = countToInt(
                alarmRecordMapper.selectCount(new LambdaQueryWrapper<AlarmRecord>().eq(AlarmRecord::getAlarmStatus, 1))
        );
        int handledAlarmCount = countToInt(
                alarmRecordMapper.selectCount(new LambdaQueryWrapper<AlarmRecord>().eq(AlarmRecord::getAlarmStatus, 2))
        );

        vo.setStationCount(stationCount);
        vo.setOnlineStationCount(onlineStationCount);
        vo.setOfflineStationCount(offlineStationCount);
        vo.setDeviceCount(deviceCount);
        vo.setRunningDeviceCount(runningDeviceCount);
        vo.setStopDeviceCount(stopDeviceCount);
        vo.setAlarmCount(alarmCount);
        vo.setUnreadAlarmCount(unreadAlarmCount);
        vo.setConfirmedAlarmCount(confirmedAlarmCount);
        vo.setHandledAlarmCount(handledAlarmCount);
        return vo;
    }

    /**
     * 告警地图点位列表
     */
    public List<AlarmMapPointVO> listAlarmMapPoints(Integer alarmStatus) {
        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(alarmStatus != null, AlarmRecord::getAlarmStatus, alarmStatus)
                .orderByDesc(AlarmRecord::getAlarmTime)
                .orderByDesc(AlarmRecord::getId);

        List<AlarmRecord> alarmList = alarmRecordMapper.selectList(wrapper);
        if (alarmList == null || alarmList.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> stationIds = alarmList.stream()
                .map(AlarmRecord::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> deviceIds = alarmList.stream()
                .map(AlarmRecord::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Station> stationMap = buildStationMap(stationIds);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(deviceIds);

        List<AlarmMapPointVO> result = new ArrayList<>();
        for (AlarmRecord alarm : alarmList) {
            Station station = stationMap.get(alarm.getStationId());
            if (station == null || station.getLongitude() == null || station.getLatitude() == null) {
                continue;
            }

            AlarmMapPointVO vo = new AlarmMapPointVO();
            vo.setId(alarm.getId());
            vo.setAlarmNo(alarm.getAlarmNo());
            vo.setStationId(alarm.getStationId());
            vo.setStationName(station.getStationName());
            vo.setLongitude(station.getLongitude());
            vo.setLatitude(station.getLatitude());
            vo.setLocationText(station.getLocationText());
            vo.setDeviceId(alarm.getDeviceId());
            vo.setDeviceName(deviceNameMap.get(alarm.getDeviceId()));
            vo.setAlarmType(alarm.getAlarmType());
            vo.setAlarmLevel(alarm.getAlarmLevel());
            vo.setTitle(alarm.getTitle());
            vo.setContent(alarm.getContent());
            vo.setAlarmStatus(alarm.getAlarmStatus());
            vo.setAlarmTime(alarm.getAlarmTime());
            result.add(vo);
        }

        return result;
    }

    private RealtimeSpectrumVO getRealtimeSnapshotFromCache(Long stationId) {
        String key = stationId != null
                ? RedisKeyConstants.realtimeLatest(stationId)
                : RedisKeyConstants.REALTIME_LATEST_GLOBAL;

        try {
            String cacheValue = stringRedisTemplate.opsForValue().get(key);
            if (cacheValue == null || cacheValue.isBlank()) {
                return null;
            }
            return objectMapper.readValue(cacheValue, RealtimeSpectrumVO.class);
        } catch (Exception ignored) {
            return null;
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
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
        }
    }

    private Integer refreshUnreadAlarmCountCache() {
        int count = queryUnreadAlarmCount();
        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.ALARM_UNREAD_COUNT,
                    String.valueOf(count),
                    ALARM_UNREAD_CACHE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
        } catch (Exception ignored) {
        }
        return count;
    }

    private int queryUnreadAlarmCount() {
        Long count = alarmRecordMapper.selectCount(
                new LambdaQueryWrapper<AlarmRecord>()
                        .eq(AlarmRecord::getAlarmStatus, 0)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countToInt(Long count) {
        return count == null ? 0 : count.intValue();
    }

    private LocalDateTime parseDateTime(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            return LocalDateTime.parse(text, DATETIME_FORMATTER);
        } catch (Exception e) {
            throw new BusinessException(400, "时间格式错误，请使用 yyyy-MM-dd HH:mm:ss");
        }
    }

    private Map<Long, String> buildStationNameMap(Set<Long> stationIds) {
        if (stationIds == null || stationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Station> stationList = stationMapper.selectBatchIds(stationIds);
        if (stationList == null || stationList.isEmpty()) {
            return Collections.emptyMap();
        }

        return stationList.stream()
                .collect(Collectors.toMap(Station::getId, Station::getStationName, (a, b) -> a));
    }

    private Map<Long, Station> buildStationMap(Set<Long> stationIds) {
        if (stationIds == null || stationIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Station> stationList = stationMapper.selectBatchIds(stationIds);
        if (stationList == null || stationList.isEmpty()) {
            return Collections.emptyMap();
        }

        return stationList.stream()
                .collect(Collectors.toMap(Station::getId, item -> item, (a, b) -> a));
    }

    private Map<Long, String> buildDeviceNameMap(Set<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Device> deviceList = deviceMapper.selectBatchIds(deviceIds);
        if (deviceList == null || deviceList.isEmpty()) {
            return Collections.emptyMap();
        }

        return deviceList.stream()
                .collect(Collectors.toMap(Device::getId, Device::getDeviceName, (a, b) -> a));
    }

    private Map<Long, String> buildTaskNameMap(Set<Long> taskIds) {
        if (taskIds == null || taskIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<MonitorTask> taskList = monitorTaskMapper.selectBatchIds(taskIds);
        if (taskList == null || taskList.isEmpty()) {
            return Collections.emptyMap();
        }

        return taskList.stream()
                .collect(Collectors.toMap(MonitorTask::getId, MonitorTask::getTaskName, (a, b) -> a));
    }
}