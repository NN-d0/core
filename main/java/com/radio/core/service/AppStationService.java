package com.radio.core.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.radio.core.entity.Device;
import com.radio.core.entity.SpectrumSnapshot;
import com.radio.core.entity.Station;
import com.radio.core.mapper.DeviceMapper;
import com.radio.core.mapper.SpectrumSnapshotMapper;
import com.radio.core.mapper.StationMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * APP 端站点状态服务
 *
 * 本次收口目标：
 * 1. 将 AppStationController 中直接 JdbcTemplate 查询下沉到 Service
 * 2. 统一通过 Mapper + Entity 查询站点、设备、最新快照
 * 3. 保持 APP 端返回字段结构尽量不变，避免前端额外改动
 */
@Service
@RequiredArgsConstructor
public class AppStationService {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final StationMapper stationMapper;
    private final DeviceMapper deviceMapper;
    private final SpectrumSnapshotMapper spectrumSnapshotMapper;

    /**
     * APP 端站点状态列表
     */
    public List<Map<String, Object>> listStationStatus() {
        List<Station> stationList = stationMapper.selectList(
                new LambdaQueryWrapper<Station>().orderByAsc(Station::getId)
        );

        if (stationList == null || stationList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();

        for (int index = 0; index < stationList.size(); index++) {
            Station station = stationList.get(index);
            Long stationId = station.getId();

            Long deviceCount = deviceMapper.selectCount(
                    new LambdaQueryWrapper<Device>()
                            .eq(Device::getStationId, stationId)
            );

            Long runningDeviceCount = deviceMapper.selectCount(
                    new LambdaQueryWrapper<Device>()
                            .eq(Device::getStationId, stationId)
                            .eq(Device::getRunStatus, 1)
            );

            SpectrumSnapshot latestSnapshot = spectrumSnapshotMapper.selectOne(
                    new LambdaQueryWrapper<SpectrumSnapshot>()
                            .eq(SpectrumSnapshot::getStationId, stationId)
                            .orderByDesc(SpectrumSnapshot::getCaptureTime)
                            .orderByDesc(SpectrumSnapshot::getId)
                            .last("limit 1")
            );

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", stationId);
            row.put("stationName", safeStationName(station));
            row.put("latitude", safeLatitude(station, index));
            row.put("longitude", safeLongitude(station, index));
            row.put("onlineStatus", station.getOnlineStatus() == null ? 1 : station.getOnlineStatus());
            row.put("deviceCount", deviceCount == null ? 0L : deviceCount);
            row.put("runningDeviceCount", runningDeviceCount == null ? 0L : runningDeviceCount);
            row.put("latestSnapshotTime", formatSnapshotTime(latestSnapshot));

            result.add(row);
        }

        return result;
    }

    private String safeStationName(Station station) {
        if (station == null) {
            return "";
        }
        if (station.getStationName() == null || station.getStationName().trim().isEmpty()) {
            return "站点" + station.getId();
        }
        return station.getStationName();
    }

    private BigDecimal safeLatitude(Station station, int index) {
        if (station != null && station.getLatitude() != null) {
            return station.getLatitude();
        }
        return BigDecimal.valueOf(round(22.53 + index * 0.015, 6));
    }

    private BigDecimal safeLongitude(Station station, int index) {
        if (station != null && station.getLongitude() != null) {
            return station.getLongitude();
        }
        return BigDecimal.valueOf(round(113.93 + index * 0.02, 6));
    }

    private String formatSnapshotTime(SpectrumSnapshot snapshot) {
        if (snapshot == null || snapshot.getCaptureTime() == null) {
            return "";
        }
        return snapshot.getCaptureTime().format(DATETIME_FORMATTER);
    }

    private double round(double value, int scale) {
        double factor = Math.pow(10, scale);
        return Math.round(value * factor) / factor;
    }
}