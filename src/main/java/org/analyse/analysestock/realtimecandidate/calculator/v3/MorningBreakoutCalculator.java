package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * V3 次日早盘冲高能力计算器（权重 20%）。
 *
 * <p>评估股票在 T+1 日早盘（09:30-09:45）冲高触发止盈条件的能力。</p>
 *
 * <p>评分组成（需求文档第 8.3 节）：</p>
 * <ul>
 *   <li>40% 触发 1% 止盈概率</li>
 *   <li>25% 触发 2% 止盈概率</li>
 *   <li>15% 触发 3% 止盈概率</li>
 *   <li>10% 早盘最高收益均值</li>
 *   <li>10% 早盘流动性</li>
 * </ul>
 *
 * <p>惩罚规则：</p>
 * <ul>
 *   <li>早盘波动长期不足 0.8%：降分</li>
 *   <li>强制卖出比例过高：降分</li>
 *   <li>强制卖出平均收益明显为负：强降权</li>
 * </ul>
 */
public class MorningBreakoutCalculator {

    /**
     * 计算早盘冲高能力评分。
     */
    public BigDecimal calculate(V3FactorSnapshot snapshot) {
        if (snapshot.getShortSampleCount() == null || snapshot.getShortSampleCount() == 0) {
            return new BigDecimal("0.5"); // 无数据给中性分
        }

        BigDecimal score = BigDecimal.ZERO;

        // 40% 触发 1% 止盈概率
        if (snapshot.getTakeProfit1PctRate() != null) {
            score = score.add(snapshot.getTakeProfit1PctRate().multiply(new BigDecimal("0.40")));
        }

        // 25% 触发 2% 止盈概率
        if (snapshot.getTakeProfit2PctRate() != null) {
            score = score.add(snapshot.getTakeProfit2PctRate().multiply(new BigDecimal("0.25")));
        }

        // 15% 触发 3% 止盈概率
        if (snapshot.getTakeProfit3PctRate() != null) {
            score = score.add(snapshot.getTakeProfit3PctRate().multiply(new BigDecimal("0.15")));
        }

        // 10% 早盘最高收益均值（归一化，假设 50bps 为满分）
        if (snapshot.getAvgTakeProfitReturnBps() != null) {
            BigDecimal normReturn = snapshot.getAvgTakeProfitReturnBps()
                    .divide(new BigDecimal("50"), 4, RoundingMode.HALF_UP);
            if (normReturn.compareTo(BigDecimal.ONE) > 0) normReturn = BigDecimal.ONE;
            if (normReturn.compareTo(BigDecimal.ZERO) < 0) normReturn = BigDecimal.ZERO;
            score = score.add(normReturn.multiply(new BigDecimal("0.10")));
        }

        // 10% 早盘流动性（基于成交额，暂用 avgAmount20d 代理）
        if (snapshot.getAvgAmount20d() != null) {
            // 简单归一化：5亿以上给满分
            BigDecimal normLiquidity = snapshot.getAvgAmount20d()
                    .divide(new BigDecimal("500000000"), 4, RoundingMode.HALF_UP);
            if (normLiquidity.compareTo(BigDecimal.ONE) > 0) normLiquidity = BigDecimal.ONE;
            score = score.add(normLiquidity.multiply(new BigDecimal("0.10")));
        } else {
            score = score.add(new BigDecimal("0.05")); // 缺数据给半分子分
        }

        // ---- 惩罚规则 ----

        // 强制卖出比例过高（> 60%）则降分
        if (snapshot.getForceSellRate() != null
                && snapshot.getForceSellRate().compareTo(new BigDecimal("0.60")) > 0) {
            score = score.multiply(new BigDecimal("0.7"));
        }

        // 强制卖出平均收益明显为负（< -30bps）则强降权
        if (snapshot.getAvgForceSellReturnBps() != null
                && snapshot.getAvgForceSellReturnBps().compareTo(new BigDecimal("-30")) < 0) {
            score = score.multiply(new BigDecimal("0.5"));
        }

        return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    /**
     * 设置早盘冲高相关字段到快照（用于输出）。
     */
    public void populateFields(V3FactorSnapshot snapshot) {
        snapshot.setHit1PctRate(snapshot.getTakeProfit1PctRate());
        snapshot.setHit2PctRate(snapshot.getTakeProfit2PctRate());
        snapshot.setHit3PctRate(snapshot.getTakeProfit3PctRate());
        snapshot.setForceSellAvgReturnBps(snapshot.getAvgForceSellReturnBps());

        // 早盘最高收益均值用止盈平均收益近似
        snapshot.setMorningHighReturnAvg(snapshot.getAvgTakeProfitReturnBps());
        snapshot.setMorningHighReturnMedian(snapshot.getAvgTakeProfitReturnBps());

        // 早盘流动性用 avgAmount20d 近似
        snapshot.setMorningLiquidity(snapshot.getAvgAmount20d());
    }
}
