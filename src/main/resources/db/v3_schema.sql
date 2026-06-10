-- ============================================================================
-- V3 交易适配型实时候选股评分模型 - 数据库Schema
-- 策略版本: REALTIME_CANDIDATE_EXECUTION_FIT_V3
-- ============================================================================

-- ----------------------------------------------------------------------------
-- 1. V3 主评分表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS realtime_candidate_score_result_v3 (
    -- 基础字段 (15.1)
    trade_date DATE NOT NULL COMMENT '交易日',
    stock_code VARCHAR(16) NOT NULL COMMENT '股票代码',
    short_name VARCHAR(64) NULL COMMENT '股票简称',
    market VARCHAR(16) NULL COMMENT '市场（SH/SZ）',
    sector VARCHAR(64) NULL COMMENT '板块/行业',

    -- 实时因子字段 (15.2)
    price_1400 DECIMAL(18,4) NULL COMMENT 'T日14:00价格',
    price_1430 DECIMAL(18,4) NULL COMMENT 'T日14:30价格（买入参考价）',
    return_to_1430 DECIMAL(10,6) NULL COMMENT 'T日14:30相对昨收涨跌幅',
    tail_momentum DECIMAL(10,6) NULL COMMENT '尾盘动量（14:00-14:30涨跌幅）',
    tail_amount_1400_1430 DECIMAL(20,4) NULL COMMENT '14:00-14:30成交额',
    tail_amount_ratio DECIMAL(10,6) NULL COMMENT '尾盘量比（今日尾盘成交额/近20日尾盘均值）',
    intraday_position DECIMAL(10,6) NULL COMMENT '日内位置（0-1）',
    relative_strength_1430 DECIMAL(10,6) NULL COMMENT '个股相对板块强度',
    amount_before_1430 DECIMAL(20,4) NULL COMMENT '14:30前累计成交额',

    -- 日K因子字段 (15.3)
    return_5d DECIMAL(10,6) NULL COMMENT '近5日收益率',
    return_20d DECIMAL(10,6) NULL COMMENT '近20日收益率',
    volatility_20d DECIMAL(10,6) NULL COMMENT '近20日波动率',
    avg_amount_20d DECIMAL(20,4) NULL COMMENT '近20日日均成交额',
    avg_amplitude_20d DECIMAL(10,6) NULL COMMENT '近20日日均振幅',
    max_drop_20d DECIMAL(10,6) NULL COMMENT '近20日最大单日跌幅',
    position_20d DECIMAL(10,6) NULL COMMENT '近20日价格位置',

    -- 早盘冲高能力字段 (15.4)
    morning_high_return_avg DECIMAL(10,6) NULL COMMENT '次日早盘最高收益均值',
    morning_high_return_median DECIMAL(10,6) NULL COMMENT '次日早盘最高收益中位数',
    hit_1pct_rate DECIMAL(10,6) NULL COMMENT '触发1%止盈概率',
    hit_2pct_rate DECIMAL(10,6) NULL COMMENT '触发2%止盈概率',
    hit_3pct_rate DECIMAL(10,6) NULL COMMENT '触发3%止盈概率',
    force_sell_rate DECIMAL(10,6) NULL COMMENT '强制卖出概率',
    force_sell_avg_return_bps DECIMAL(10,6) NULL COMMENT '强制卖出平均收益(bps)',

    -- 买入适配字段 (15.5)
    buy_fill_rate DECIMAL(10,6) NULL COMMENT '买入成交率',
    buy_3pct_fill_rate DECIMAL(10,6) NULL COMMENT '3%档买入比例',
    buy_2pct_fill_rate DECIMAL(10,6) NULL COMMENT '2%档买入比例',
    buy_1pct_fill_rate DECIMAL(10,6) NULL COMMENT '1%档买入比例',
    buy_3pct_avg_net_return_bps DECIMAL(10,6) NULL COMMENT '3%档买入平均净收益(bps)',
    buy_2pct_avg_net_return_bps DECIMAL(10,6) NULL COMMENT '2%档买入平均净收益(bps)',
    buy_1pct_avg_net_return_bps DECIMAL(10,6) NULL COMMENT '1%档买入平均净收益(bps)',
    not_filled_rate DECIMAL(10,6) NULL COMMENT '未成交比例',

    -- 风险字段 (15.6)
    post_buy_drawdown_avg DECIMAL(10,6) NULL COMMENT '买入后平均最大浮亏',
    force_sell_loss_rate DECIMAL(10,6) NULL COMMENT '强制卖出亏损比例',
    max_loss_bps DECIMAL(10,6) NULL COMMENT '历史最大亏损(bps)',
    liquidity_risk_score DECIMAL(10,6) NULL COMMENT '流动性风险评分',
    gap_down_risk_score DECIMAL(10,6) NULL COMMENT '低开风险评分',

    -- 短样本统计
    short_sample_count INT NULL COMMENT '有效样本数量',
    short_win_rate DECIMAL(10,6) NULL COMMENT '短样本原始胜率',
    short_adjusted_win_rate DECIMAL(10,6) NULL COMMENT '贝叶斯修正胜率',
    short_avg_net_return_bps DECIMAL(10,6) NULL COMMENT '短样本平均净收益(bps)',
    short_avg_win_bps DECIMAL(10,6) NULL COMMENT '短样本平均盈利(bps)',
    short_avg_loss_bps DECIMAL(10,6) NULL COMMENT '短样本平均亏损(bps)',
    short_profit_loss_ratio DECIMAL(10,6) NULL COMMENT '短样本盈亏比',

    -- 分项评分字段 (15.7)
    expected_net_return_score DECIMAL(10,6) NULL COMMENT '预期净收益评分（权重25%）',
    morning_breakout_score DECIMAL(10,6) NULL COMMENT '次日早盘冲高能力评分（权重20%）',
    buy_execution_fit_score DECIMAL(10,6) NULL COMMENT '买入成交适配度评分（权重15%）',
    tail_strength_score DECIMAL(10,6) NULL COMMENT '尾盘强度评分（权重15%）',
    sector_market_score DECIMAL(10,6) NULL COMMENT '板块/市场环境评分（权重10%）',
    risk_control_score DECIMAL(10,6) NULL COMMENT '风险控制评分（权重10%）',
    short_sample_score DECIMAL(10,6) NULL COMMENT '短样本稳定性评分（权重5%）',

    -- 市场/板块环境
    market_breadth DECIMAL(10,6) NULL COMMENT '市场宽度（上涨家数比例）',
    market_return_1430 DECIMAL(10,6) NULL COMMENT '市场14:30平均涨幅',
    market_regime VARCHAR(16) NULL COMMENT '市场状态（STRONG/NORMAL/WEAK/EXTREME_WEAK）',
    sector_strength DECIMAL(10,6) NULL COMMENT '板块强度',
    sector_breadth DECIMAL(10,6) NULL COMMENT '板块宽度',
    sector_rank INT NULL COMMENT '板块强度排名',

    -- 总分与排名
    final_score DECIMAL(18,6) NULL COMMENT '最终评分（百分制 0-100）',
    rank_no INT NULL COMMENT '全市场排名',

    -- 元数据
    confidence_level VARCHAR(32) NULL COMMENT '置信度等级',
    data_quality_flag VARCHAR(32) NULL COMMENT '数据质量标记',
    valid_flag TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
    invalid_reason VARCHAR(128) NULL COMMENT '无效原因',
    strategy_version VARCHAR(64) NOT NULL DEFAULT 'REALTIME_CANDIDATE_EXECUTION_FIT_V3' COMMENT '策略版本',
    score_explanation TEXT NULL COMMENT '评分解释JSON（主要加分/扣分原因）',
    created_at DATETIME NULL COMMENT '创建时间',

    PRIMARY KEY (trade_date, strategy_version, stock_code),
    KEY idx_score_v3_trade_rank (trade_date, strategy_version, valid_flag, rank_no),
    KEY idx_score_v3_trade_score (trade_date, strategy_version, valid_flag, final_score),
    KEY idx_score_v3_stock_date (stock_code, trade_date),
    KEY idx_score_v3_sector (trade_date, sector, valid_flag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3实时候选股评分结果';

-- ----------------------------------------------------------------------------
-- 2. V3 增强尾盘交易快照表
--    合并 stock_tail_trade_snapshot + stock_intraday_execution_snapshot
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_tail_trade_snapshot_v3 (
    stock_code VARCHAR(16) NOT NULL COMMENT '股票代码',
    trade_date DATE NOT NULL COMMENT '交易日',

    -- T日基础价格
    price_1400 DECIMAL(18,4) NULL COMMENT 'T日14:00价格',
    price_1430 DECIMAL(18,4) NULL COMMENT 'T日14:30价格',
    high_before_1430 DECIMAL(18,4) NULL COMMENT 'T日14:30前最高价',
    low_before_1430 DECIMAL(18,4) NULL COMMENT 'T日14:30前最低价',

    -- T日尾盘成交
    tail_amount_1400_1430 DECIMAL(20,4) NULL COMMENT '14:00-14:30成交额',
    tail_volume_1400_1430 BIGINT NULL COMMENT '14:00-14:30成交量',
    amount_before_1430 DECIMAL(20,4) NULL COMMENT '14:30前累计成交额',
    volume_before_1430 BIGINT NULL COMMENT '14:30前累计成交量',

    -- T日尾盘买入窗口（14:35-15:00各档最低价）
    low_1435_1444 DECIMAL(18,4) NULL COMMENT '14:35-14:44最低价（3%买入档）',
    low_1445_1454 DECIMAL(18,4) NULL COMMENT '14:45-14:54最低价（2%买入档）',
    low_1455_1500 DECIMAL(18,4) NULL COMMENT '14:55-15:00最低价（1%买入档）',

    -- T日收盘
    close_price DECIMAL(18,4) NULL COMMENT 'T日收盘价',
    close_amount DECIMAL(20,4) NULL COMMENT 'T日收盘成交额',

    -- T+1日早盘卖出窗口
    high_0930_0935 DECIMAL(18,4) NULL COMMENT '09:30-09:35最高价（3%止盈档）',
    high_0936_0940 DECIMAL(18,4) NULL COMMENT '09:36-09:40最高价（2%止盈档）',
    high_0941_0944 DECIMAL(18,4) NULL COMMENT '09:41-09:44最高价（1%止盈档）',
    price_0945 DECIMAL(18,4) NULL COMMENT '09:45价格（强制卖出价）',
    vwap_0930_0945 DECIMAL(18,4) NULL COMMENT '09:30-09:45 VWAP',
    sell_amount_0930_0945 DECIMAL(20,4) NULL COMMENT '09:30-09:45成交额',
    sell_volume_0930_0945 BIGINT NULL COMMENT '09:30-09:45成交量',

    -- 元数据
    valid_flag TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否有效',
    invalid_reason VARCHAR(64) NULL COMMENT '无效原因',
    created_at DATETIME NULL COMMENT '创建时间',
    updated_at DATETIME NULL COMMENT '更新时间',

    PRIMARY KEY (trade_date, stock_code),
    KEY idx_tail_v3_trade_valid (trade_date, valid_flag),
    KEY idx_tail_v3_stock_date (stock_code, trade_date),
    KEY idx_tail_v3_next_date (stock_code, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3增强尾盘交易快照';

-- ----------------------------------------------------------------------------
-- 3. V3 短样本统计表
--    基于分阶段买卖规则的历史表现统计
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS stock_short_sample_stats_v3 (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    stock_code VARCHAR(16) NOT NULL COMMENT '股票代码',
    trade_date DATE NOT NULL COMMENT '统计截止交易日',

    -- 买入成交统计
    buy_fill_rate DECIMAL(10,6) NULL COMMENT '买入成交率',
    buy_3pct_fill_rate DECIMAL(10,6) NULL COMMENT '3%档买入比例',
    buy_2pct_fill_rate DECIMAL(10,6) NULL COMMENT '2%档买入比例',
    buy_1pct_fill_rate DECIMAL(10,6) NULL COMMENT '1%档买入比例',
    not_filled_rate DECIMAL(10,6) NULL COMMENT '未成交比例',

    -- 止盈统计
    take_profit_1pct_rate DECIMAL(10,6) NULL COMMENT '触发1%止盈概率',
    take_profit_2pct_rate DECIMAL(10,6) NULL COMMENT '触发2%止盈概率',
    take_profit_3pct_rate DECIMAL(10,6) NULL COMMENT '触发3%止盈概率',
    force_sell_rate DECIMAL(10,6) NULL COMMENT '强制卖出概率',

    -- 收益统计
    avg_net_return_bps DECIMAL(10,6) NULL COMMENT '平均净收益(bps)',
    avg_gross_return_bps DECIMAL(10,6) NULL COMMENT '平均毛收益(bps)',
    avg_take_profit_return_bps DECIMAL(10,6) NULL COMMENT '止盈成交平均收益(bps)',
    avg_force_sell_return_bps DECIMAL(10,6) NULL COMMENT '强制卖出平均收益(bps)',
    max_loss_bps DECIMAL(10,6) NULL COMMENT '历史最大亏损(bps)',

    -- 分档收益
    buy_3pct_avg_net_return_bps DECIMAL(10,6) NULL COMMENT '3%档买入平均净收益(bps)',
    buy_2pct_avg_net_return_bps DECIMAL(10,6) NULL COMMENT '2%档买入平均净收益(bps)',
    buy_1pct_avg_net_return_bps DECIMAL(10,6) NULL COMMENT '1%档买入平均净收益(bps)',

    -- 风险统计
    post_buy_drawdown_avg DECIMAL(10,6) NULL COMMENT '买入后平均最大浮亏',
    force_sell_loss_rate DECIMAL(10,6) NULL COMMENT '强制卖出亏损比例',

    -- 贝叶斯修正
    raw_win_rate DECIMAL(10,6) NULL COMMENT '原始胜率',
    adjusted_win_rate DECIMAL(10,6) NULL COMMENT '贝叶斯修正胜率',
    sample_count INT NOT NULL DEFAULT 0 COMMENT '有效样本数量',

    -- 元数据
    created_at DATETIME NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sss_v3 (trade_date, stock_code),
    KEY idx_sss_v3_stock_date (stock_code, trade_date),
    KEY idx_sss_v3_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3短样本统计';

-- ----------------------------------------------------------------------------
-- 4. V3 市场环境快照表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS market_context_snapshot_v3 (
    trade_date DATE NOT NULL COMMENT '交易日',

    -- 市场宽度与收益
    market_breadth_1430 DECIMAL(10,6) NULL COMMENT '14:30全市场上涨家数比例',
    market_return_1430 DECIMAL(10,6) NULL COMMENT '14:30全市场平均涨幅',
    market_strong_stock_ratio DECIMAL(10,6) NULL COMMENT '强势股比例',
    market_weak_stock_ratio DECIMAL(10,6) NULL COMMENT '弱势股比例',

    -- 涨跌停统计
    limit_up_count INT NULL COMMENT '涨停数量',
    limit_down_count INT NULL COMMENT '跌停数量',

    -- 市场状态
    market_regime VARCHAR(16) NULL COMMENT '市场状态（STRONG/NORMAL/WEAK/EXTREME_WEAK）',

    -- 全市场有效股票数
    total_valid_stocks INT NULL COMMENT '有效股票总数',
    total_filtered_stocks INT NULL COMMENT '过滤后股票数',

    -- 元数据
    created_at DATETIME NULL COMMENT '创建时间',
    PRIMARY KEY (trade_date),
    KEY idx_market_v3_regime (market_regime, trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3市场环境快照';

-- ----------------------------------------------------------------------------
-- 5. V3 板块环境快照表
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sector_context_snapshot_v3 (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '自增主键',
    trade_date DATE NOT NULL COMMENT '交易日',
    sector_name VARCHAR(64) NOT NULL COMMENT '板块名称',

    -- 板块指标
    sector_strength_1430 DECIMAL(10,6) NULL COMMENT '板块14:30平均涨幅',
    sector_breadth_1430 DECIMAL(10,6) NULL COMMENT '板块内上涨股票比例',
    sector_volume_ratio DECIMAL(10,6) NULL COMMENT '板块量能强度',
    sector_rank INT NULL COMMENT '板块强度排名',
    stock_count INT NULL COMMENT '板块有效股票数量',

    -- 元数据
    created_at DATETIME NULL COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_sector_v3 (trade_date, sector_name),
    KEY idx_sector_v3_date (trade_date),
    KEY idx_sector_v3_rank (trade_date, sector_rank)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='V3板块环境快照';

-- ============================================================================
-- ALTER: 回测 TopK 汇总表增加基线对照字段
-- ============================================================================
ALTER TABLE backtest_topk_summary
    ADD COLUMN IF NOT EXISTS baseline_type VARCHAR(32) NULL COMMENT '基线类型: TOP_K / RANDOM / BOTTOM_K / MIDDLE_K',
    ADD COLUMN IF NOT EXISTS random_iteration INT NULL COMMENT '随机迭代次数（仅RANDOM基线）',
    ADD COLUMN IF NOT EXISTS sharpe_ratio DECIMAL(10,6) NULL COMMENT '年化夏普比率',
    ADD COLUMN IF NOT EXISTS max_drawdown_bps DECIMAL(18,4) NULL COMMENT '最大回撤(bps)';
