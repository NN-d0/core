package com.radio.core.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.radio.core.entity.MonitorTask;
import com.radio.core.mapper.MonitorTaskMapper;
import com.radio.core.service.TaskDispatchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 任务执行调度器（最小可执行版）
 *
 * 规则：
 * 1. 每 1 秒扫描一次任务表
 * 2. 只执行 task_status = 1 的任务
 * 3. task_status = 0 未启动：不执行
 * 4. task_status = 2 已停止：立即停止后续调度
 * 5. cronExpr 简化支持：0/5 * * * * ?、0/10 * * * * ? 等
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorTaskExecuteScheduler {

    private static final Pattern CRON_SECONDS_PATTERN = Pattern.compile("0/(\\d+)");

    /**
     * 记录每个任务最近一次执行时间
     */
    private final ConcurrentHashMap<Long, Long> lastExecuteTimeMap = new ConcurrentHashMap<>();

    private final MonitorTaskMapper monitorTaskMapper;
    private final TaskDispatchService taskDispatchService;

    /**
     * 每 1 秒扫描一次
     */
    @Scheduled(fixedDelay = 1000)
    public void scanAndExecuteRunningTasks() {
        var runningTasks = monitorTaskMapper.selectList(
                new LambdaQueryWrapper<MonitorTask>()
                        .eq(MonitorTask::getTaskStatus, 1)
                        .orderByAsc(MonitorTask::getId)
        );

        if (runningTasks == null || runningTasks.isEmpty()) {
            lastExecuteTimeMap.clear();
            return;
        }

        Set<Long> activeTaskIds = runningTasks.stream()
                .map(MonitorTask::getId)
                .collect(Collectors.toSet());

        // 清理已停止 / 已删除任务的执行计时器
        lastExecuteTimeMap.keySet().removeIf(taskId -> !activeTaskIds.contains(taskId));

        long now = System.currentTimeMillis();

        for (MonitorTask task : runningTasks) {
            try {
                long intervalMs = resolveIntervalMs(task.getCronExpr());
                Long lastExecuteMs = lastExecuteTimeMap.get(task.getId());

                if (lastExecuteMs != null && now - lastExecuteMs < intervalMs) {
                    continue;
                }

                taskDispatchService.executeTask(task);
                lastExecuteTimeMap.put(task.getId(), now);
            } catch (Exception e) {
                log.error("任务执行失败，taskId={}, taskName={}, error={}",
                        task.getId(),
                        task.getTaskName(),
                        e.getMessage(),
                        e);
            }
        }
    }

    /**
     * 启动任务后，清掉上次执行时间，让任务尽快执行
     */
    public void resetTaskSchedule(Long taskId) {
        if (taskId != null) {
            lastExecuteTimeMap.remove(taskId);
        }
    }

    /**
     * 停止任务后，立即移除执行计时器
     */
    public void removeTaskSchedule(Long taskId) {
        if (taskId != null) {
            lastExecuteTimeMap.remove(taskId);
        }
    }

    private long resolveIntervalMs(String cronExpr) {
        if (cronExpr == null || cronExpr.isBlank()) {
            return 5000L;
        }

        Matcher matcher = CRON_SECONDS_PATTERN.matcher(cronExpr.trim());
        if (matcher.find()) {
            try {
                long seconds = Long.parseLong(matcher.group(1));
                if (seconds < 1) {
                    seconds = 5;
                }
                if (seconds > 60) {
                    seconds = 60;
                }
                return seconds * 1000L;
            } catch (Exception ignored) {
                return 5000L;
            }
        }

        return 5000L;
    }
}