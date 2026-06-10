package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * V3 板块/市场环境计算器（权重 10%）。
 *
 * <p>评估市场环境和板块情绪对次日早盘冲高的支撑力度。</p>
 *
 * <p>市场状态分类（需求文档第 11.2 节）：</p>
 * <ul>
 *   <li>STRONG：市场宽度 >= 70%</li>
 *   <li>NORMAL：市场宽度 40%-70%</li>
 *   <li>WEAK：市场宽度 20%-40%</li>
 *   <li>EXTREME_WEAK：市场宽度 < 20%</li>
 * </ul>
 *
 * <p>弱市规则（需求文档第 11.4 节）：</p>
 * <ul>
 *   <li>marketBreadth < 20%：全市场不出候选</li>
 *   <li>marketBreadth < 30%：所有候选分数乘以弱市场折扣系数</li>
 *   <li>板块强度明显为负：该板块股票整体降权</li>
 * </ul>
 */
public class SectorMarketCalculatorV3 {

    /**
     * 计算板块/市场环境评分。
     */
    public BigDecimal calculate(V3FactorSnapshot snapshot) {
        BigDecimal score = BigDecimal.ZERO;

        // ---- 市场宽度（40%） ----
        if (snapshot.getMarketBreadth() != null) {
            BigDecimal mb = snapshot.getMarketBreadth();
            // 线性映射：宽度越高越好
            score = score.add(mb.multiply(new BigDecimal("0.40")));
        } else {
            score = score.add(new BigDecimal("0.20"));
        }

        // ---- 板块强度（30%） ----
        if (snapshot.getSectorStrength() != null) {
            BigDecimal ss = snapshot.getSectorStrength();
            // 板块强度归一化：假设 -2% ~ 2% 映射到 0-1
            BigDecimal normSs = ss.add(new BigDecimal("0.02"))
                    .divide(new BigDecimal("0.04"), 4, RoundingMode.HALF_UP);
            if (normSs.compareTo(BigDecimal.ONE) > 0) normSs = BigDecimal.ONE;
            if (normSs.compareTo(BigDecimal.ZERO) < 0) normSs = BigDecimal.ZERO;
            score = score.add(normSs.multiply(new BigDecimal("0.30")));
        } else {
            score = score.add(new BigDecimal("0.15"));
        }

        // ---- 板块宽度（20%） ----
        if (snapshot.getSectorBreadth() != null) {
            score = score.add(snapshot.getSectorBreadth().multiply(new BigDecimal("0.20")));
        } else {
            score = score.add(new BigDecimal("0.10"));
        }

        // ---- 个股相对板块强度（10%） ----
        if (snapshot.getRelativeStrength1430() != null) {
            BigDecimal rs = snapshot.getRelativeStrength1430();
            BigDecimal normRs = rs.add(new BigDecimal("0.02"))
                    .divide(new BigDecimal("0.04"), 4, RoundingMode.HALF_UP);
            if (normRs.compareTo(BigDecimal.ONE) > 0) normRs = BigDecimal.ONE;
            if (normRs.compareTo(BigDecimal.ZERO) < 0) normRs = BigDecimal.ZERO;
            score = score.add(normRs.multiply(new BigDecimal("0.10")));
        } else {
            score = score.add(new BigDecimal("0.05"));
        }

        return score.max(BigDecimal.ZERO).min(BigDecimal.ONE);
    }

    /**
     * 判断市场状态。
     */
    public String determineMarketRegime(BigDecimal marketBreadth) {
        if (marketBreadth == null) return "NORMAL";
        double mb = marketBreadth.doubleValue();
        if (mb >= 0.70) return "STRONG";
        if (mb >= 0.40) return "NORMAL";
        if (mb >= 0.20) return "WEAK";
        return "EXTREME_WEAK";
    }

    /**
     * 判断是否应该全市场不出候选（marketBreadth < 20%）。
     */
    public boolean shouldSuppressAll(BigDecimal marketBreadth) {
        return marketBreadth != null && marketBreadth.compareTo(new BigDecimal("0.20")) < 0;
    }

    /**
     * 获取弱市场折扣系数（marketBreadth < 30% 时应用）。
     */
    public BigDecimal getWeakMarketDiscount(BigDecimal marketBreadth) {
        if (marketBreadth == null) return BigDecimal.ONE;
        if (marketBreadth.compareTo(new BigDecimal("0.30")) < 0) {
            return new BigDecimal("0.7");
        }
        return BigDecimal.ONE;
    }
}
