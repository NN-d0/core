package com.radio.core.service;

import com.radio.core.context.UserContext;
import com.radio.core.entity.AlarmRecord;
import com.radio.core.exception.BusinessException;
import com.radio.core.mapper.AlarmRecordMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 告警操作服务
 *
 * 本次收口目标：
 * 1. 将告警确认/处理逻辑从 Controller 下沉到 Service
 * 2. 统一通过 Mapper 操作 alarm_record
 * 3. 保留当前最小可运行方案，优先保证演示闭环稳定
 */
@Service
@RequiredArgsConstructor
public class AlarmActionService {

    private final AlarmRecordMapper alarmRecordMapper;

    /**
     * 确认告警：0 -> 1
     */
    public Boolean confirmAlarm(Long alarmId) {
        if (alarmId == null) {
            throw new BusinessException(500, "alarmId不能为空");
        }

        AlarmRecord alarm = alarmRecordMapper.selectById(alarmId);
        if (alarm == null) {
            throw new BusinessException(404, "告警不存在");
        }

        if (alarm.getAlarmStatus() == null || alarm.getAlarmStatus() != 0) {
            throw new BusinessException(500, "告警确认失败，可能该告警已不是未处理状态");
        }

        LocalDateTime now = LocalDateTime.now();

        AlarmRecord updateEntity = new AlarmRecord();
        updateEntity.setId(alarmId);
        updateEntity.setAlarmStatus(1);
        updateEntity.setConfirmTime(now);
        updateEntity.setUpdateTime(now);

        int updated = alarmRecordMapper.updateById(updateEntity);
        if (updated <= 0) {
            throw new BusinessException(500, "告警确认失败");
        }

        return true;
    }

    /**
     * 处理告警：0/1 -> 2
     */
    public Boolean handleAlarm(Long alarmId, String handleNote) {
        if (alarmId == null) {
            throw new BusinessException(500, "alarmId不能为空");
        }

        AlarmRecord alarm = alarmRecordMapper.selectById(alarmId);
        if (alarm == null) {
            throw new BusinessException(404, "告警不存在");
        }

        if (alarm.getAlarmStatus() != null && alarm.getAlarmStatus() == 2) {
            throw new BusinessException(500, "告警处理失败，可能该告警已被处理");
        }

        LocalDateTime now = LocalDateTime.now();

        AlarmRecord updateEntity = new AlarmRecord();
        updateEntity.setId(alarmId);
        updateEntity.setAlarmStatus(2);
        updateEntity.setHandleTime(now);
        updateEntity.setHandleNote(handleNote);
        updateEntity.setUpdateTime(now);

        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            updateEntity.setHandleUserId(currentUserId);
        }

        int updated = alarmRecordMapper.updateById(updateEntity);
        if (updated <= 0) {
            throw new BusinessException(500, "告警处理失败");
        }

        return true;
    }
}