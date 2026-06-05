package org.analyse.analysestock.realtimecandidate.enums;

public enum InvalidReason {
    STOCK_ST,
    DELISTED,
    NEW_STOCK,
    MISSING_1400_PRICE,
    MISSING_1430_PRICE,
    LOW_DAILY_AMOUNT,
    LOW_TAIL_AMOUNT,
    LIMIT_UP_RISK,
    LIMIT_DOWN_RISK,
    HIGH_VOLATILITY,
    WEAK_MARKET,
    DATA_QUALITY_ISSUE
}
