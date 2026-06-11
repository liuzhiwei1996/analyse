package org.analyse.analysestock.strategy.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.analyse.analysestock.monitor.entity.ShadowRecord;
import org.analyse.analysestock.monitor.service.ShadowTracker;
import org.analyse.analysestock.risk.entity.RiskAlert;
import org.analyse.analysestock.risk.service.RiskController;
import org.analyse.analysestock.strategy.entity.StrategyRunLog;
import org.analyse.analysestock.strategy.execution.entity.PositionPlan;
import org.analyse.analysestock.strategy.execution.service.PositionSizer;
import org.analyse.analysestock.strategy.filter.service.CandidateFilterPipeline;
import org.analyse.analysestock.strategy.mapper.StrategyRunLogMapper;
import org.analyse.analysestock.strategy.market.entity.MarketRegimeSnapshot;
import org.analyse.analysestock.strategy.market.service.MarketRegimeDetector;
import org.analyse.analysestock.strategy.portfolio.entity.PortfolioDecision;
import org.analyse.analysestock.strategy.portfolio.service.PortfolioOptimizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class StrategyOrchestrator {

    @Autowired
    private MarketRegimeDetector marketRegimeDetector;

    @Autowired
    private CandidateFilterPipeline candidateFilterPipeline;

    @Autowired
    private PortfolioOptimizer portfolioOptimizer;

    @Autowired
    private PositionSizer positionSizer;

    @Autowired
    private ShadowTracker shadowTracker;

    @Autowired
    private RiskController riskController;

    @Autowired
    private StrategyRunLogMapper strategyRunLogMapper;

    public StrategyRunLog run(LocalDate tradeDate) {
        StrategyRunLog log = createLog(tradeDate);
        try {
            MarketRegimeSnapshot regimeSnapshot = marketRegimeDetector.detect(tradeDate);
            candidateFilterPipeline.run(tradeDate, regimeSnapshot);
            List<String> passedStockCodes = candidateFilterPipeline.passedStockCodes(tradeDate);
            List<PortfolioDecision> decisions = portfolioOptimizer.optimize(tradeDate, regimeSnapshot, passedStockCodes);
            List<PositionPlan> plans = positionSizer.createPlans(tradeDate, decisions);
            List<ShadowRecord> records = shadowTracker.recordSignals(tradeDate, plans);
            List<RiskAlert> alerts = riskController.evaluate(tradeDate, regimeSnapshot, decisions);

            log.setStatus("SUCCESS");
            log.setStockCount(records.size());
            log.setErrorMessage(alerts.isEmpty() ? null : "风险告警数量: " + alerts.size());
        } catch (Exception e) {
            log.setStatus("FAILED");
            log.setErrorMessage(e.getMessage());
        } finally {
            log.setEndTime(LocalDateTime.now());
            log.setUpdatedAt(LocalDateTime.now());
            strategyRunLogMapper.updateById(log);
        }
        return log;
    }

    public List<StrategyRunLog> listLogs(LocalDate tradeDate) {
        return strategyRunLogMapper.selectList(new LambdaQueryWrapper<StrategyRunLog>()
                .eq(StrategyRunLog::getTradeDate, tradeDate)
                .orderByDesc(StrategyRunLog::getStartTime));
    }

    private StrategyRunLog createLog(LocalDate tradeDate) {
        StrategyRunLog log = new StrategyRunLog();
        log.setTradeDate(tradeDate);
        log.setRunType("MANUAL");
        log.setStatus("RUNNING");
        log.setStartTime(LocalDateTime.now());
        log.setStockCount(0);
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        strategyRunLogMapper.insert(log);
        return log;
    }
}
