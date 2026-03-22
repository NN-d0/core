package com.radio.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.radio.core.dto.DeviceSaveRequest;
import com.radio.core.dto.StationSaveRequest;
import com.radio.core.dto.TaskSaveRequest;
import com.radio.core.entity.Device;
import com.radio.core.entity.MonitorTask;
import com.radio.core.entity.Station;
import com.radio.core.exception.BusinessException;
import com.radio.core.mapper.DeviceMapper;
import com.radio.core.mapper.MonitorTaskMapper;
import com.radio.core.mapper.StationMapper;
import com.radio.core.vo.DeviceListVO;
import com.radio.core.vo.PageResult;
import com.radio.core.vo.TaskListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 核心管理服务：站点/设备/任务 CRUD + 分页
 */
@Service
@RequiredArgsConstructor
public class CoreManageService {

    private final DeviceMapper deviceMapper;
    private final StationMapper stationMapper;
    private final MonitorTaskMapper monitorTaskMapper;

    /**
     * 新增站点
     */
    public Long createStation(StationSaveRequest request) {
        validateStationCodeUnique(request.getStationCode(), null);

        Station station = new Station();
        station.setStationCode(request.getStationCode());
        station.setStationName(request.getStationName());
        station.setLongitude(request.getLongitude());
        station.setLatitude(request.getLatitude());
        station.setLocationText(request.getLocationText());
        station.setOnlineStatus(0);
        station.setCreateTime(LocalDateTime.now());
        station.setUpdateTime(LocalDateTime.now());

        stationMapper.insert(station);
        return station.getId();
    }

    /**
     * 设备分页
     */
    public PageResult<DeviceListVO> pageDevices(long current,
                                                long size,
                                                Long stationId,
                                                Integer runStatus,
                                                String keyword) {
        Page<Device> page = new Page<>(current, size);

        LambdaQueryWrapper<Device> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, Device::getStationId, stationId)
                .eq(runStatus != null, Device::getRunStatus, runStatus)
                .and(keyword != null && !keyword.isBlank(), w -> w
                        .like(Device::getDeviceCode, keyword)
                        .or()
                        .like(Device::getDeviceName, keyword))
                .orderByDesc(Device::getId);

        Page<Device> resultPage = deviceMapper.selectPage(page, wrapper);
        List<Device> records = resultPage.getRecords();

        if (records == null || records.isEmpty()) {
            PageResult<DeviceListVO> result = new PageResult<>();
            result.setCurrent(resultPage.getCurrent());
            result.setSize(resultPage.getSize());
            result.setTotal(resultPage.getTotal());
            result.setPages(resultPage.getPages());
            result.setRecords(Collections.emptyList());
            return result;
        }

        Set<Long> stationIds = records.stream()
                .map(Device::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> stationNameMap = buildStationNameMap(stationIds);

        List<DeviceListVO> voList = new ArrayList<>();
        for (Device device : records) {
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
            voList.add(vo);
        }

        Page<DeviceListVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(voList);
        return PageResult.of(voPage);
    }

    /**
     * 新增设备
     */
    public void createDevice(DeviceSaveRequest request) {
        validateStationExists(request.getStationId());
        validateDeviceCodeUnique(request.getDeviceCode(), null);

        Device device = new Device();
        device.setDeviceCode(request.getDeviceCode());
        device.setDeviceName(request.getDeviceName());
        device.setStationId(request.getStationId());
        device.setDeviceType(request.getDeviceType());
        device.setIpAddr(request.getIpAddr());
        device.setRunStatus(request.getRunStatus());
        device.setRemark(request.getRemark());
        device.setCreateTime(LocalDateTime.now());
        device.setUpdateTime(LocalDateTime.now());

        deviceMapper.insert(device);
    }

    /**
     * 修改设备
     */
    public void updateDevice(DeviceSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(400, "设备ID不能为空");
        }

        Device dbDevice = deviceMapper.selectById(request.getId());
        if (dbDevice == null) {
            throw new BusinessException(404, "设备不存在");
        }

        validateStationExists(request.getStationId());
        validateDeviceCodeUnique(request.getDeviceCode(), request.getId());

        dbDevice.setDeviceCode(request.getDeviceCode());
        dbDevice.setDeviceName(request.getDeviceName());
        dbDevice.setStationId(request.getStationId());
        dbDevice.setDeviceType(request.getDeviceType());
        dbDevice.setIpAddr(request.getIpAddr());
        dbDevice.setRunStatus(request.getRunStatus());
        dbDevice.setRemark(request.getRemark());
        dbDevice.setUpdateTime(LocalDateTime.now());

        deviceMapper.updateById(dbDevice);
    }

    /**
     * 删除设备
     */
    public void deleteDevice(Long id) {
        if (id == null) {
            throw new BusinessException(400, "设备ID不能为空");
        }

        Device dbDevice = deviceMapper.selectById(id);
        if (dbDevice == null) {
            throw new BusinessException(404, "设备不存在");
        }

        Long taskCount = monitorTaskMapper.selectCount(
                new LambdaQueryWrapper<MonitorTask>()
                        .eq(MonitorTask::getDeviceId, id)
        );

        if (taskCount != null && taskCount > 0) {
            throw new BusinessException(400, "该设备已被任务使用，不能删除");
        }

        deviceMapper.deleteById(id);
    }

    /**
     * 任务分页
     */
    public PageResult<TaskListVO> pageTasks(long current,
                                            long size,
                                            Long stationId,
                                            Integer taskStatus,
                                            String keyword) {
        Page<MonitorTask> page = new Page<>(current, size);

        LambdaQueryWrapper<MonitorTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(stationId != null, MonitorTask::getStationId, stationId)
                .eq(taskStatus != null, MonitorTask::getTaskStatus, taskStatus)
                .and(keyword != null && !keyword.isBlank(), w -> w.like(MonitorTask::getTaskName, keyword))
                .orderByDesc(MonitorTask::getId);

        Page<MonitorTask> resultPage = monitorTaskMapper.selectPage(page, wrapper);
        List<MonitorTask> records = resultPage.getRecords();

        if (records == null || records.isEmpty()) {
            PageResult<TaskListVO> result = new PageResult<>();
            result.setCurrent(resultPage.getCurrent());
            result.setSize(resultPage.getSize());
            result.setTotal(resultPage.getTotal());
            result.setPages(resultPage.getPages());
            result.setRecords(Collections.emptyList());
            return result;
        }

        Set<Long> stationIds = records.stream()
                .map(MonitorTask::getStationId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Long> deviceIds = records.stream()
                .map(MonitorTask::getDeviceId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> stationNameMap = buildStationNameMap(stationIds);
        Map<Long, String> deviceNameMap = buildDeviceNameMap(deviceIds);

        List<TaskListVO> voList = new ArrayList<>();
        for (MonitorTask task : records) {
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
            voList.add(vo);
        }

        Page<TaskListVO> voPage = new Page<>(resultPage.getCurrent(), resultPage.getSize(), resultPage.getTotal());
        voPage.setRecords(voList);
        return PageResult.of(voPage);
    }

    /**
     * 新增任务
     */
    public void createTask(TaskSaveRequest request) {
        validateStationExists(request.getStationId());
        validateDeviceForStation(request.getDeviceId(), request.getStationId());
        validateTaskFrequency(request.getFreqStartMhz(), request.getFreqEndMhz());

        MonitorTask task = new MonitorTask();
        task.setTaskName(request.getTaskName());
        task.setStationId(request.getStationId());
        task.setDeviceId(request.getDeviceId());
        task.setFreqStartMhz(request.getFreqStartMhz());
        task.setFreqEndMhz(request.getFreqEndMhz());
        task.setSampleRateKhz(request.getSampleRateKhz());
        task.setAlgorithmMode(request.getAlgorithmMode());
        task.setTaskStatus(request.getTaskStatus());
        task.setCronExpr(request.getCronExpr());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());

        monitorTaskMapper.insert(task);
    }

    /**
     * 修改任务
     */
    public void updateTask(TaskSaveRequest request) {
        if (request.getId() == null) {
            throw new BusinessException(400, "任务ID不能为空");
        }

        MonitorTask dbTask = monitorTaskMapper.selectById(request.getId());
        if (dbTask == null) {
            throw new BusinessException(404, "任务不存在");
        }

        validateStationExists(request.getStationId());
        validateDeviceForStation(request.getDeviceId(), request.getStationId());
        validateTaskFrequency(request.getFreqStartMhz(), request.getFreqEndMhz());

        dbTask.setTaskName(request.getTaskName());
        dbTask.setStationId(request.getStationId());
        dbTask.setDeviceId(request.getDeviceId());
        dbTask.setFreqStartMhz(request.getFreqStartMhz());
        dbTask.setFreqEndMhz(request.getFreqEndMhz());
        dbTask.setSampleRateKhz(request.getSampleRateKhz());
        dbTask.setAlgorithmMode(request.getAlgorithmMode());
        dbTask.setTaskStatus(request.getTaskStatus());
        dbTask.setCronExpr(request.getCronExpr());
        dbTask.setUpdateTime(LocalDateTime.now());

        monitorTaskMapper.updateById(dbTask);
    }

    /**
     * 删除任务
     */
    public void deleteTask(Long id) {
        if (id == null) {
            throw new BusinessException(400, "任务ID不能为空");
        }

        MonitorTask dbTask = monitorTaskMapper.selectById(id);
        if (dbTask == null) {
            throw new BusinessException(404, "任务不存在");
        }

        monitorTaskMapper.deleteById(id);
    }

    private void validateStationExists(Long stationId) {
        Station station = stationMapper.selectById(stationId);
        if (station == null) {
            throw new BusinessException(404, "所属站点不存在");
        }
    }

    private void validateStationCodeUnique(String stationCode, Long excludeId) {
        Long count = stationMapper.selectCount(
                new LambdaQueryWrapper<Station>()
                        .eq(Station::getStationCode, stationCode)
                        .ne(excludeId != null, Station::getId, excludeId)
        );

        if (count != null && count > 0) {
            throw new BusinessException(400, "站点编码已存在");
        }
    }

    private void validateDeviceCodeUnique(String deviceCode, Long excludeId) {
        Long count = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getDeviceCode, deviceCode)
                        .ne(excludeId != null, Device::getId, excludeId)
        );

        if (count != null && count > 0) {
            throw new BusinessException(400, "设备编码已存在");
        }
    }

    private void validateDeviceForStation(Long deviceId, Long stationId) {
        Device device = deviceMapper.selectById(deviceId);
        if (device == null) {
            throw new BusinessException(404, "所属设备不存在");
        }

        if (!Objects.equals(device.getStationId(), stationId)) {
            throw new BusinessException(400, "设备与站点不匹配");
        }
    }

    private void validateTaskFrequency(BigDecimal start, BigDecimal end) {
        if (start == null || end == null) {
            throw new BusinessException(400, "频率范围不能为空");
        }

        if (start.compareTo(end) >= 0) {
            throw new BusinessException(400, "结束频率必须大于起始频率");
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

        return stationList.stream().collect(Collectors.toMap(Station::getId, Station::getStationName, (a, b) -> a));
    }

    private Map<Long, String> buildDeviceNameMap(Set<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Device> deviceList = deviceMapper.selectBatchIds(deviceIds);
        if (deviceList == null || deviceList.isEmpty()) {
            return Collections.emptyMap();
        }

        return deviceList.stream().collect(Collectors.toMap(Device::getId, Device::getDeviceName, (a, b) -> a));
    }
}