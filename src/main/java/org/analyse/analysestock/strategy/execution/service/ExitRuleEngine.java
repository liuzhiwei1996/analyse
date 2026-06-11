package org.analyse.analysestock.strategy.execution.service;

import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.strategy.execution.entity.PositionPlan;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
public class ExitRuleEngine {

    public String evaluate(PositionPlan plan, StockDataDailyAll latestBar, LocalDate currentDate) {
        if (plan == null || latestBar == null || latestBar.getClose() == null) {
            return "HOLD";
        }
        BigDecimal close = latestBar.getClose();
        if (plan.getStopLossPrice() != null && close.compareTo(plan.getStopLossPrice()) <= 0) {
            return "STOP_LOSS";
        }
        if (plan.getTakeProfitPrice() != null && close.compareTo(plan.getTakeProfitPrice()) >= 0) {
            return "TAKE_PROFIT";
        }
        if (plan.getTimeStopDate() != null && !currentDate.isBefore(plan.getTimeStopDate())) {
            return "TIME_STOP";
        }
        return "HOLD";
    }
}
