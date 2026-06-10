package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * V3 预期净收益计算器（权重 25%）。
 *
 * <p>综合买入成交概率、止盈概率、止盈收益、强制卖出概率、强制卖出收益和交易成本，
 * 计算每笔交易的预期净收益（expectedNetBps）。</p>
 *
 * <p>评分逻辑（需求文档第 7 节）：</p>
 * <ul>
 *   <li>expectedNetBps &lt;= 0：0 分</li>
 *   <li>0 &lt; expectedNetBps &lt; 10：0.2 分</li>
 *   <li>10 &lt;= expectedNetBps &lt; 25：0.5 分</li>
 *   <li>25 &lt;= expectedNetBps &lt; 50：0.75 分</li>
 *   <li>expectedNetBps &gt;= 50：1 分</li>
 * </ul>
 */
public class ExpectedNetReturnCalculator {

    /**
     * 计算预期净收益评分。
     *
     * @param snapshot 因子快照（需已填充短样本统计）
     * @return 0-1 范围的评分
     */
    public BigDecimal calculate(V3FactorSnapshot snapshot) {
        // 1. 计算预期净收益（bps）
        BigDecimal expectedNetBps = computeExpectedNetBps(snapshot);

        // 2. 映射到 0-1 评分
        if (expectedNetBps == null) {
            return new BigDecimal("0.2"); // 无数据给中性偏低分
        }

        if (expectedNetBps.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        } else if (expectedNetBps.compareTo(new BigDecimal("10")) < 0) {
            return new BigDecimal("0.2");
        } else if (expectedNetBps.compareTo(new BigDecimal("25")) < 0) {
            return new BigDecimal("0.5");
        } else if (expectedNetBps.compareTo(new BigDecimal("50")) < 0) {
            return new BigDecimal("0.75");
        } else {
            return BigDecimal.ONE;
        }
    }

    /**
     * 计算预期净收益（bps）。
     *
     * <p>公式：</p>
     * <pre>
     * expectedNetBps = buyFillProb *
     *   (takeProfit1Prob * tp1Return + takeProfit2Prob * tp2Return + takeProfit3Prob * tp3Return
     *    + forceSellProb * forceSellAvgReturn)
     *   - costBps
     * </pre>
     *
     * <p>简化实现：使用短样本统计的 avgNetReturnBps 作为基础，结合 costCoverage 调整。</p>
     */
    private BigDecimal computeExpectedNetBps(V3FactorSnapshot snapshot) {
        if (snapshot.getShortSampleCount() == null || snapshot.getShortSampleCount() == 0) {
            return null;
        }

        // 如果有直接的分阶段统计，使用更精确的计算
        if (snapshot.getAvgNetReturnBps() != null) {
            BigDecimal expected = snapshot.getAvgNetReturnBps();

            // 成本覆盖调整
            if (snapshot.getAvgGrossReturnBps() != null && snapshot.getAvgNetReturnBps() != null) {
                BigDecimal costMargin = snapshot.getAvgGrossReturnBps().subtract(snapshot.getAvgNetReturnBps());
                // 如果历史平均毛收益扣除成本后仍为负，降低预期
                if (snapshot.getAvgNetReturnBps().compareTo(BigDecimal.ZERO) <= 0) {
                    // 但仍需考虑胜率和盈亏比
                    if (snapshot.getShortProfitLossRatio() != null
                            && snapshot.getShortProfitLossRatio().compareTo(new BigDecimal("1.5")) > 0
                            && snapshot.getShortAdjustedWinRate() != null
                            && snapshot.getShortAdjustedWinRate().compareTo(new BigDecimal("0.45")) > 0) {
                        // 盈亏比好且胜率尚可，给一个小正值
                        return new BigDecimal("5");
                    }
                }
            }
            return expected;
        }

        // 回退到简单估算
        if (snapshot.getShortAdjustedWinRate() != null && snapshot.getShortAvgWinBps() != null
                && snapshot.getShortAvgLossBps() != null) {
            BigDecimal adjustedWinRate = snapshot.getShortAdjustedWinRate();
            BigDecimal avgWin = snapshot.getShortAvgWinBps();
            BigDecimal avgLoss = snapshot.getShortAvgLossBps();
            BigDecimal lossRate = BigDecimal.ONE.subtract(adjustedWinRate);

            return adjustedWinRate.multiply(avgWin)
                    .subtract(lossRate.multiply(avgLoss))
                    .setScale(2, RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    /**
     * 计算成本覆盖评分（需求文档第 7.4 节）。
     *
     * @return 0-1 范围
     */
    public BigDecimal calculateCostCoverage(V3FactorSnapshot snapshot, BigDecimal totalCostBps) {
        if (snapshot.getAvgGrossReturnBps() == null) {
            return new BigDecimal("0.3");
        }

        BigDecimal avgGross = snapshot.getAvgGrossReturnBps();
        if (avgGross.compareTo(totalCostBps) <= 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal margin = avgGross.subtract(totalCostBps);
        if (margin.compareTo(new BigDecimal("10")) < 0) {
            return new BigDecimal("0.3");
        } else if (margin.compareTo(new BigDecimal("25")) < 0) {
            return new BigDecimal("0.6");
        } else {
            return BigDecimal.ONE;
        }
    }
}
