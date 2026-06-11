package org.analyse.analysestock.strategy.portfolio.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.analyse.analysestock.strategy.market.entity.MarketRegime;
import org.analyse.analysestock.strategy.market.entity.MarketRegimeSnapshot;
import org.analyse.analysestock.strategy.portfolio.entity.PortfolioDecision;
import org.analyse.analysestock.strategy.portfolio.mapper.PortfolioDecisionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PortfolioOptimizer {

    @Autowired
    private ExpectedReturnEstimator expectedReturnEstimator;

    @Autowired
    private PortfolioDecisionMapper portfolioDecisionMapper;

    public List<PortfolioDecision> optimize(LocalDate tradeDate, MarketRegimeSnapshot regimeSnapshot, List<String> stockCodes) {
        if (regimeSnapshot == null || MarketRegime.WEAK.name().equals(regimeSnapshot.getRegime()) || CollectionUtils.isEmpty(stockCodes)) {
            return Collections.emptyList();
        }
        int limit = MarketRegime.STRONG.name().equals(regimeSnapshot.getRegime()) ? 5 : 3;
        BigDecimal totalPosition = MarketRegime.STRONG.name().equals(regimeSnapshot.getRegime()) ? new BigDecimal("0.70") : new BigDecimal("0.30");
        List<PortfolioDecision> decisions = stockCodes.stream()
                .map(stockCode -> expectedReturnEstimator.estimate(tradeDate, stockCode, regimeSnapshot.getRegime()))
                .filter(decision -> decision.getRankValue() != null && decision.getRankValue().compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing(PortfolioDecision::getRankValue).reversed())
                .limit(limit)
                .collect(Collectors.toList());
        if (decisions.isEmpty()) {
            return Collections.emptyList();
        }
        BigDecimal singlePosition = totalPosition.divide(BigDecimal.valueOf(decisions.size()), 4, java.math.RoundingMode.HALF_UP);
        decisions.forEach(decision -> {
            decision.setPositionPct(singlePosition);
            portfolioDecisionMapper.insert(decision);
        });
        return decisions;
    }

    public List<PortfolioDecision> list(LocalDate tradeDate) {
        return portfolioDecisionMapper.selectList(new LambdaQueryWrapper<PortfolioDecision>()
                .eq(PortfolioDecision::getTradeDate, tradeDate)
                .orderByDesc(PortfolioDecision::getRankValue));
    }
}
