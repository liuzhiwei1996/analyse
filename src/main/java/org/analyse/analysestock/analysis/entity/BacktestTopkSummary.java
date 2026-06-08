package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("backtest_topk_summary")
/**
 * 回测 TopK 汇总结果。
 *
 * <p>一条记录对应一个 taskId、一个 TopK 和一个成本场景。</p>
 */
public class BacktestTopkSummary {

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
     * TopK 组合规模，例如 5、10、20。
     */
    @TableField("top_k")
    private Integer topK;

    /**
     * 当前汇总使用的总成本，单位 bps。
     */
    @TableField("cost_bps")
    private BigDecimal costBps;

    /**
     * 当前 TopK 和成本场景下有收益结果的交易日数。
     */
    @TableField("trade_days")
    private Integer tradeDays;

    /**
     * 日组合平均净收益，单位 bps。
     */
    @TableField("avg_net_return_bps")
    private BigDecimal avgNetReturnBps;

    /**
     * 日组合胜率，dailyReturnBps > 0 的交易日占比。
     */
    @TableField("daily_win_rate")
    private BigDecimal dailyWinRate;

    /**
     * 单票胜率，明细中 netReturnBps > 0 的股票占比。
     */
    @TableField("stock_win_rate")
    private BigDecimal stockWinRate;

    /**
     * 简单累计总收益，单位 bps；不做复利。
     */
    @TableField("total_return_bps")
    private BigDecimal totalReturnBps;

    /**
     * 最大单日亏损，即日组合净收益最小值。
     */
    @TableField("max_single_day_loss_bps")
    private BigDecimal maxSingleDayLossBps;

    /**
     * 每日平均有效选股数量。
     */
    @TableField("avg_selected_count")
    private BigDecimal avgSelectedCount;

    /**
     * 汇总记录创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
