package org.analyse.analysestock.realtimecandidate.config;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class CostConfig {
    private BigDecimal commissionBuyBps = new BigDecimal("2.5");
    private BigDecimal commissionSellBps = new BigDecimal("2.5");

    private BigDecimal exchangeFeeBuyBps = new BigDecimal("0.1");
    private BigDecimal exchangeFeeSellBps = new BigDecimal("0.1");

    private BigDecimal transferFeeBuyBps = new BigDecimal("0.1");
    private BigDecimal transferFeeSellBps = new BigDecimal("0.1");

    private BigDecimal stampTaxSellBps = new BigDecimal("5.0");

    private BigDecimal buySlippageBps = new BigDecimal("5.0");
    private BigDecimal sellSlippageBps = new BigDecimal("5.0");

    private BigDecimal impactCostBps = BigDecimal.ZERO;

    private BigDecimal minCommissionCny = new BigDecimal("5.0");
    private BigDecimal orderAmountCny = new BigDecimal("100000");

    private BigDecimal volumeMultiplier = BigDecimal.ONE;
}
