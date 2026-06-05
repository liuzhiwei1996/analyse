package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class DailyBar {
    private String stockCode;
    private LocalDate tradeDate;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal amount;
    private Long volume;
    private BigDecimal closePrevious;
}
