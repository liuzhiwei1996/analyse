package org.analyse.analysestock.strategy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("strategy_run_log")
public class StrategyRunLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String runType;

    private String status;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer stockCount;

    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
