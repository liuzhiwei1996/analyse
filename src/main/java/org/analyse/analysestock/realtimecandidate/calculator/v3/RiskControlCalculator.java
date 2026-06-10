package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * V3 风险控制计算器（权重 10%）。
 *
 * <p>评估并惩罚高风险情景，降低最大单日亏损。</p>
 *
 * <p>风险扣分规则（需求文档第 12.3 节）：</p>
 * <ul>
 *   <li>近 20 日最大单日跌幅过大（&lt; -10%）：强降权</li>
 *   <li>强制卖出平均收益明显为负（&lt; -50bps）：强降权</li>
 *   <li>强制卖出亏损比例高（&gt; 50%）：降权</li>
 *   <li>买入后到收盘经常继续下跌：降权</li>
 *   <li>尾盘放量但价格走弱：降权</li>
 *   <li>低流动性：降权</li>
 * </ul>
 */
public class RiskControlCalculator {

    /**
     * 计算风险控制评分。
     *
     * @return 0-1 范围，1 表示风险最低
     */
    public BigDecimal calculate(V3FactorSnapshot snapshot) {
        BigDecimal score = BigDecimal.ONE;

        // ---- 1. 近 20 日最大单日跌幅 ----
        if (snapshot.getMaxDrop20d() != null) {
            BigDecimal maxDrop = snapshot.getMaxDrop20d();
            if (maxDrop.compareTo(new BigDecimal("-0.10")) < 0) {
                // 最大跌幅 > 10%：强降权
                score = score.subtract(new BigDecimal("0.3"));
            } else if (maxDrop.compareTo(new BigDecimal("-0.07")) < 0) {
                score = score.subtract(new BigDecimal("0.15"));
            } else if (maxDrop.compareTo(new BigDecimal("-0.05")) < 0) {
                score = score.subtract(new BigDecimal("0.05"));
            }
        }

        // ---- 2. 强制卖出平均收益明显为负 ----
        if (snapshot.getAvgForceSellReturnBps() != null) {
            BigDecimal fsReturn = snapshot.getAvgForceSellReturnBps();
            if (fsReturn.compareTo(new BigDecimal("-50")) < 0) {
                score = score.subtract(new BigDecimal("0.3"));
            } else if (fsReturn.compareTo(new BigDecimal("-30")) < 0) {
                score = score.subtract(new BigDecimal("0.15"));
            } else if (fsReturn.compareTo(BigDecimal.ZERO) < 0) {
                score = score.subtract(new BigDecimal("0.05"));
            }
        }

        // ---- 3. 强制卖出亏损比例 ----
        if (snapshot.getForceSellLossRate() != null) {
            BigDecimal fsLossRate = snapshot.getForceSellLossRate();
            if (fsLossRate.compareTo(new BigDecimal("0.60")) > 0) {
                score = score.subtract(new BigDecimal("0.2"));
            } else if (fsLossRate.compareTo(new BigDecimal("0.40")) > 0) {
                score = score.subtract(new BigDecimal("0.1"));
            }
        }

        // ---- 4. 波动率过高 ----
        if (snapshot.getVolatility20d() != null) {
            BigDecimal vol = snapshot.getVolatility20d();
            if (vol.compareTo(new BigDecimal("0.05")) > 0) {
                // 年化波动率 > 5%/日 ≈ 80%/年
                score = score.subtract(new BigDecimal("0.2"));
            } else if (vol.compareTo(new BigDecimal("0.035")) > 0) {
                score = score.subtract(new BigDecimal("0.1"));
            }
        }

        // ---- 5. 尾盘放量但价格走弱 ----
        if (snapshot.getTailMomentum() != null && snapshot.getTailAmountRatio() != null) {
            if (snapshot.getTailMomentum().compareTo(new BigDecimal("-0.003")) < 0
                    && snapshot.getTailAmountRatio().compareTo(new BigDecimal("1.5")) > 0) {
                score = score.subtract(new BigDecimal("0.15"));
            }
        }

        // ---- 6. 低流动性风险 ----
        if (snapshot.getAvgAmount20d() != null) {
            BigDecimal amountInYi = snapshot.getAvgAmount20d().divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP);
            if (amountInYi.compareTo(new BigDecimal("1.5")) < 0) {
                score = score.subtract(new BigDecimal("0.15"));
            } else if (amountInYi.compareTo(new BigDecimal("3")) < 0) {
                score = score.subtract(new BigDecimal("0.05"));
            }
        }

        // ---- 7. 计算流动性风险子评分 ----
        snapshot.setLiquidityRiskScore(calculateLiquidityRisk(snapshot));

        // ---- 8. 计算低开风险子评分 ----
        snapshot.setGapDownRiskScore(calculateGapDownRisk(snapshot));

        return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    private BigDecimal calculateLiquidityRisk(V3FactorSnapshot snapshot) {
        if (snapshot.getAvgAmount20d() == null) return new BigDecimal("0.5");
        BigDecimal amountInYi = snapshot.getAvgAmount20d().divide(new BigDecimal("100000000"), 2, RoundingMode.HALF_UP);
        if (amountInYi.compareTo(new BigDecimal("5")) > 0) return BigDecimal.ONE;
        if (amountInYi.compareTo(new BigDecimal("2")) > 0) return new BigDecimal("0.7");
        if (amountInYi.compareTo(new BigDecimal("1")) > 0) return new BigDecimal("0.4");
        return new BigDecimal("0.1");
    }

    private BigDecimal calculateGapDownRisk(V3FactorSnapshot snapshot) {
        // 简化：使用波动率和最大跌幅评估低开风险
        BigDecimal risk = BigDecimal.ONE;
        if (snapshot.getMaxDrop20d() != null && snapshot.getMaxDrop20d().compareTo(new BigDecimal("-0.07")) < 0) {
            risk = risk.subtract(new BigDecimal("0.3"));
        }
        if (snapshot.getVolatility20d() != null && snapshot.getVolatility20d().compareTo(new BigDecimal("0.04")) > 0) {
            risk = risk.subtract(new BigDecimal("0.2"));
        }
        return risk.max(BigDecimal.ZERO);
    }
}
