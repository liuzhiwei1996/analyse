package org.analyse.analysestock.strategy.filter.service;

import org.analyse.analysestock.strategy.filter.entity.CandidateFilterResult;

import java.time.LocalDate;
import java.time.LocalDateTime;

public final class FilterResultFactory {

    private FilterResultFactory() {
    }

    public static CandidateFilterResult pass(LocalDate tradeDate, String stockCode, String filterName, String details) {
        CandidateFilterResult result = build(tradeDate, stockCode, filterName, true, null, details);
        return result;
    }

    public static CandidateFilterResult fail(LocalDate tradeDate, String stockCode, String filterName, String reason, String details) {
        return build(tradeDate, stockCode, filterName, false, reason, details);
    }

    private static CandidateFilterResult build(LocalDate tradeDate, String stockCode, String filterName, boolean passed, String reason, String details) {
        CandidateFilterResult result = new CandidateFilterResult();
        result.setTradeDate(tradeDate);
        result.setStockCode(stockCode);
        result.setFilterName(filterName);
        result.setPassed(passed);
        result.setRejectionReason(reason);
        result.setDetails(details);
        result.setCreatedAt(LocalDateTime.now());
        result.setUpdatedAt(LocalDateTime.now());
        return result;
    }
}
