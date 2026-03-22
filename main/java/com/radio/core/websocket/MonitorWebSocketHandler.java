package com.radio.core.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radio.core.vo.RealtimeSpectrumVO;
import com.radio.core.service.CoreQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原生 WebSocket 处理器
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private final MonitorSessionManager monitorSessionManager;
    private final CoreQueryService coreQueryService;
    private final ObjectMapper objectMapper;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long stationId = parseStationId(session.getUri());
        if (stationId == null) {
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        monitorSessionManager.addSession(stationId, session);

        // 连接建立后，先主动推一条当前最新数据
        RealtimeSpectrumVO latestData = coreQueryService.getLatestRealtimeSnapshot(stationId);
        if (latestData != null) {
            sendRealtimePayload(session, latestData);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        if (payload == null || payload.isBlank()) {
            return;
        }

        // 前端可发送 refresh，主动拉一条最新快照
        if ("refresh".equalsIgnoreCase(payload.trim())) {
            Long stationId = parseStationId(session.getUri());
            if (stationId == null) {
                return;
            }

            RealtimeSpectrumVO latestData = coreQueryService.getLatestRealtimeSnapshot(stationId);
            if (latestData != null) {
                sendRealtimePayload(session, latestData);
            }
            return;
        }

        // 前端可发送 ping，服务端返回 pong
        if ("ping".equalsIgnoreCase(payload.trim())) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("type", "pong");
            result.put("ts", LocalDateTime.now().toString());
            synchronized (session) {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(result)));
            }
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        log.warn("WebSocket传输异常，sessionId={}, error={}", session.getId(), exception.getMessage());
        monitorSessionManager.removeSession(session);
        if (session.isOpen()) {
            session.close(CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        monitorSessionManager.removeSession(session);
    }

    private void sendRealtimePayload(WebSocketSession session, RealtimeSpectrumVO realtimeData) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("type", "realtime");
        result.put("stationId", realtimeData.getStationId());
        result.put("ts", LocalDateTime.now().toString());
        result.put("data", realtimeData);

        synchronized (session) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(result)));
        }
    }

    private Long parseStationId(URI uri) {
        if (uri == null || uri.getPath() == null || uri.getPath().isBlank()) {
            return null;
        }

        String path = uri.getPath();
        String[] segments = path.split("/");
        if (segments.length == 0) {
            return null;
        }

        String last = segments[segments.length - 1];
        if (last == null || last.isBlank()) {
            return null;
        }

        try {
            return Long.parseLong(last);
        } catch (Exception e) {
            return null;
        }
    }
}