package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MinuteBar {
    private String stockCode;
    private LocalDate tradeDate;
    private int time;
    private BigDecimal price;
    private Long volume;
    private BigDecimal amount;
    private BigDecimal high;
    private BigDecimal low;
}
