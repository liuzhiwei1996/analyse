package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("backtest_daily_summary")
/**
 * 回测每日组合收益。
 *
 * <p>一条记录对应某个交易日、TopK 和成本场景下的等权组合结果。</p>
 */
public class BacktestDailySummary {

    /**
     * 自增主键。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 回测任务 ID。
     */
    @TableField("task_id")
    private String taskId;

    /**
     * 买入参考日 T。
     */
    @TableField("trade_date")
    private LocalDate tradeDate;

    /**
     * 卖出参考日 T+1。
     */
    @TableField("next_trade_date")
    private LocalDate nextTradeDate;

    /**
     * TopK 组合规模。
     */
    @TableField("top_k")
    private Integer topK;

    /**
     * 当前成本场景，单位 bps。
     */
    @TableField("cost_bps")
    private BigDecimal costBps;

    /**
     * 当日可计算收益的有效股票数量。
     */
    @TableField("selected_count")
    private Integer selectedCount;

    /**
     * 当日组合平均毛收益，单位 bps。
     */
    @TableField("avg_gross_return_bps")
    private BigDecimal avgGrossReturnBps;

    /**
     * 当日组合平均成本，单位 bps。
     */
    @TableField("avg_cost_bps")
    private BigDecimal avgCostBps;

    /**
     * 当日组合平均净收益，单位 bps。
     */
    @TableField("avg_net_return_bps")
    private BigDecimal avgNetReturnBps;

    /**
     * 当日组合是否盈利。
     */
    @TableField("win_flag")
    private Boolean winFlag;

    /**
     * 当日贡献最大股票代码。
     */
    @TableField("best_stock_code")
    private String bestStockCode;

    /**
     * 当日贡献最大股票净收益，单位 bps。
     */
    @TableField("best_stock_return_bps")
    private BigDecimal bestStockReturnBps;

    /**
     * 当日拖累最大股票代码。
     */
    @TableField("worst_stock_code")
    private String worstStockCode;

    /**
     * 当日拖累最大股票净收益，单位 bps。
     */
    @TableField("worst_stock_return_bps")
    private BigDecimal worstStockReturnBps;

    /**
     * 每日汇总创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
