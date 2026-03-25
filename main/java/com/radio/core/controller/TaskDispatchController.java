package com.radio.core.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.radio.core.common.ApiResponse;
import com.radio.core.entity.MonitorTask;
import com.radio.core.entity.TaskExecuteLog;
import com.radio.core.mapper.MonitorTaskMapper;
import com.radio.core.mapper.TaskExecuteLogMapper;
import com.radio.core.scheduler.MonitorTaskExecuteScheduler;
import com.radio.core.service.RuntimeStatusSyncService;
import com.radio.core.service.TaskDispatchService;
import com.radio.core.vo.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * 任务调度执行控制接口
 *
 * 用途：
 * 1. 启动任务
 * 2. 停止任务
 * 3. 查询任务执行日志
 */
@RestController
@RequestMapping("/api/core/task-dispatch")
@RequiredArgsConstructor
public class TaskDispatchController {

    private final MonitorTaskMapper monitorTaskMapper;
    private final TaskExecuteLogMapper taskExecuteLogMapper;
    private final MonitorTaskExecuteScheduler taskExecuteScheduler;
    private final TaskDispatchService taskDispatchService;
    private final RuntimeStatusSyncService runtimeStatusSyncService;

    /**
     * 启动任务
     */
    @PostMapping("/start/{id}")
    public ApiResponse<Void> startTask(@PathVariable Long id) {
        MonitorTask task = monitorTaskMapper.selectById(id);
        if (task == null) {
            return ApiResponse.fail(404, "任务不存在");
        }

        task.setTaskStatus(1);
        task.setUpdateTime(LocalDateTime.now());
        monitorTaskMapper.updateById(task);

        // 同步设备与站点状态
        runtimeStatusSyncService.syncAfterTaskStarted(task);

        // 清理旧计时器，确保启动后尽快执行
        taskExecuteScheduler.resetTaskSchedule(id);

        return ApiResponse.success("任务已启动", null);
    }

    /**
     * 停止任务
     */
    @PostMapping("/stop/{id}")
    public ApiResponse<Void> stopTask(@PathVariable Long id) {
        MonitorTask task = monitorTaskMapper.selectById(id);
        if (task == null) {
            return ApiResponse.fail(404, "任务不存在");
        }

        task.setTaskStatus(2);
        task.setUpdateTime(LocalDateTime.now());
        monitorTaskMapper.updateById(task);

        // 立即移除调度计时器
        taskExecuteScheduler.removeTaskSchedule(id);

        // 同步设备与站点状态
        runtimeStatusSyncService.syncAfterTaskStopped(task);

        // 写一条“手动停止”日志
        taskDispatchService.recordManualStopLog(task);

        return ApiResponse.success("任务已停止", null);
    }

    /**
     * 任务执行日志分页
     */
    @GetMapping("/logs/page")
    public ApiResponse<PageResult<TaskExecuteLog>> pageLogs(@RequestParam Long taskId,
                                                            @RequestParam(defaultValue = "1") Long current,
                                                            @RequestParam(defaultValue = "10") Long size) {
        Page<TaskExecuteLog> page = taskExecuteLogMapper.selectPage(
                new Page<>(current, size),
                new LambdaQueryWrapper<TaskExecuteLog>()
                        .eq(TaskExecuteLog::getTaskId, taskId)
                        .orderByDesc(TaskExecuteLog::getExecuteTime)
                        .orderByDesc(TaskExecuteLog::getId)
        );

        return ApiResponse.success(PageResult.of(page));
    }
}