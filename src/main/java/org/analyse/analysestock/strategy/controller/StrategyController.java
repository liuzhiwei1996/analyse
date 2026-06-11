package org.analyse.analysestock.strategy.controller;

import org.analyse.analysestock.config.ResultData;
import org.analyse.analysestock.config.ResultUtil;
import org.analyse.analysestock.monitor.entity.ShadowRecord;
import org.analyse.analysestock.monitor.service.ShadowTracker;
import org.analyse.analysestock.risk.entity.RiskAlert;
import org.analyse.analysestock.risk.service.RiskController;
import org.analyse.analysestock.strategy.entity.StrategyRunLog;
import org.analyse.analysestock.strategy.filter.entity.CandidateFilterResult;
import org.analyse.analysestock.strategy.filter.service.CandidateFilterPipeline;
import org.analyse.analysestock.strategy.market.entity.MarketRegimeSnapshot;
import org.analyse.analysestock.strategy.market.service.MarketRegimeDetector;
import org.analyse.analysestock.strategy.portfolio.entity.PortfolioDecision;
import org.analyse.analysestock.strategy.portfolio.service.PortfolioOptimizer;
import org.analyse.analysestock.strategy.service.StrategyOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/strategy")
public class StrategyController {

    @Autowired
    private MarketRegimeDetector marketRegimeDetector;

    @Autowired
    private CandidateFilterPipeline candidateFilterPipeline;

    @Autowired
    private PortfolioOptimizer portfolioOptimizer;

    @Autowired
    private ShadowTracker shadowTracker;

    @Autowired
    private RiskController riskController;

    @Autowired
    private StrategyOrchestrator strategyOrchestrator;

    @GetMapping("/market-regime")
    public ResultData<MarketRegimeSnapshot> marketRegime() {
        return ResultUtil.success(marketRegimeDetector.latest());
    }

    @GetMapping("/candidates")
    public ResultData<List<CandidateFilterResult>> candidates(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tradeDate) {
        return ResultUtil.success(candidateFilterPipeline.list(tradeDate));
    }

    @GetMapping("/portfolio")
    public ResultData<List<PortfolioDecision>> portfolio(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tradeDate) {
        return ResultUtil.success(portfolioOptimizer.list(tradeDate));
    }

    @GetMapping("/shadow")
    public ResultData<List<ShadowRecord>> shadow(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tradeDate) {
        return ResultUtil.success(shadowTracker.list(tradeDate));
    }

    @GetMapping("/alerts")
    public ResultData<List<RiskAlert>> alerts(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tradeDate) {
        return ResultUtil.success(riskController.list(tradeDate));
    }

    @GetMapping("/logs")
    public ResultData<List<StrategyRunLog>> logs(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tradeDate) {
        return ResultUtil.success(strategyOrchestrator.listLogs(tradeDate));
    }

    @PostMapping("/trigger")
    public ResultData<StrategyRunLog> trigger(@RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate tradeDate) {
        return ResultUtil.success(strategyOrchestrator.run(tradeDate));
    }
}
