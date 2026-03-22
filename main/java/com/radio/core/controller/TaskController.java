package com.radio.core.controller;

import com.radio.core.common.ApiResponse;
import com.radio.core.dto.TaskSaveRequest;
import com.radio.core.service.CoreManageService;
import com.radio.core.service.CoreQueryService;
import com.radio.core.vo.PageResult;
import com.radio.core.vo.TaskListVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 任务调度接口
 */
@RestController
@RequestMapping("/api/core/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final CoreQueryService coreQueryService;
    private final CoreManageService coreManageService;

    /**
     * 全量任务列表
     */
    @GetMapping("/list")
    public ApiResponse<List<TaskListVO>> list(@RequestParam(required = false) Long stationId,
                                              @RequestParam(required = false) Integer taskStatus) {
        return ApiResponse.success(coreQueryService.listTasks(stationId, taskStatus));
    }

    /**
     * 分页任务列表
     */
    @GetMapping("/page")
    public ApiResponse<PageResult<TaskListVO>> page(@RequestParam(defaultValue = "1") Long current,
                                                    @RequestParam(defaultValue = "10") Long size,
                                                    @RequestParam(required = false) Long stationId,
                                                    @RequestParam(required = false) Integer taskStatus,
                                                    @RequestParam(required = false) String keyword) {
        return ApiResponse.success(coreManageService.pageTasks(current, size, stationId, taskStatus, keyword));
    }

    /**
     * 新增任务
     */
    @PostMapping("/create")
    public ApiResponse<Void> create(@Valid @RequestBody TaskSaveRequest request) {
        coreManageService.createTask(request);
        return ApiResponse.success("新增成功", null);
    }

    /**
     * 修改任务
     */
    @PutMapping("/update")
    public ApiResponse<Void> update(@Valid @RequestBody TaskSaveRequest request) {
        coreManageService.updateTask(request);
        return ApiResponse.success("修改成功", null);
    }

    /**
     * 删除任务
     */
    @DeleteMapping("/delete/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        coreManageService.deleteTask(id);
        return ApiResponse.success("删除成功", null);
    }
}