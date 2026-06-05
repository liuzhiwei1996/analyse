package org.analyse.analysestock.realtimecandidate.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class ShortSampleStats {
    private int shortSampleCount;
    private BigDecimal shortWinRate = BigDecimal.ZERO;
    private BigDecimal shortAvgNetReturnBps = BigDecimal.ZERO;
    private BigDecimal shortAvgWinBps = BigDecimal.ZERO;
    private BigDecimal shortAvgLossBps = BigDecimal.ZERO;
    private BigDecimal shortProfitLossRatio = BigDecimal.ZERO;
}
