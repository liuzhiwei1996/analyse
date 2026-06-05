package org.analyse.analysestock.realtimecandidate.calculator;

import org.analyse.analysestock.analysis.entity.StockMinuteData;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public class VwapCalculator {

    public static BigDecimal calculateVwap(List<StockMinuteData> minutes, int volumeMultiplier) {
        if (minutes == null || minutes.isEmpty()) {
            return null;
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        long totalVolume = 0;

        for (StockMinuteData min : minutes) {
            if (min.getMinuteAmount() != null && min.getMinuteVolume() != null) {
                totalAmount = totalAmount.add(min.getMinuteAmount());
                totalVolume += min.getMinuteVolume();
            }
        }

        if (totalVolume == 0) {
            return null;
        }

        return totalAmount.divide(BigDecimal.valueOf(totalVolume * (long)volumeMultiplier), 4, RoundingMode.HALF_UP);
    }
}
