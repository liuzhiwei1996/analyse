CREATE TABLE IF NOT EXISTS market_regime_snapshot (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    regime VARCHAR(20) NOT NULL,
    index_trend DECIMAL(10, 4),
    breadth_ratio DECIMAL(10, 4),
    turnover_ratio DECIMAL(10, 4),
    atr20_percentile DECIMAL(10, 4),
    tail_strength DECIMAL(10, 4),
    reasoning VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_market_regime_trade_date (trade_date),
    KEY idx_market_regime_regime (regime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS candidate_filter_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    filter_name VARCHAR(64) NOT NULL,
    passed TINYINT(1) NOT NULL,
    rejection_reason VARCHAR(500),
    details VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_candidate_filter (trade_date, stock_code, filter_name),
    KEY idx_candidate_filter_trade_date (trade_date),
    KEY idx_candidate_filter_stock_code (stock_code),
    KEY idx_candidate_filter_passed (passed)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS portfolio_decision (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    rank_value DECIMAL(18, 6),
    p_win DECIMAL(10, 4),
    avg_win_bps DECIMAL(18, 4),
    avg_loss_bps DECIMAL(18, 4),
    cost_bps DECIMAL(18, 4),
    risk_unit DECIMAL(18, 4),
    position_pct DECIMAL(10, 4),
    regime VARCHAR(20) NOT NULL,
    reasoning VARCHAR(1000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_portfolio_decision (trade_date, stock_code),
    KEY idx_portfolio_decision_trade_date (trade_date),
    KEY idx_portfolio_decision_rank (trade_date, rank_value),
    KEY idx_portfolio_decision_regime (regime)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS position_plan (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    position_size DECIMAL(18, 4),
    entry_price DECIMAL(18, 4),
    stop_loss_price DECIMAL(18, 4),
    take_profit_price DECIMAL(18, 4),
    time_stop_date DATE,
    risk_budget_pct DECIMAL(10, 4),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_position_plan (trade_date, stock_code),
    KEY idx_position_plan_trade_date (trade_date),
    KEY idx_position_plan_stock_code (stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS shadow_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    stock_code VARCHAR(20) NOT NULL,
    signal_type VARCHAR(64) NOT NULL,
    entry_price DECIMAL(18, 4),
    next_day_open DECIMAL(18, 4),
    next_day_close DECIMAL(18, 4),
    pnl_bps DECIMAL(18, 4),
    drawdown_bps DECIMAL(18, 4),
    exit_reason VARCHAR(500),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_shadow_record_trade_date (trade_date),
    KEY idx_shadow_record_stock_code (stock_code),
    KEY idx_shadow_record_signal_type (signal_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS risk_alert (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    alert_type VARCHAR(64) NOT NULL,
    severity VARCHAR(20) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    related_stock_code VARCHAR(20),
    resolved TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_risk_alert_trade_date (trade_date),
    KEY idx_risk_alert_severity (severity),
    KEY idx_risk_alert_resolved (resolved),
    KEY idx_risk_alert_stock_code (related_stock_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS strategy_run_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    trade_date DATE NOT NULL,
    run_type VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL,
    start_time DATETIME NOT NULL,
    end_time DATETIME,
    stock_count INT DEFAULT 0,
    error_message VARCHAR(2000),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_strategy_run_log_trade_date (trade_date),
    KEY idx_strategy_run_log_status (status),
    KEY idx_strategy_run_log_run_type (run_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
