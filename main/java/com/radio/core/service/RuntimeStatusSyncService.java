package com.radio.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.radio.core.constant.RedisKeyConstants;
import com.radio.core.entity.Device;
import com.radio.core.entity.MonitorTask;
import com.radio.core.entity.Station;
import com.radio.core.mapper.DeviceMapper;
import com.radio.core.mapper.MonitorTaskMapper;
import com.radio.core.mapper.StationMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 运行状态同步服务
 *
 * 目标：
 * 1. 任务启动后，设备/站点状态立即同步为运行中/在线
 * 2. 任务停止后，重新计算设备/站点状态，避免页面出现“任务停了但设备还在运行”的不一致
 * 3. 采集成功后，继续把设备/站点刷新为在线，保证实时链路展示正常
 * 4. 配合离线巡检调度器，在超时未收到采集数据时自动判定离线
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeStatusSyncService {

    private final MonitorTaskMapper monitorTaskMapper;
    private final DeviceMapper deviceMapper;
    private final StationMapper stationMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Value("${monitor.station-online-cache-ttl-seconds:300}")
    private long stationOnlineCacheTtlSeconds;

    /**
     * 任务启动后同步状态
     */
    public void syncAfterTaskStarted(MonitorTask task) {
        if (task == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        if (task.getDeviceId() != null) {
            Device device = deviceMapper.selectById(task.getDeviceId());
            if (device != null) {
                device.setRunStatus(1);
                device.setUpdateTime(now);
                deviceMapper.updateById(device);
            }
        }

        if (task.getStationId() != null) {
            Station station = stationMapper.selectById(task.getStationId());
            if (station != null) {
                station.setOnlineStatus(1);
                station.setUpdateTime(now);
                stationMapper.updateById(station);
                updateStationOnlineCache(station.getId(), 1);
            }
        }

        log.info("任务启动后状态同步完成：taskId={}, deviceId={}, stationId={}",
                task.getId(), task.getDeviceId(), task.getStationId());
    }

    /**
     * 任务停止后同步状态
     *
     * 规则：
     * 1. 如果该设备下还有其他 task_status=1 的任务，则设备仍保持运行中
     * 2. 如果该设备下已经没有运行任务，则设备改为停止
     * 3. 如果该站点下还有任何 task_status=1 的任务，则站点仍保持在线
     * 4. 如果该站点下已经没有运行任务，则站点改为离线
     */
    public void syncAfterTaskStopped(MonitorTask task) {
        if (task == null) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        if (task.getDeviceId() != null) {
            long runningTaskCountByDevice = countRunningTasksByDevice(task.getDeviceId());

            Device device = deviceMapper.selectById(task.getDeviceId());
            if (device != null) {
                device.setRunStatus(runningTaskCountByDevice > 0 ? 1 : 0);
                device.setUpdateTime(now);
                deviceMapper.updateById(device);

                log.info("任务停止后设备状态已同步：deviceId={}, runningTaskCount={}, runStatus={}",
                        task.getDeviceId(), runningTaskCountByDevice, device.getRunStatus());
            }
        }

        if (task.getStationId() != null) {
            long runningTaskCountByStation = countRunningTasksByStation(task.getStationId());

            Station station = stationMapper.selectById(task.getStationId());
            if (station != null) {
                int onlineStatus = runningTaskCountByStation > 0 ? 1 : 0;
                station.setOnlineStatus(onlineStatus);
                station.setUpdateTime(now);
                stationMapper.updateById(station);
                updateStationOnlineCache(station.getId(), onlineStatus);

                log.info("任务停止后站点状态已同步：stationId={}, runningTaskCount={}, onlineStatus={}",
                        task.getStationId(), runningTaskCountByStation, station.getOnlineStatus());
            }
        }
    }

    /**
     * 采集成功后同步状态
     *
     * 说明：
     * 当前真实数据链路来自 Python 仿真器 -> Core Collect API，
     * 所以只要采集成功，就应保证设备/站点处于在线展示状态。
     */
    public void syncAfterCollectSuccess(Station station, Device device, LocalDateTime captureTime) {
        LocalDateTime now = LocalDateTime.now();

        if (station != null) {
            station.setOnlineStatus(1);
            station.setUpdateTime(now);
            stationMapper.updateById(station);
            updateStationOnlineCache(station.getId(), 1);
        }

        if (device != null) {
            device.setRunStatus(1);
            device.setLastOnlineTime(captureTime);
            device.setUpdateTime(now);
            deviceMapper.updateById(device);
        }
    }

    /**
     * 离线巡检时：把设备标记为离线
     */
    public void markDeviceOffline(Device device, LocalDateTime now) {
        if (device == null || device.getId() == null) {
            return;
        }

        device.setRunStatus(0);
        device.setUpdateTime(now);
        deviceMapper.updateById(device);

        log.info("设备因采集超时被判定离线：deviceId={}, deviceName={}, lastOnlineTime={}",
                device.getId(), device.getDeviceName(), device.getLastOnlineTime());
    }

    /**
     * 离线巡检后：重新计算一个站点是否仍在线
     *
     * 规则：
     * - 该站点下只要还有 run_status=1 的设备，就保持在线
     * - 否则置为离线
     */
    public void refreshStationOnlineStatus(Long stationId, LocalDateTime now) {
        if (stationId == null) {
            return;
        }

        Long onlineDeviceCount = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getStationId, stationId)
                        .eq(Device::getRunStatus, 1)
        );

        int onlineStatus = (onlineDeviceCount != null && onlineDeviceCount > 0) ? 1 : 0;

        Station station = stationMapper.selectById(stationId);
        if (station == null) {
            return;
        }

        if (station.getOnlineStatus() == null || station.getOnlineStatus() != onlineStatus) {
            station.setOnlineStatus(onlineStatus);
            station.setUpdateTime(now);
            stationMapper.updateById(station);
        }

        updateStationOnlineCache(stationId, onlineStatus);

        log.info("站点在线状态已刷新：stationId={}, onlineDeviceCount={}, onlineStatus={}",
                stationId, onlineDeviceCount, onlineStatus);
    }

    private void updateStationOnlineCache(Long stationId, Integer onlineStatus) {
        if (stationId == null || onlineStatus == null) {
            return;
        }

        try {
            stringRedisTemplate.opsForValue().set(
                    RedisKeyConstants.stationOnline(stationId),
                    String.valueOf(onlineStatus),
                    stationOnlineCacheTtlSeconds,
                    TimeUnit.SECONDS
            );
        } catch (Exception e) {
            log.warn("更新站点在线缓存失败：stationId={}, error={}", stationId, e.getMessage());
        }
    }

    private long countRunningTasksByDevice(Long deviceId) {
        Long count = monitorTaskMapper.selectCount(
                new LambdaQueryWrapper<MonitorTask>()
                        .eq(MonitorTask::getDeviceId, deviceId)
                        .eq(MonitorTask::getTaskStatus, 1)
        );
        return count == null ? 0L : count;
    }

    private long countRunningTasksByStation(Long stationId) {
        Long count = monitorTaskMapper.selectCount(
                new LambdaQueryWrapper<MonitorTask>()
                        .eq(MonitorTask::getStationId, stationId)
                        .eq(MonitorTask::getTaskStatus, 1)
        );
        return count == null ? 0L : count;
    }
}