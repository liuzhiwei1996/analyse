package org.analyse.analysestock.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.mapper.StockDataDailyAllMapper;
import org.analyse.analysestock.monitor.entity.ShadowRecord;
import org.analyse.analysestock.monitor.mapper.ShadowRecordMapper;
import org.analyse.analysestock.strategy.execution.entity.PositionPlan;
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
public class ShadowTracker {

    @Autowired
    private ShadowRecordMapper shadowRecordMapper;

    @Autowired
    private StockDataDailyAllMapper stockDataDailyAllMapper;

    public List<ShadowRecord> recordSignals(LocalDate tradeDate, List<PositionPlan> plans) {
        if (CollectionUtils.isEmpty(plans)) {
            return Collections.emptyList();
        }
        return plans.stream()
                .map(plan -> buildRecord(tradeDate, plan))
                .peek(shadowRecordMapper::insert)
                .collect(Collectors.toList());
    }

    public List<ShadowRecord> list(LocalDate tradeDate) {
        return shadowRecordMapper.selectList(new LambdaQueryWrapper<ShadowRecord>()
                .eq(ShadowRecord::getTradeDate, tradeDate)
                .orderByAsc(ShadowRecord::getStockCode));
    }

    private ShadowRecord buildRecord(LocalDate tradeDate, PositionPlan plan) {
        StockDataDailyAll nextBar = stockDataDailyAllMapper.selectOne(new LambdaQueryWrapper<StockDataDailyAll>()
                .eq(StockDataDailyAll::getStockCode, plan.getStockCode())
                .gt(StockDataDailyAll::getTradeDate, tradeDate)
                .orderByAsc(StockDataDailyAll::getTradeDate)
                .last("LIMIT 1"));
        ShadowRecord record = new ShadowRecord();
        record.setTradeDate(tradeDate);
        record.setStockCode(plan.getStockCode());
        record.setSignalType("POSITION_PLAN");
        record.setEntryPrice(plan.getEntryPrice());
        if (nextBar != null) {
            record.setNextDayOpen(nextBar.getOpen());
            record.setNextDayClose(nextBar.getClose());
            if (plan.getEntryPrice() != null && plan.getEntryPrice().compareTo(BigDecimal.ZERO) > 0 && nextBar.getClose() != null) {
                record.setPnlBps(nextBar.getClose().subtract(plan.getEntryPrice()).divide(plan.getEntryPrice(), 6, RoundingMode.HALF_UP).multiply(new BigDecimal("10000")));
            }
            if (plan.getEntryPrice() != null && plan.getEntryPrice().compareTo(BigDecimal.ZERO) > 0 && nextBar.getLowest() != null) {
                record.setDrawdownBps(nextBar.getLowest().subtract(plan.getEntryPrice()).divide(plan.getEntryPrice(), 6, RoundingMode.HALF_UP).multiply(new BigDecimal("10000")));
            }
        }
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        return record;
    }
}
