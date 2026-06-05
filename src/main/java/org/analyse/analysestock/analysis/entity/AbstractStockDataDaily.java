package org.analyse.analysestock.analysis.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 股票数据日线抽象基类
 */
@Data
public abstract class AbstractStockDataDaily {
    protected Long id;
    protected String stockCode;
    protected LocalDate tradeDate;
    protected BigDecimal open;
    protected BigDecimal highest;
    protected BigDecimal lowest;
    protected BigDecimal close;
    protected BigDecimal amount;
    protected BigDecimal closePrevious;
    protected Long volume;

    // 抽象方法，子类必须实现
    public abstract void setTradeDate(LocalDate tradeDate);
    public abstract LocalDate getTradeDate();
    public abstract void setOpen(BigDecimal open);
    public abstract BigDecimal getOpen();
    public abstract void setHighest(BigDecimal highest);
    public abstract BigDecimal getHighest();
    public abstract void setLowest(BigDecimal lowest);
    public abstract BigDecimal getLowest();
    public abstract void setClose(BigDecimal close);
    public abstract BigDecimal getClose();
    public abstract void setAmount(BigDecimal amount);
    public abstract BigDecimal getAmount();
    public abstract void setClosePrevious(BigDecimal closePrevious);
    public abstract BigDecimal getClosePrevious();
    public abstract void setVolume(Long volume);
    public abstract Long getVolume();
    public abstract void setStockCode(String stockCode);
    public abstract String getStockCode();

}
