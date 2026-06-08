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
@TableName("backtest_trade_detail")
/**
 * 回测选股收益明细。
 *
 * <p>明细只保存一份，不按 TopK 冗余；查询 TopK 时使用 rankNo <= topK。</p>
 */
public class BacktestTradeDetail {

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
     * 第一版不按 TopK 冗余明细，因此该字段保留为空。
     */
    @TableField("top_k")
    private Integer topK;

    /**
     * 当前成本场景，单位 bps。
     */
    @TableField("cost_bps")
    private BigDecimal costBps;

    /**
     * 股票代码。
     */
    @TableField("stock_code")
    private String stockCode;

    /**
     * 股票简称。
     */
    @TableField("short_name")
    private String shortName;

    /**
     * 回测使用的当日排序名次，按 finalScore/rankNo/stockCode 重新稳定排序后生成。
     */
    @TableField("rank_no")
    private Integer rankNo;

    /**
     * 评分值。
     */
    @TableField("score")
    private BigDecimal score;

    /**
     * 评分置信度。
     */
    @TableField("confidence_level")
    private String confidenceLevel;

    /**
     * T 日 14:30 买入参考价。
     */
    @TableField("buy_price_1430")
    private BigDecimal buyPrice1430;

    /**
     * T+1 日 09:30-09:45 卖出 VWAP。
     */
    @TableField("sell_vwap_0930_0945")
    private BigDecimal sellVwap09300945;

    /**
     * 单票毛收益，单位 bps。
     */
    @TableField("gross_return_bps")
    private BigDecimal grossReturnBps;

    /**
     * 请求中的固定交易成本，单位 bps。
     */
    @TableField("cost_bps_value")
    private BigDecimal costBpsValue;

    /**
     * 请求中的滑点成本，单位 bps。
     */
    @TableField("slippage_bps")
    private BigDecimal slippageBps;

    /**
     * 单票净收益，单位 bps。
     */
    @TableField("net_return_bps")
    private BigDecimal netReturnBps;

    /**
     * 单票是否盈利。
     */
    @TableField("win_flag")
    private Boolean winFlag;

    /**
     * 无法计算收益时记录原因，例如 NO_SELL_VWAP。
     */
    @TableField("invalid_reason")
    private String invalidReason;

    /**
     * 明细创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
