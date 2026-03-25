package com.radio.core.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.radio.core.entity.Device;
import com.radio.core.mapper.DeviceMapper;
import com.radio.core.service.RuntimeStatusSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 在线状态离线巡检调度器
 *
 * 规则：
 * 1. 每隔一段时间扫描一次设备状态
 * 2. 如果设备当前 run_status = 1，但 last_online_time 为空，或早于离线阈值，则判定设备离线
 * 3. 设备离线后，再重新计算所属站点是否仍在线
 *
 * 说明：
 * - 任务状态表示“业务调度状态”
 * - 设备/站点在线状态表示“真实数据流存活状态”
 * - 两者有关联，但不完全相同
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuntimeOfflineCheckScheduler {

    private final DeviceMapper deviceMapper;
    private final RuntimeStatusSyncService runtimeStatusSyncService;

    @Value("${monitor.offline-timeout-seconds:30}")
    private long offlineTimeoutSeconds;

    /**
     * 每隔 5 秒巡检一次
     */
    @Scheduled(fixedDelayString = "${monitor.offline-check-interval-ms:5000}")
    public void scanOfflineDevices() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.minusSeconds(offlineTimeoutSeconds);

        List<Device> staleOnlineDevices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getRunStatus, 1)
                        .and(wrapper -> wrapper
                                .isNull(Device::getLastOnlineTime)
                                .or()
                                .lt(Device::getLastOnlineTime, deadline))
                        .orderByAsc(Device::getId)
        );

        if (staleOnlineDevices == null || staleOnlineDevices.isEmpty()) {
            return;
        }

        Set<Long> affectedStationIds = new LinkedHashSet<>();

        for (Device device : staleOnlineDevices) {
            try {
                runtimeStatusSyncService.markDeviceOffline(device, now);
                if (device.getStationId() != null) {
                    affectedStationIds.add(device.getStationId());
                }
            } catch (Exception e) {
                log.warn("设备离线巡检处理失败：deviceId={}, error={}", device.getId(), e.getMessage());
            }
        }

        for (Long stationId : affectedStationIds) {
            try {
                runtimeStatusSyncService.refreshStationOnlineStatus(stationId, now);
            } catch (Exception e) {
                log.warn("站点在线状态刷新失败：stationId={}, error={}", stationId, e.getMessage());
            }
        }

        log.info("离线巡检完成：offlineTimeoutSeconds={}, offlineDeviceCount={}, affectedStationCount={}",
                offlineTimeoutSeconds, staleOnlineDevices.size(), affectedStationIds.size());
    }
}