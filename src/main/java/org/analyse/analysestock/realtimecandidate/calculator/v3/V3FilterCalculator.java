package org.analyse.analysestock.realtimecandidate.calculator.v3;

import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.realtimecandidate.config.RealtimeStrategyConfig;
import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;
import org.analyse.analysestock.realtimecandidate.enums.DataQualityFlag;
import org.analyse.analysestock.realtimecandidate.enums.InvalidReason;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

/**
 * V3 硬过滤计算器。
 *
 * <p>在评分计算前应用所有硬过滤规则，同时为通过过滤的股票设置降权系数。</p>
 *
 * <p>过滤顺序（按需求文档第 6 节）：</p>
 * <ol>
 *   <li>ST / 退市 / 新股过滤</li>
 *   <li>流动性过滤</li>
 *   <li>低波动股票过滤</li>
 *   <li>尾盘明显走弱过滤</li>
 *   <li>日内位置过滤</li>
 *   <li>涨跌停风险过滤</li>
 * </ol>
 */
public class V3FilterCalculator {

    /**
     * 对单只股票应用过滤规则。
     *
     * @param snapshot 因子快照
     * @param info     股票基础信息
     * @param config   策略配置
     * @param tradeDate 交易日
     * @return true 表示通过所有硬过滤，false 表示被过滤
     */
    public boolean apply(V3FactorSnapshot snapshot, PubStockInfo info,
                         RealtimeStrategyConfig config, LocalDate tradeDate) {

        // ---- 6.1 ST / 退市 / 新股过滤 ----
        if (config.isExcludeSt() && "1".equals(info.getStStatus())) {
            snapshot.setValid(false);
            snapshot.setInvalidReason(InvalidReason.STOCK_ST);
            return false;
        }
        if (config.isExcludeDelisted() && "0".equals(info.getListedStatus())) {
            snapshot.setValid(false);
            snapshot.setInvalidReason(InvalidReason.DELISTED);
            return false;
        }
        if (config.isExcludeNewStock() && info.getListingDate() != null) {
            if (info.getListingDate().plusDays(config.getMinListingDays()).isAfter(tradeDate)) {
                snapshot.setValid(false);
                snapshot.setInvalidReason(InvalidReason.NEW_STOCK);
                return false;
            }
        }

        // ---- 6.2 流动性过滤 ----
        if (snapshot.getAvgAmount20d() != null
                && snapshot.getAvgAmount20d().compareTo(config.getMinDailyAmount()) < 0) {
            snapshot.setValid(false);
            snapshot.setInvalidReason(InvalidReason.LOW_DAILY_AMOUNT);
            return false;
        }
        if (snapshot.getTodayTailAmount() != null
                && snapshot.getTodayTailAmount().compareTo(config.getMinTailAmount()) < 0) {
            snapshot.setValid(false);
            snapshot.setInvalidReason(InvalidReason.LOW_TAIL_AMOUNT);
            return false;
        }

        // ---- 6.3 低波动股票过滤 ----
        if (snapshot.getAvgAmplitude20d() != null) {
            BigDecimal amp = snapshot.getAvgAmplitude20d();
            if (amp.compareTo(new BigDecimal("0.01")) < 0) {
                // avgAmplitude20d < 1.0%：直接过滤
                snapshot.setValid(false);
                snapshot.setInvalidReason(InvalidReason.LOW_AMPLITUDE);
                return false;
            } else if (amp.compareTo(new BigDecimal("0.015")) < 0) {
                // 1.0% <= avgAmplitude20d < 1.5%：强降权
                snapshot.setFilterDowngradeMultiplier(new BigDecimal("0.5"));
                snapshot.setDataQualityFlag(DataQualityFlag.EXTREME_VOLATILITY);
            } else if (amp.compareTo(new BigDecimal("0.02")) < 0) {
                // 1.5% <= avgAmplitude20d < 2.0%：轻降权
                snapshot.setFilterDowngradeMultiplier(new BigDecimal("0.75"));
            }
        }

        // ---- 6.4 尾盘明显走弱过滤 ----
        if (snapshot.getTailMomentum() != null) {
            BigDecimal tm = snapshot.getTailMomentum();
            if (tm.compareTo(new BigDecimal("-0.005")) < 0) {
                // tailMomentum < -0.5%：过滤
                snapshot.setValid(false);
                snapshot.setInvalidReason(InvalidReason.WEAK_TAIL_MOMENTUM);
                return false;
            } else if (tm.compareTo(BigDecimal.ZERO) < 0) {
                // -0.5% <= tailMomentum < 0：降权
                BigDecimal current = snapshot.getFilterDowngradeMultiplier();
                snapshot.setFilterDowngradeMultiplier(current.multiply(new BigDecimal("0.7")));
            }
        }

        // ---- 6.5 日内位置过滤 ----
        if (snapshot.getIntradayPosition() != null) {
            BigDecimal ip = snapshot.getIntradayPosition();
            if (ip.compareTo(new BigDecimal("0.35")) < 0) {
                // intradayPosition < 35%：过滤
                snapshot.setValid(false);
                snapshot.setInvalidReason(InvalidReason.LOW_INTRADAY_POSITION);
                return false;
            } else if (ip.compareTo(new BigDecimal("0.50")) < 0) {
                // 35% <= intradayPosition < 50%：降权
                BigDecimal current = snapshot.getFilterDowngradeMultiplier();
                snapshot.setFilterDowngradeMultiplier(current.multiply(new BigDecimal("0.8")));
            } else if (ip.compareTo(new BigDecimal("0.85")) > 0 && ip.compareTo(new BigDecimal("0.95")) <= 0) {
                // 85% < intradayPosition <= 95%：轻微降权
                BigDecimal current = snapshot.getFilterDowngradeMultiplier();
                snapshot.setFilterDowngradeMultiplier(current.multiply(new BigDecimal("0.9")));
            } else if (ip.compareTo(new BigDecimal("0.95")) > 0) {
                // intradayPosition > 95%：强降权
                snapshot.setValid(false);
                snapshot.setInvalidReason(InvalidReason.HIGH_INTRADAY_POSITION);
                return false;
            }
        }

        // ---- 6.6 涨跌停风险过滤 ----
        if (snapshot.getClosePrevious() != null && snapshot.getPrice1430() != null) {
            BigDecimal prevClose = snapshot.getClosePrevious();
            if (prevClose.compareTo(BigDecimal.ZERO) <= 0) {
                snapshot.setValid(false);
                snapshot.setInvalidReason(InvalidReason.PRICE_INVALID);
                return false;
            }
            BigDecimal return1430 = snapshot.getPrice1430().divide(prevClose, 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE);

            // 接近涨停（涨幅 > 9.5%）
            if (return1430.compareTo(new BigDecimal("0.095")) > 0) {
                snapshot.setValid(false);
                snapshot.setInvalidReason(InvalidReason.LIMIT_UP_RISK);
                return false;
            }
            // 接近跌停（跌幅 < -9.5%）
            if (return1430.compareTo(new BigDecimal("-0.095")) < 0) {
                snapshot.setValid(false);
                snapshot.setInvalidReason(InvalidReason.LIMIT_DOWN_RISK);
                return false;
            }
        }

        return true;
    }
}
