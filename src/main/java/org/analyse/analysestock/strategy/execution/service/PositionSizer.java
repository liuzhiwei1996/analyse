package org.analyse.analysestock.strategy.execution.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.mapper.StockDataDailyAllMapper;
import org.analyse.analysestock.strategy.execution.entity.PositionPlan;
import org.analyse.analysestock.strategy.execution.mapper.PositionPlanMapper;
import org.analyse.analysestock.strategy.portfolio.entity.PortfolioDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class PositionSizer {

    @Autowired
    private StockDataDailyAllMapper stockDataDailyAllMapper;

    @Autowired
    private PositionPlanMapper positionPlanMapper;

    public List<PositionPlan> createPlans(LocalDate tradeDate, List<PortfolioDecision> decisions) {
        if (CollectionUtils.isEmpty(decisions)) {
            return Collections.emptyList();
        }
        return decisions.stream()
                .map(decision -> createPlan(tradeDate, decision))
                .peek(positionPlanMapper::insert)
                .collect(Collectors.toList());
    }

    public List<PositionPlan> list(LocalDate tradeDate) {
        return positionPlanMapper.selectList(new LambdaQueryWrapper<PositionPlan>()
                .eq(PositionPlan::getTradeDate, tradeDate)
                .orderByDesc(PositionPlan::getPositionSize));
    }

    private PositionPlan createPlan(LocalDate tradeDate, PortfolioDecision decision) {
        StockDataDailyAll latest = stockDataDailyAllMapper.selectOne(new LambdaQueryWrapper<StockDataDailyAll>()
                .eq(StockDataDailyAll::getStockCode, decision.getStockCode())
                .le(StockDataDailyAll::getTradeDate, tradeDate)
                .orderByDesc(StockDataDailyAll::getTradeDate)
                .last("LIMIT 1"));
        BigDecimal entryPrice = latest == null ? BigDecimal.ZERO : latest.getClose();
        BigDecimal atrBps = decision.getRiskUnit() == null || decision.getRiskUnit().compareTo(BigDecimal.ZERO) <= 0 ? new BigDecimal("100") : decision.getRiskUnit();
        BigDecimal stopLossPct = atrBps.divide(new BigDecimal("10000"), 6, RoundingMode.HALF_UP).multiply(new BigDecimal("0.9"));
        BigDecimal takeProfitPct = atrBps.divide(new BigDecimal("10000"), 6, RoundingMode.HALF_UP).multiply(new BigDecimal("1.5"));

        PositionPlan plan = new PositionPlan();
        plan.setTradeDate(tradeDate);
        plan.setStockCode(decision.getStockCode());
        plan.setPositionSize(decision.getPositionPct());
        plan.setEntryPrice(entryPrice);
        plan.setStopLossPrice(entryPrice.multiply(BigDecimal.ONE.subtract(stopLossPct)).setScale(4, RoundingMode.HALF_UP));
        plan.setTakeProfitPrice(entryPrice.multiply(BigDecimal.ONE.add(takeProfitPct)).setScale(4, RoundingMode.HALF_UP));
        plan.setTimeStopDate(tradeDate.plusDays(3));
        plan.setRiskBudgetPct(decision.getPositionPct());
        plan.setCreatedAt(LocalDateTime.now());
        plan.setUpdatedAt(LocalDateTime.now());
        return plan;
    }
}
