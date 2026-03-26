package com.radio.core.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.RealtimeSpectrumVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 定时推送最新频谱数据
 *
 * 第二阶段优化说明：
 * 1. 将推送检查间隔从 2 秒缩短到 500ms
 * 2. 仍然按 snapshotId 去重，不会重复推送同一条数据
 * 3. 与 Python 仿真器 1 秒上报节奏配合后，页面刷新观感更顺滑
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RealtimePushScheduler {

    private final MonitorSessionManager monitorSessionManager;
    private final CoreQueryService coreQueryService;
    private final ObjectMapper objectMapper;

    /**
     * 记录每个站点最后一次已推送的 snapshotId，避免重复推送同一条数据
     */
    private final ConcurrentHashMap<Long, Long> lastPushedSnapshotMap = new ConcurrentHashMap<>();

    @Scheduled(initialDelay = 1000, fixedDelay = 500)
    public void pushLatestRealtimeData() {
        Set<Long> activeStationIds = monitorSessionManager.getActiveStationIds();
        if (activeStationIds == null || activeStationIds.isEmpty()) {
            return;
        }

        for (Long stationId : activeStationIds) {
            try {
                RealtimeSpectrumVO latestData = coreQueryService.getLatestRealtimeSnapshot(stationId);
                if (latestData == null || latestData.getId() == null) {
                    continue;
                }

                Long lastSnapshotId = lastPushedSnapshotMap.get(stationId);
                if (Objects.equals(lastSnapshotId, latestData.getId())) {
                    continue;
                }

                Map<String, Object> result = new LinkedHashMap<>();
                result.put("type", "realtime");
                result.put("stationId", latestData.getStationId());
                result.put("ts", LocalDateTime.now().toString());
                result.put("data", latestData);

                monitorSessionManager.sendToStation(stationId, objectMapper.writeValueAsString(result));
                lastPushedSnapshotMap.put(stationId, latestData.getId());
            } catch (Exception e) {
                log.warn("WebSocket 定时推送失败，stationId={}, error={}", stationId, e.getMessage());
            }
        }
    }
}
