package org.analyse.analysestock.realtimecandidate.calculator;

import org.analyse.analysestock.realtimecandidate.config.CostConfig;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class CostCalculator {

    public static BigDecimal calculateRoundTripCostBps(CostConfig config) {
        BigDecimal bps10000 = new BigDecimal("10000");

        // 交易佣金
        BigDecimal buyCommission = config.getCommissionBuyBps();
        // 佣金最低5元处理，这里简化逻辑，假设订单金额足够大
        BigDecimal sellCommission = config.getCommissionSellBps();

        // 交易所费用
        BigDecimal exchangeFee = config.getExchangeFeeBuyBps().add(config.getExchangeFeeSellBps());

        // 过户费
        BigDecimal transferFee = config.getTransferFeeBuyBps().add(config.getTransferFeeSellBps());

        // 印花税 (卖出)
        BigDecimal stampTax = config.getStampTaxSellBps();

        // 滑点
        BigDecimal slippage = config.getBuySlippageBps().add(config.getSellSlippageBps());

        // 冲击成本
        BigDecimal impact = config.getImpactCostBps();

        return buyCommission.add(sellCommission)
                .add(exchangeFee)
                .add(transferFee)
                .add(stampTax)
                .add(slippage)
                .add(impact);
    }
}
