package org.analyse.analysestock.risk.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.analyse.analysestock.risk.entity.RiskAlert;
import org.analyse.analysestock.risk.mapper.RiskAlertMapper;
import org.analyse.analysestock.strategy.market.entity.MarketRegime;
import org.analyse.analysestock.strategy.market.entity.MarketRegimeSnapshot;
import org.analyse.analysestock.strategy.portfolio.entity.PortfolioDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class RiskController {

    @Autowired
    private RiskAlertMapper riskAlertMapper;

    public List<RiskAlert> evaluate(LocalDate tradeDate, MarketRegimeSnapshot regimeSnapshot, List<PortfolioDecision> decisions) {
        List<RiskAlert> alerts = new ArrayList<>();
        if (regimeSnapshot == null) {
            alerts.add(alert(tradeDate, "MARKET_REGIME_MISSING", "HIGH", "缺少市场环境评估，禁止交易", null));
        } else if (MarketRegime.WEAK.name().equals(regimeSnapshot.getRegime())) {
            alerts.add(alert(tradeDate, "NO_TRADE", "HIGH", "市场环境为WEAK，触发NO_TRADE", null));
        } else if (CollectionUtils.isEmpty(decisions)) {
            alerts.add(alert(tradeDate, "NO_QUALIFIED_CANDIDATE", "MEDIUM", "非弱市但无通过性价比筛选的候选股", null));
        }
        alerts.forEach(riskAlertMapper::insert);
        return alerts;
    }

    public List<RiskAlert> list(LocalDate tradeDate) {
        return riskAlertMapper.selectList(new LambdaQueryWrapper<RiskAlert>()
                .eq(RiskAlert::getTradeDate, tradeDate)
                .orderByDesc(RiskAlert::getSeverity));
    }

    private RiskAlert alert(LocalDate tradeDate, String type, String severity, String message, String stockCode) {
        RiskAlert alert = new RiskAlert();
        alert.setTradeDate(tradeDate);
        alert.setAlertType(type);
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setRelatedStockCode(stockCode);
        alert.setResolved(false);
        alert.setCreatedAt(LocalDateTime.now());
        alert.setUpdatedAt(LocalDateTime.now());
        return alert;
    }
}
