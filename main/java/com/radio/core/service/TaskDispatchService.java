package com.radio.core.service;

import com.radio.core.entity.Device;
import com.radio.core.entity.MonitorTask;
import com.radio.core.entity.Station;
import com.radio.core.entity.TaskExecuteLog;
import com.radio.core.mapper.DeviceMapper;
import com.radio.core.mapper.StationMapper;
import com.radio.core.mapper.TaskExecuteLogMapper;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 任务调度服务（关闭自发电版）
 *
 * 当前职责：
 * 1. 保留任务启动后的“进入运行态”日志
 * 2. 保留任务停止日志
 * 3. 不再生成频谱快照
 * 4. 不再生成告警
 * 5. 不再写 Redis 最新频谱
 *
 * 当前系统唯一真实数据链路：
 * Python Simulator -> Core Collect API -> Core AI -> MySQL / Redis / Alarm / WebSocket
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskDispatchService {

    private final StationMapper stationMapper;
    private final DeviceMapper deviceMapper;
    private final TaskExecuteLogMapper taskExecuteLogMapper;

    /**
     * 兼容旧调用入口。
     *
     * 说明：
     * - 以前这里会真正执行任务并生成频谱/告警
     * - 现在这里不再“自发电”
     * - 仅写一条“任务已进入运行态，等待外部仿真器上报”的日志
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecuteResult executeTask(MonitorTask task) {
        long startMs = System.currentTimeMillis();
        LocalDateTime now = LocalDateTime.now();

        Station station = null;
        Device device = null;

        try {
            if (task == null || task.getId() == null) {
                throw new IllegalArgumentException("任务不能为空");
            }

            if (task.getStationId() != null) {
                station = stationMapper.selectById(task.getStationId());
            }
            if (task.getDeviceId() != null) {
                device = deviceMapper.selectById(task.getDeviceId());
            }

            TaskExecuteLog logEntity = buildLogEntity(
                    task,
                    station,
                    device,
                    1,
                    "SCHEDULER",
                    "任务已进入运行态，当前版本由 Python 仿真器通过 Collect API 上报数据，调度器不再自生成频谱/告警。",
                    null,
                    null,
                    System.currentTimeMillis() - startMs,
                    now
            );
            taskExecuteLogMapper.insert(logEntity);

            ExecuteResult result = new ExecuteResult();
            result.setSuccess(true);
            result.setSnapshotId(null);
            result.setAlarmId(null);
            result.setDurationMs(System.currentTimeMillis() - startMs);
            result.setMessage(logEntity.getExecMessage());

            log.info("任务运行态日志写入成功：taskId={}, taskName={}",
                    task.getId(), task.getTaskName());

            return result;
        } catch (Exception e) {
            TaskExecuteLog logEntity = buildLogEntity(
                    task,
                    station,
                    device,
                    2,
                    "SCHEDULER",
                    "任务运行态日志写入失败：" + e.getMessage(),
                    null,
                    null,
                    System.currentTimeMillis() - startMs,
                    now
            );
            taskExecuteLogMapper.insert(logEntity);

            log.error("任务运行态日志写入失败：taskId={}, taskName={}, error={}",
                    task == null ? null : task.getId(),
                    task == null ? null : task.getTaskName(),
                    e.getMessage(),
                    e);

            throw e;
        }
    }

    /**
     * 记录“任务启动后进入运行态”的日志。
     *
     * 说明：
     * - 调度器在发现任务从“非运行态”转入“运行态”时调用
     * - 只记录一次，不做频谱生成
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordSchedulerActivatedLog(MonitorTask task) {
        LocalDateTime now = LocalDateTime.now();

        Station station = null;
        Device device = null;

        if (task != null) {
            if (task.getStationId() != null) {
                station = stationMapper.selectById(task.getStationId());
            }
            if (task.getDeviceId() != null) {
                device = deviceMapper.selectById(task.getDeviceId());
            }
        }

        TaskExecuteLog logEntity = buildLogEntity(
                task,
                station,
                device,
                1,
                "SCHEDULER",
                "任务已启动并进入运行态，等待 Python 仿真器上报实时频谱数据；调度器不再自生成频谱/告警。",
                null,
                null,
                0L,
                now
        );
        taskExecuteLogMapper.insert(logEntity);

        log.info("任务激活日志写入成功：taskId={}, taskName={}",
                task == null ? null : task.getId(),
                task == null ? null : task.getTaskName());
    }

    /**
     * 记录“手动停止”日志
     */
    @Transactional(rollbackFor = Exception.class)
    public void recordManualStopLog(MonitorTask task) {
        LocalDateTime now = LocalDateTime.now();
        Station station = null;
        Device device = null;

        if (task != null) {
            if (task.getStationId() != null) {
                station = stationMapper.selectById(task.getStationId());
            }
            if (task.getDeviceId() != null) {
                device = deviceMapper.selectById(task.getDeviceId());
            }
        }

        TaskExecuteLog logEntity = buildLogEntity(
                task,
                station,
                device,
                3,
                "MANUAL_STOP",
                "任务已手动停止，后续不再自动调度；当前版本真实频谱数据仅来源于 Python 仿真器上报。",
                null,
                null,
                0L,
                now
        );
        taskExecuteLogMapper.insert(logEntity);

        log.info("任务停止日志写入成功：taskId={}, taskName={}",
                task == null ? null : task.getId(),
                task == null ? null : task.getTaskName());
    }

    private TaskExecuteLog buildLogEntity(MonitorTask task,
                                          Station station,
                                          Device device,
                                          Integer execStatus,
                                          String triggerType,
                                          String execMessage,
                                          Long snapshotId,
                                          Long alarmId,
                                          Long durationMs,
                                          LocalDateTime executeTime) {
        TaskExecuteLog entity = new TaskExecuteLog();
        entity.setTaskId(task == null ? null : task.getId());
        entity.setTaskName(task == null ? "" : safeText(task.getTaskName(), ""));
        entity.setStationId(station == null ? null : station.getId());
        entity.setStationName(station == null ? "" : safeText(station.getStationName(), ""));
        entity.setDeviceId(device == null ? null : device.getId());
        entity.setDeviceName(device == null ? "" : safeText(device.getDeviceName(), ""));
        entity.setExecStatus(execStatus);
        entity.setTriggerType(triggerType);
        entity.setExecMessage(execMessage);
        entity.setSnapshotId(snapshotId);
        entity.setAlarmId(alarmId);
        entity.setDurationMs(durationMs);
        entity.setExecuteTime(executeTime);
        entity.setCreateTime(executeTime);
        return entity;
    }

    private String safeText(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    @Data
    public static class ExecuteResult {
        private boolean success;
        private Long snapshotId;
        private Long alarmId;
        private Long durationMs;
        private String message;
    }
}