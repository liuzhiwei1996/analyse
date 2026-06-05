package org.analyse.analysestock.realtimecandidate.config;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class RealtimeStrategyConfig {
    private int buyTime = 1430;
    private int tailStartTime = 1400;
    private int sellStartTime = 930;
    private int sellEndTime = 945;

    private int minuteLookbackDays = 21;

    private int minListingDays = 120;
    private BigDecimal minDailyAmount = new BigDecimal("100000000"); // 1亿
    private BigDecimal minTailAmount = new BigDecimal("3000000");    // 300万

    private int minShortSampleCount = 5;
    private int recommendedSampleCount = 20;

    private boolean excludeSt = true;
    private boolean excludeNewStock = true;
    private boolean excludeDelisted = true;
    private boolean excludeLowLiquidity = true;

    private int volumeMultiplier = 1;
}
