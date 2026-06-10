package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * V3 尾盘强度计算器（权重 15%）。
 *
 * <p>评估 T 日 14:30 前股票是否存在资金关注。</p>
 *
 * <p>偏好（需求文档第 10.3 节）：</p>
 * <ul>
 *   <li>尾盘小幅走强</li>
 *   <li>尾盘放量但不过热</li>
 *   <li>个股强于板块</li>
 *   <li>日内位置在 50%～85%</li>
 *   <li>14:30 涨幅不过大</li>
 * </ul>
 */
public class TailStrengthCalculator {

    /**
     * 计算尾盘强度评分。
     *
     * <p>组成：</p>
     * <ul>
     *   <li>50% 尾盘动量（tailMomentum）</li>
     *   <li>30% 尾盘量比（tailAmountRatio）</li>
     *   <li>20% 日内位置适配度</li>
     * </ul>
     */
    public BigDecimal calculate(V3FactorSnapshot snapshot) {
        BigDecimal score = BigDecimal.ZERO;

        // ---- 50% 尾盘动量 ----
        if (snapshot.getTailMomentum() != null) {
            BigDecimal tm = snapshot.getTailMomentum();
            // 偏好小幅走强（0% ~ 2%），太高或太低都不好
            BigDecimal tmScore;
            if (tm.compareTo(BigDecimal.ZERO) < 0) {
                // 尾盘走弱：线性扣分，-1% 时到 0
                tmScore = BigDecimal.ONE.add(tm.multiply(new BigDecimal("100"))).max(BigDecimal.ZERO);
            } else if (tm.compareTo(new BigDecimal("0.02")) <= 0) {
                // 0% ~ 2%：线性加分，2% 时满分
                tmScore = tm.divide(new BigDecimal("0.02"), 4, RoundingMode.HALF_UP);
            } else {
                // > 2%：涨幅过大，轻微降分
                tmScore = new BigDecimal("0.7");
            }
            score = score.add(tmScore.multiply(new BigDecimal("0.50")));
        } else {
            score = score.add(new BigDecimal("0.25")); // 中性
        }

        // ---- 30% 尾盘量比 ----
        if (snapshot.getTailAmountRatio() != null) {
            BigDecimal ratio = snapshot.getTailAmountRatio();
            BigDecimal ratioScore;
            // 偏好 1.0 ~ 2.0 倍（放量但不过热）
            if (ratio.compareTo(new BigDecimal("1.0")) >= 0 && ratio.compareTo(new BigDecimal("2.0")) <= 0) {
                ratioScore = BigDecimal.ONE;
            } else if (ratio.compareTo(new BigDecimal("0.5")) >= 0 && ratio.compareTo(new BigDecimal("1.0")) < 0) {
                ratioScore = new BigDecimal("0.7");
            } else if (ratio.compareTo(new BigDecimal("2.0")) > 0 && ratio.compareTo(new BigDecimal("3.0")) <= 0) {
                ratioScore = new BigDecimal("0.6");
            } else {
                ratioScore = new BigDecimal("0.3");
            }
            score = score.add(ratioScore.multiply(new BigDecimal("0.30")));
        } else {
            score = score.add(new BigDecimal("0.15"));
        }

        // ---- 20% 日内位置适配度 ----
        if (snapshot.getIntradayPosition() != null) {
            BigDecimal ip = snapshot.getIntradayPosition();
            BigDecimal ipScore;
            // 理想区间 50%-85%
            if (ip.compareTo(new BigDecimal("0.50")) >= 0 && ip.compareTo(new BigDecimal("0.85")) <= 0) {
                ipScore = BigDecimal.ONE;
            } else if (ip.compareTo(new BigDecimal("0.35")) >= 0 && ip.compareTo(new BigDecimal("0.50")) < 0) {
                ipScore = new BigDecimal("0.6");
            } else if (ip.compareTo(new BigDecimal("0.85")) > 0 && ip.compareTo(new BigDecimal("0.95")) <= 0) {
                ipScore = new BigDecimal("0.5");
            } else {
                ipScore = new BigDecimal("0.2");
            }
            score = score.add(ipScore.multiply(new BigDecimal("0.20")));
        } else {
            score = score.add(new BigDecimal("0.10"));
        }

        // ---- 惩罚：尾盘放量下跌 ----
        if (snapshot.getTailMomentum() != null && snapshot.getTailAmountRatio() != null
                && snapshot.getTailMomentum().compareTo(BigDecimal.ZERO) < 0
                && snapshot.getTailAmountRatio().compareTo(new BigDecimal("1.5")) > 0) {
            score = score.multiply(new BigDecimal("0.5"));
        }

        // ---- 惩罚：14:30 涨幅过大 ----
        if (snapshot.getReturnTo1430() != null
                && snapshot.getReturnTo1430().compareTo(new BigDecimal("0.05")) > 0) {
            score = score.multiply(new BigDecimal("0.6"));
        }

        return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }
}
