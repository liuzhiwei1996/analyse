package org.analyse.analysestock.analysis.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("backtest_task")
/**
 * 回测任务主表。
 *
 * <p>记录一次异步回测的请求参数、执行状态和最终汇总 JSON。</p>
 */
public class BacktestTask {

    /**
     * 回测任务 ID，接口后续查询均以该字段为入口。
     */
    @TableId("task_id")
    private String taskId;

    /**
     * 回测开始日期。
     */
    @TableField("start_date")
    private LocalDate startDate;

    /**
     * 回测结束日期。
     */
    @TableField("end_date")
    private LocalDate endDate;

    /**
     * TopK 列表 JSON，例如 [5,10,20]。
     */
    @TableField("top_k_list")
    private String topKList;

    /**
     * 成本场景 JSON，例如 [0,10,25,50]。
     */
    @TableField("cost_scenario_bps_list")
    private String costScenarioBpsList;

    /**
     * 评分策略版本。
     */
    @TableField("strategy_version")
    private String strategyVersion;

    /**
     * 任务状态：PENDING、RUNNING、FINISHED、FAILED。
     */
    @TableField("status")
    private String status;

    /**
     * 至少有一只股票能计算 T 到 T+1 收益的有效交易日数。
     */
    @TableField("backtest_trade_days")
    private Integer backtestTradeDays;

    /**
     * 任务创建时间。
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 任务开始执行时间。
     */
    @TableField("started_at")
    private LocalDateTime startedAt;

    /**
     * 任务结束时间。
     */
    @TableField("finished_at")
    private LocalDateTime finishedAt;

    /**
     * 原始请求参数 JSON，便于复盘回测口径。
     */
    @TableField("request_json")
    private String requestJson;

    /**
     * 汇总结果 JSON，便于任务列表快速展示。
     */
    @TableField("summary_json")
    private String summaryJson;

    /**
     * 失败原因；仅 FAILED 状态下有值。
     */
    @TableField("error_message")
    private String errorMessage;
}
