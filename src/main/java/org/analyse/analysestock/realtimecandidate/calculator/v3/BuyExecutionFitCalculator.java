package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * V3 买入成交适配度计算器（权重 15%）。
 *
 * <p>分析股票尾盘行为与分阶段低吸买入策略的匹配程度。</p>
 *
 * <p>理想状态：</p>
 * <ul>
 *   <li>14:30 前较强</li>
 *   <li>14:30 后小幅回落</li>
 *   <li>回落不破趋势</li>
 *   <li>次日早盘有修复或冲高</li>
 * </ul>
 *
 * <p>评分逻辑（需求文档第 9.4 节）：</p>
 * <ul>
 *   <li>买入成交率过低（&lt; 30%）：降权（机会少）</li>
 *   <li>买入成交率适中（30%-70%）：较理想</li>
 *   <li>买入成交率过高（&gt; 70%）且收益差：强降权（容易下跌）</li>
 *   <li>3% 档买入历史收益差：降权（接飞刀）</li>
 *   <li>1% 档买入历史收益好：加分（轻微回踩有效）</li>
 * </ul>
 */
public class BuyExecutionFitCalculator {

    /**
     * 计算买入成交适配度评分。
     */
    public BigDecimal calculate(V3FactorSnapshot snapshot) {
        if (snapshot.getShortSampleCount() == null || snapshot.getShortSampleCount() == 0) {
            return new BigDecimal("0.5");
        }

        BigDecimal score = new BigDecimal("0.5"); // 基准中性分

        // 1. 买入成交率评估
        if (snapshot.getBuyFillRate() != null) {
            BigDecimal fillRate = snapshot.getBuyFillRate();
            if (fillRate.compareTo(new BigDecimal("0.30")) < 0) {
                // 成交率过低：机会少，降权
                score = score.subtract(new BigDecimal("0.2"));
            } else if (fillRate.compareTo(new BigDecimal("0.70")) > 0) {
                // 成交率过高：检查收益是否差
                if (snapshot.getAvgNetReturnBps() != null
                        && snapshot.getAvgNetReturnBps().compareTo(BigDecimal.ZERO) < 0) {
                    score = score.subtract(new BigDecimal("0.3"));
                }
            } else {
                // 30%-70%：适中
                score = score.add(new BigDecimal("0.15"));
            }
        }

        // 2. 1% 档买入历史收益好：加分
        if (snapshot.getBuy1PctAvgNetReturnBps() != null
                && snapshot.getBuy1PctAvgNetReturnBps().compareTo(new BigDecimal("10")) > 0) {
            score = score.add(new BigDecimal("0.15"));
        }

        // 3. 3% 档买入历史收益差：降权
        if (snapshot.getBuy3PctAvgNetReturnBps() != null
                && snapshot.getBuy3PctAvgNetReturnBps().compareTo(new BigDecimal("-20")) < 0) {
            score = score.subtract(new BigDecimal("0.2"));
        }

        // 4. 1% 档买入比例高且收益好：额外加分
        if (snapshot.getBuy1PctFillRate() != null && snapshot.getBuy1PctAvgNetReturnBps() != null
                && snapshot.getBuy1PctFillRate().compareTo(new BigDecimal("0.30")) > 0
                && snapshot.getBuy1PctAvgNetReturnBps().compareTo(new BigDecimal("15")) > 0) {
            score = score.add(new BigDecimal("0.1"));
        }

        // 5. 尾盘动量正面 + 有买入机会：加分
        if (snapshot.getTailMomentum() != null && snapshot.getBuyFillRate() != null
                && snapshot.getTailMomentum().compareTo(BigDecimal.ZERO) > 0
                && snapshot.getBuyFillRate().compareTo(new BigDecimal("0.30")) > 0) {
            score = score.add(new BigDecimal("0.1"));
        }

        return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
