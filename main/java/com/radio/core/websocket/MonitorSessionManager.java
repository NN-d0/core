package com.radio.core.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 会话管理器
 */
@Slf4j
@Component
public class MonitorSessionManager {

    /**
     * stationId -> (sessionId -> session)
     */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, WebSocketSession>> stationSessionMap = new ConcurrentHashMap<>();

    /**
     * sessionId -> stationId
     */
    private final ConcurrentHashMap<String, Long> sessionStationMap = new ConcurrentHashMap<>();

    public void addSession(Long stationId, WebSocketSession session) {
        if (stationId == null || session == null) {
            return;
        }

        stationSessionMap.computeIfAbsent(stationId, key -> new ConcurrentHashMap<>())
                .put(session.getId(), session);

        sessionStationMap.put(session.getId(), stationId);

        log.info("WebSocket连接建立成功，stationId={}, sessionId={}", stationId, session.getId());
    }

    public void removeSession(WebSocketSession session) {
        if (session == null) {
            return;
        }
        removeSessionById(session.getId());
    }

    public void removeSessionById(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        Long stationId = sessionStationMap.remove(sessionId);
        if (stationId == null) {
            return;
        }

        Map<String, WebSocketSession> sessionMap = stationSessionMap.get(stationId);
        if (sessionMap != null) {
            sessionMap.remove(sessionId);
            if (sessionMap.isEmpty()) {
                stationSessionMap.remove(stationId);
            }
        }

        log.info("WebSocket连接已移除，stationId={}, sessionId={}", stationId, sessionId);
    }

    public Set<Long> getActiveStationIds() {
        return new HashSet<>(stationSessionMap.keySet());
    }

    public void sendToStation(Long stationId, String text) {
        if (stationId == null || text == null) {
            return;
        }

        Map<String, WebSocketSession> sessionMap = stationSessionMap.get(stationId);
        if (sessionMap == null || sessionMap.isEmpty()) {
            return;
        }

        for (WebSocketSession session : new ArrayList<>(sessionMap.values())) {
            if (session == null || !session.isOpen()) {
                removeSession(session);
                continue;
            }

            try {
                synchronized (session) {
                    session.sendMessage(new TextMessage(text));
                }
            } catch (IOException e) {
                log.warn("WebSocket消息发送失败，stationId={}, sessionId={}, error={}",
                        stationId, session.getId(), e.getMessage());
                try {
                    session.close();
                } catch (IOException ignored) {
                }
                removeSession(session);
            }
        }
    }
}