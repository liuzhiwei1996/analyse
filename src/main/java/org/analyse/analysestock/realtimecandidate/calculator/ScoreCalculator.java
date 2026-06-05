package org.analyse.analysestock.realtimecandidate.calculator;

import org.analyse.analysestock.realtimecandidate.config.RealtimeStrategyConfig;
import org.analyse.analysestock.realtimecandidate.dto.FactorSnapshot;
import org.analyse.analysestock.realtimecandidate.dto.MarketContext;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class ScoreCalculator {

    public BigDecimal calculate(
            FactorSnapshot snapshot,
            MarketContext marketContext,
            RealtimeStrategyConfig config
    ) {
        // 12.1 评分公式
        // finalScore = 100 * (0.25 * tailMomentumScore + 0.20 * tailVolumeScore + 0.15 * intradayPositionScore
        //             + 0.15 * dailyTrendScore + 0.10 * volatilityScore + 0.10 * regimeScore + 0.05 * shortSampleScore)

        BigDecimal score = BigDecimal.ZERO;

        // 12.2 tailMomentumScore (已经是百分位排名)
        if (snapshot.getTailMomentumScore() != null) {
            score = score.add(snapshot.getTailMomentumScore().multiply(new BigDecimal("0.25")));
        }

        // 12.3 tailVolumeScore
        if (snapshot.getTailVolumeScore() != null) {
            score = score.add(snapshot.getTailVolumeScore().multiply(new BigDecimal("0.20")));
        }

        // 12.4 intradayPositionScore
        BigDecimal ipScore = calculateIntradayPositionScore(snapshot.getIntradayPosition());
        snapshot.setIntradayPositionScore(ipScore);
        score = score.add(ipScore.multiply(new BigDecimal("0.15")));

        // 12.5 dailyTrendScore
        if (snapshot.getDailyTrendScore() != null) {
            score = score.add(snapshot.getDailyTrendScore().multiply(new BigDecimal("0.15")));
        }

        // 12.6 volatilityScore
        if (snapshot.getVolatilityScore() != null) {
            score = score.add(snapshot.getVolatilityScore().multiply(new BigDecimal("0.10")));
        }
        // 12.7 regimeScore
        if (marketContext != null && marketContext.getRegimeScore() != null) {
            score = score.add(marketContext.getRegimeScore().multiply(new BigDecimal("0.10")));
        }

        // 12.7 shortSampleScore
        BigDecimal ssScore = calculateShortSampleScore(snapshot);
        snapshot.setShortSampleScore(ssScore);
        score = score.add(ssScore.multiply(new BigDecimal("0.05")));

        // 弱市场处理 (9.6)
        if (marketContext != null && marketContext.getMarketBreadth() != null) {
            if (marketContext.getMarketBreadth().compareTo(new BigDecimal("0.30")) < 0) {
                score = score.multiply(new BigDecimal("0.7"));
            }
        }

        return score.multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateIntradayPositionScore(BigDecimal ip) {
        if (ip == null) return new BigDecimal("0.5");
        // 0.55 <= intradayPosition <= 0.85 => 1.0
        // 0.40 <= intradayPosition < 0.55  => 0.7
        // 0.85 < intradayPosition <= 0.95  => 0.6
        // intradayPosition < 0.40          => 0.3
        // intradayPosition > 0.95          => 0.2
        double val = ip.doubleValue();
        if (val >= 0.55 && val <= 0.85) return BigDecimal.ONE;
        if (val >= 0.40 && val < 0.55) return new BigDecimal("0.7");
        if (val > 0.85 && val <= 0.95) return new BigDecimal("0.6");
        if (val < 0.40) return new BigDecimal("0.3");
        return new BigDecimal("0.2");
    }

    private BigDecimal calculateShortSampleScore(FactorSnapshot snapshot) {
        if (snapshot.getShortSampleStats() == null || snapshot.getShortSampleStats().getShortSampleCount() < 5) {
            return new BigDecimal("0.5");
        }
        // 0.6 * shortWinRate + 0.4 * normalizedShortAvgNetReturn
        // 这里对收益率做简单的归一化处理，假设 100bps 为 1.0
        BigDecimal winRate = snapshot.getShortSampleStats().getShortWinRate();
        BigDecimal avgReturn = snapshot.getShortSampleStats().getShortAvgNetReturnBps();
        BigDecimal normReturn = avgReturn.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP).add(new BigDecimal("0.5"));
        if (normReturn.compareTo(BigDecimal.ONE) > 0) normReturn = BigDecimal.ONE;
        if (normReturn.compareTo(BigDecimal.ZERO) < 0) normReturn = BigDecimal.ZERO;

        return winRate.multiply(new BigDecimal("0.6")).add(normReturn.multiply(new BigDecimal("0.4")));
    }
}
