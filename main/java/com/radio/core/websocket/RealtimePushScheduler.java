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
 * 说明：
 * - WebSocket 优先推送
 * - 推送内容来自现有实时接口逻辑（Redis 优先，DB 兜底）
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

    @Scheduled(fixedDelay = 2000)
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
                log.warn("WebSocket定时推送失败，stationId={}, error={}", stationId, e.getMessage());
            }
        }
    }
}