package com.radio.core.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 监测站点表
 */
@Data
@TableName("station")
public class Station {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String stationCode;

    private String stationName;

    private BigDecimal longitude;

    private BigDecimal latitude;

    private String locationText;

    private Integer onlineStatus;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}