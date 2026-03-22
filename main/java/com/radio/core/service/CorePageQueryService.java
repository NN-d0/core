package com.radio.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.radio.core.entity.AlarmRecord;
import com.radio.core.entity.Device;
import com.radio.core.entity.MonitorTask;
import com.radio.core.entity.SpectrumSnapshot;
import com.radio.core.entity.Station;
import com.radio.core.exception.BusinessException;
import com.radio.core.mapper.AlarmRecordMapper;
import com.radio.core.mapper.DeviceMapper;
import com.radio.core.mapper.MonitorTaskMapper;
import com.radio.core.mapper.SpectrumSnapshotMapper;
import com.radio.core.mapper.StationMapper;
import com.radio.core.vo.AlarmListVO;
import com.radio.core.vo.AlarmMapPointVO;
import com.radio.core.vo.HistorySnapshotVO;
import com.radio.core.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 分页查询服务
 */
@Service
@RequiredArgsConstructor
public class CorePageQueryService {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlarmRecordMapper alarmRecordMapper;
    private final StationMapper stationMapper;
    private final DeviceMapper deviceMapper;
    private final MonitorTaskMapper monitorTaskMapper;
    private final SpectrumSnapshotMapper spectrumSnapshotMapper;

    /**
     * 告警分页
     */
    public PageResult<AlarmListVO> pageAlarms(long current,
                                              long size,
                                              Integer alarmStatus,
                                              Long stationId,
                                              String keyword) {
        Page<AlarmRecord> page = new Page<>(current, size);

        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(alarmStatus != null, AlarmRecord::getAlarmStatus, alarmStatus)
                .eq(stationId != null, AlarmRecord::getStationId, stationId)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(AlarmRecord::getAlarmNo, keyword)
                        .or()
                        .like(AlarmRecord::getTitle, keyword))
                .orderByDesc(AlarmRecord::getAlarmTime)
                .orderByDesc(AlarmRecord::getId);

        Page<AlarmRecord> resultPage = alarmRecordMapper.selectPage(page, wrapper);
        List<AlarmRecord> records = resultPage.getRecords();

        if (records == null || records.isEmpty()) {
            PageResult<AlarmListVO> result = new PageResult<>();
            result.setCurrent(resultPage.getCurrent());
            result.setSize(resultPage.getSize());
            result.setTotal(resultPage.getTotal());
            result.setPages(resultPage.getPages());
            result.setRecords(Collections.emptyList());
            return result;
        }

        Set<Long> stationIds = records.stream()
                .map(AlarmRecord::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> deviceIds = records.stream()
                .map(AlarmRecord::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> stationNameMap = buildStationNameMap(stationIds);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(deviceIds);

        List<AlarmListVO> voList = new ArrayList<>();
        for (AlarmRecord alarm : records) {
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
            voList.add(vo);
        }

        Page<AlarmListVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(voList);
        return PageResult.of(voPage);
    }

    /**
     * 历史快照分页
     */
    public PageResult<HistorySnapshotVO> pageHistorySnapshots(long current,
                                                              long size,
                                                              Long stationId,
                                                              String signalType,
                                                              String startTime,
                                                              String endTime) {
        LocalDateTime startDateTime = parseDateTime(startTime);
        LocalDateTime endDateTime = parseDateTime(endTime);

        Page<SpectrumSnapshot> page = new Page<>(current, size);

        LambdaQueryWrapper<SpectrumSnapshot> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, SpectrumSnapshot::getStationId, stationId)
                .eq(signalType != null && !signalType.isBlank(), SpectrumSnapshot::getSignalType, signalType)
                .ge(startDateTime != null, SpectrumSnapshot::getCaptureTime, startDateTime)
                .le(endDateTime != null, SpectrumSnapshot::getCaptureTime, endDateTime)
                .orderByDesc(SpectrumSnapshot::getCaptureTime)
                .orderByDesc(SpectrumSnapshot::getId);

        Page<SpectrumSnapshot> resultPage = spectrumSnapshotMapper.selectPage(page, wrapper);
        List<SpectrumSnapshot> records = resultPage.getRecords();

        if (records == null || records.isEmpty()) {
            PageResult<HistorySnapshotVO> result = new PageResult<>();
            result.setCurrent(resultPage.getCurrent());
            result.setSize(resultPage.getSize());
            result.setTotal(resultPage.getTotal());
            result.setPages(resultPage.getPages());
            result.setRecords(Collections.emptyList());
            return result;
        }

        Set<Long> stationIds = records.stream()
                .map(SpectrumSnapshot::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> deviceIds = records.stream()
                .map(SpectrumSnapshot::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> taskIds = records.stream()
                .map(SpectrumSnapshot::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> stationNameMap = buildStationNameMap(stationIds);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(deviceIds);
        Map<Long, String> taskNameMap = buildTaskNameMap(taskIds);

        List<HistorySnapshotVO> voList = new ArrayList<>();
        for (SpectrumSnapshot snapshot : records) {
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
            voList.add(vo);
        }

        Page<HistorySnapshotVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(voList);
        return PageResult.of(voPage);
    }

    /**
     * 告警地图表格分页
     *
     * 修复说明：
     * 1. 先筛出“真正可上地图”的告警（站点存在且经纬度不为空）
     * 2. 再基于筛选后的结果做内存分页
     * 3. 保证当前页列表与地图点位一一对应
     * 4. 翻页时地图严格只展示当前页数据
     */
    public PageResult<AlarmMapPointVO> pageAlarmMapPoints(long current, long size, Integer alarmStatus) {
        long safeCurrent = current <= 0 ? 1 : current;
        long safeSize = size <= 0 ? 10 : size;

        LambdaQueryWrapper<AlarmRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(alarmStatus != null, AlarmRecord::getAlarmStatus, alarmStatus)
                .orderByDesc(AlarmRecord::getAlarmTime)
                .orderByDesc(AlarmRecord::getId);

        List<AlarmRecord> allAlarmList = alarmRecordMapper.selectList(wrapper);
        if (allAlarmList == null || allAlarmList.isEmpty()) {
            return buildEmptyPageResult(safeCurrent, safeSize);
        }

        Set<Long> stationIds = allAlarmList.stream()
                .map(AlarmRecord::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> deviceIds = allAlarmList.stream()
                .map(AlarmRecord::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, Station> stationMap = buildStationMap(stationIds);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(deviceIds);

        // 先过滤出真正能展示在地图上的告警
        List<AlarmRecord> validAlarmList = allAlarmList.stream()
                .filter(alarm -> {
                    Station station = stationMap.get(alarm.getStationId());
                    return canDisplayOnMap(station);
                })
                .collect(Collectors.toList());

        if (validAlarmList.isEmpty()) {
            return buildEmptyPageResult(safeCurrent, safeSize);
        }

        long total = validAlarmList.size();
        long pages = (total + safeSize - 1) / safeSize;

        long from = (safeCurrent - 1) * safeSize;
        if (from >= total) {
            PageResult<AlarmMapPointVO> result = new PageResult<>();
            result.setCurrent(safeCurrent);
            result.setSize(safeSize);
            result.setTotal(total);
            result.setPages(pages);
            result.setRecords(Collections.emptyList());
            return result;
        }

        int fromIndex = (int) from;
        int toIndex = (int) Math.min(from + safeSize, total);

        List<AlarmRecord> pageRecords = validAlarmList.subList(fromIndex, toIndex);

        List<AlarmMapPointVO> voList = new ArrayList<>();
        for (AlarmRecord alarm : pageRecords) {
            Station station = stationMap.get(alarm.getStationId());
            if (!canDisplayOnMap(station)) {
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
            voList.add(vo);
        }

        PageResult<AlarmMapPointVO> result = new PageResult<>();
        result.setCurrent(safeCurrent);
        result.setSize(safeSize);
        result.setTotal(total);
        result.setPages(pages);
        result.setRecords(voList);
        return result;
    }

    private boolean canDisplayOnMap(Station station) {
        return station != null
                && station.getLongitude() != null
                && station.getLatitude() != null;
    }

    private PageResult<AlarmMapPointVO> buildEmptyPageResult(long current, long size) {
        PageResult<AlarmMapPointVO> result = new PageResult<>();
        result.setCurrent(current);
        result.setSize(size);
        result.setTotal(0L);
        result.setPages(0L);
        result.setRecords(Collections.emptyList());
        return result;
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