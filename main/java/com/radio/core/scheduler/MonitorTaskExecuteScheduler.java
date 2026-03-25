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
import java.util.stream.Collectors;

/**
 * 任务执行调度器（关闭自发电版）
 *
 * 当前规则：
 * 1. 每 1 秒扫描一次 monitor_task
 * 2. 只关注 task_status = 1 的运行中任务
 * 3. 任务进入运行态时，只记录一条“等待外部仿真器上报”的日志
 * 4. 不再调用 TaskDispatchService 生成频谱快照
 * 5. 不再生成告警
 * 6. 不再刷新 Redis 最新频谱
 *
 * 说明：
 * 当前版本中，“任务运行”只表示业务状态已开启，
 * 真正的数据流必须来自：
 * Python Simulator -> Core Collect API
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MonitorTaskExecuteScheduler {

    /**
     * 记录当前已经进入“运行态”的任务
     *
     * 用途：
     * 1. 避免每秒都重复写激活日志
     * 2. 任务启动后只记一次
     * 3. 任务停止后从集合中移除
     */
    private final Set<Long> activeTaskIds = ConcurrentHashMap.newKeySet();

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
            activeTaskIds.clear();
            return;
        }

        Set<Long> currentRunningIds = runningTasks.stream()
                .map(MonitorTask::getId)
                .collect(Collectors.toSet());

        // 清理那些已经不再运行的任务
        activeTaskIds.removeIf(taskId -> !currentRunningIds.contains(taskId));

        for (MonitorTask task : runningTasks) {
            try {
                if (task == null || task.getId() == null) {
                    continue;
                }

                // 只有任务第一次进入运行态时，才记录一次激活日志
                boolean firstSeenRunning = activeTaskIds.add(task.getId());
                if (!firstSeenRunning) {
                    continue;
                }

                taskDispatchService.recordSchedulerActivatedLog(task);

                log.info("任务已进入运行态（不再自发电）：taskId={}, taskName={}",
                        task.getId(), task.getTaskName());
            } catch (Exception e) {
                log.error("任务运行态处理失败：taskId={}, taskName={}, error={}",
                        task == null ? null : task.getId(),
                        task == null ? null : task.getTaskName(),
                        e.getMessage(),
                        e);
            }
        }
    }

    /**
     * 启动任务后，移除旧状态
     *
     * 这样下一次扫描时会把它当作“新进入运行态”的任务，
     * 并写入一条新的激活日志。
     */
    public void resetTaskSchedule(Long taskId) {
        if (taskId != null) {
            activeTaskIds.remove(taskId);
        }
    }

    /**
     * 停止任务后，立即移除运行态记录
     */
    public void removeTaskSchedule(Long taskId) {
        if (taskId != null) {
            activeTaskIds.remove(taskId);
        }
    }
}