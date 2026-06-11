package org.analyse.analysestock.monitor.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("shadow_record")
public class ShadowRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    private String stockCode;

    private String signalType;

    private BigDecimal entryPrice;

    private BigDecimal nextDayOpen;

    private BigDecimal nextDayClose;

    private BigDecimal pnlBps;

    private BigDecimal drawdownBps;

    private String exitReason;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
