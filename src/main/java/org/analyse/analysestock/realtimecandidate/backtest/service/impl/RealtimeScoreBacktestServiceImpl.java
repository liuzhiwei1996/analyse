package org.analyse.analysestock.realtimecandidate.backtest.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.entity.BacktestDailySummary;
import org.analyse.analysestock.analysis.entity.BacktestTask;
import org.analyse.analysestock.analysis.entity.BacktestTopkSummary;
import org.analyse.analysestock.analysis.entity.BacktestTradeDetail;
import org.analyse.analysestock.analysis.entity.RealtimeCandidateScoreResult;
import org.analyse.analysestock.analysis.entity.StockTailTradeSnapshot;
import org.analyse.analysestock.analysis.mapper.BacktestDailySummaryMapper;
import org.analyse.analysestock.analysis.mapper.BacktestTaskMapper;
import org.analyse.analysestock.analysis.mapper.BacktestTopkSummaryMapper;
import org.analyse.analysestock.analysis.mapper.BacktestTradeDetailMapper;
import org.analyse.analysestock.analysis.mapper.RealtimeCandidateScoreResultMapper;
import org.analyse.analysestock.analysis.mapper.StockTailTradeSnapshotMapper;
import org.analyse.analysestock.analysis.mapper.TradingDateMapper;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestSummaryResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskCreateResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTaskStatusResponse;
import org.analyse.analysestock.realtimecandidate.backtest.dto.BacktestTopKResult;
import org.analyse.analysestock.realtimecandidate.backtest.dto.RealtimeScoreBacktestRequest;
import org.analyse.analysestock.realtimecandidate.backtest.service.RealtimeScoreBacktestService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * 实时候选股评分回测实现。
 *
 * <p>第一版只验证评分排序能力：读取已落库评分结果，固定每日 TopK 名单，
 * 再在不同成本场景下计算 T 日 14:30 到 T+1 日 09:30-09:45 的收益。</p>
 */
@Service
@Slf4j
public class RealtimeScoreBacktestServiceImpl implements RealtimeScoreBacktestService {

    /**
     * 任务尚未进入线程池执行。
     */
    private static final String STATUS_PENDING = "PENDING";
    /**
     * 任务正在执行。
     */
    private static final String STATUS_RUNNING = "RUNNING";
    /**
     * 任务执行完成。
     */
    private static final String STATUS_FINISHED = "FINISHED";
    /**
     * 任务执行失败。
     */
    private static final String STATUS_FAILED = "FAILED";
    /**
     * 未传策略版本时使用的默认评分版本。
     */
    private static final String DEFAULT_STRATEGY_VERSION = "V1";

    /**
     * 新的分阶段低吸买入 + 次日早盘分阶段止盈卖出策略版本。
     */
    private static final String STRATEGY_LIMIT_BUY_TAKE_PROFIT_V1 = "REALTIME_CANDIDATE_LIMIT_BUY_TAKE_PROFIT_V1";
    /**
     * 收益率转换为 bps 的乘数。
     */
    private static final BigDecimal BPS_BASE = new BigDecimal("10000");
    /**
     * 回测收益和比例统一保留的小数位。
     */
    private static final int SCALE = 4;
    /**
     * 批量写库大小，避免一次 SQL 太大。
     */
    private static final int BATCH_SIZE = 500;

    /**
     * 回测任务主表。
     */
    @Autowired
    private BacktestTaskMapper backtestTaskMapper;

    /**
     * TopK 汇总表。
     */
    @Autowired
    private BacktestTopkSummaryMapper backtestTopkSummaryMapper;

    /**
     * 每日组合汇总表。
     */
    @Autowired
    private BacktestDailySummaryMapper backtestDailySummaryMapper;

    /**
     * 选股收益明细表。
     */
    @Autowired
    private BacktestTradeDetailMapper backtestTradeDetailMapper;

    /**
     * 已落库的每日全市场评分结果。
     */
    @Autowired
    private RealtimeCandidateScoreResultMapper realtimeCandidateScoreResultMapper;

    /**
     * 14:30 买入价和次日早盘卖出 VWAP 快照。
     */
    @Autowired
    private StockTailTradeSnapshotMapper stockTailTradeSnapshotMapper;

    /**
     * 盘中执行快照。
     */
    @Autowired
    private org.analyse.analysestock.analysis.mapper.StockIntradayExecutionSnapshotMapper stockIntradayExecutionSnapshotMapper;

    /**
     * 交易日历，用于计算 T+1。
     */
    @Autowired
    private TradingDateMapper tradingDateMapper;

    /**
     * 复用项目已有线程池异步执行回测任务。
     */
    @Autowired
    @Qualifier("importExecutor")
    private Executor importExecutor;

    /**
     * 创建任务记录，并将实际计算提交到线程池。
     */
    @Override
    public BacktestTaskCreateResponse runBacktest(RealtimeScoreBacktestRequest request) {
        RealtimeScoreBacktestRequest normalized = normalizeRequest(request);
        String taskId = UUID.randomUUID().toString().replace("-", "");

        BacktestTask task = new BacktestTask();
        task.setTaskId(taskId);
        task.setStartDate(normalized.getStartDate());
        task.setEndDate(normalized.getEndDate());
        task.setTopKList(JSON.toJSONString(normalized.getTopKList()));
        task.setCostScenarioBpsList(JSON.toJSONString(normalized.getCostScenarioBpsList()));
        task.setStrategyVersion(normalized.getStrategyVersion());
        task.setStatus(STATUS_PENDING);
        task.setBacktestTradeDays(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setRequestJson(JSON.toJSONString(normalized));
        backtestTaskMapper.insert(task);

        CompletableFuture.runAsync(() -> executeTask(taskId, normalized), importExecutor);
        return new BacktestTaskCreateResponse(taskId, STATUS_PENDING);
    }

    /**
     * 返回任务状态；第一版只提供粗粒度进度，不维护逐日百分比。
     */
    @Override
    public BacktestTaskStatusResponse getTaskStatus(String taskId) {
        BacktestTask task = getRequiredTask(taskId);
        BacktestTaskStatusResponse response = new BacktestTaskStatusResponse();
        response.setTaskId(task.getTaskId());
        response.setStatus(task.getStatus());
        response.setProgress(resolveProgress(task.getStatus()));
        response.setErrorMessage(task.getErrorMessage());
        response.setCreatedAt(task.getCreatedAt());
        response.setStartedAt(task.getStartedAt());
        response.setFinishedAt(task.getFinishedAt());
        return response;
    }

    /**
     * 读取 TopK 汇总表并组装前端需要的成本敏感性结构。
     */
    @Override
    public BacktestSummaryResponse getSummary(String taskId) {
        BacktestTask task = getRequiredTask(taskId);
        List<BacktestTopkSummary> summaries = backtestTopkSummaryMapper.selectList(
                new LambdaQueryWrapper<BacktestTopkSummary>()
                        .eq(BacktestTopkSummary::getTaskId, taskId)
                        .orderByAsc(BacktestTopkSummary::getCostBps)
                        .orderByAsc(BacktestTopkSummary::getTopK)
        );
        return buildSummaryResponse(task, summaries);
    }

    /**
     * 查询每日组合收益，用于前端查看哪天赚、哪天亏。
     */
    @Override
    public List<BacktestDailySummary> listDaily(String taskId, Integer topK, BigDecimal costBps, LocalDate startDate, LocalDate endDate) {
        getRequiredTask(taskId);
        LambdaQueryWrapper<BacktestDailySummary> query = new LambdaQueryWrapper<>();
        query.eq(BacktestDailySummary::getTaskId, taskId);
        if (topK != null) {
            query.eq(BacktestDailySummary::getTopK, topK);
        }
        if (costBps != null) {
            query.eq(BacktestDailySummary::getCostBps, costBps);
        }
        if (startDate != null) {
            query.ge(BacktestDailySummary::getTradeDate, startDate);
        }
        if (endDate != null) {
            query.le(BacktestDailySummary::getTradeDate, endDate);
        }
        query.orderByAsc(BacktestDailySummary::getTradeDate)
                .orderByAsc(BacktestDailySummary::getCostBps)
                .orderByAsc(BacktestDailySummary::getTopK);
        return backtestDailySummaryMapper.selectList(query);
    }

    /**
     * 查询单票明细；因为明细不按 TopK 冗余，所以 TopK 条件用 rankNo <= topK 表达。
     */
    @Override
    public List<BacktestTradeDetail> listDetails(String taskId, LocalDate tradeDate, Integer topK, BigDecimal costBps, String stockCode) {
        getRequiredTask(taskId);
        QueryWrapper<BacktestTradeDetail> query = new QueryWrapper<>();
        query.eq("task_id", taskId);
        if (tradeDate != null) {
            query.eq("trade_date", tradeDate);
        }
        if (topK != null) {
            query.le("rank_no", topK);
        }
        if (costBps != null) {
            query.eq("cost_bps", costBps);
        }
        if (StringUtils.hasText(stockCode)) {
            query.eq("stock_code", stockCode);
        }
        query.orderByAsc("trade_date", "cost_bps", "rank_no", "stock_code");
        return backtestTradeDetailMapper.selectList(query);
    }

    /**
     * 回测任务执行入口。
     *
     * <p>这里负责状态流转、旧结果清理、计算结果落库和失败捕获。</p>
     */
    private void executeTask(String taskId, RealtimeScoreBacktestRequest request) {
        markTaskRunning(taskId);
        try {
            backtestTopkSummaryMapper.deleteByTaskId(taskId);
            backtestDailySummaryMapper.deleteByTaskId(taskId);
            backtestTradeDetailMapper.deleteByTaskId(taskId);

            CalculationResult result = calculate(taskId, request);
            insertDetails(result.getTradeDetails());
            insertDailySummaries(result.getDailySummaries());
            insertTopkSummaries(result.getTopkSummaries());

            BacktestTask task = getRequiredTask(taskId);
            task.setStatus(STATUS_FINISHED);
            task.setBacktestTradeDays(result.getBacktestTradeDays());
            task.setFinishedAt(LocalDateTime.now());
            task.setErrorMessage(null);
            task.setSummaryJson(JSON.toJSONString(buildSummaryResponse(task, result.getTopkSummaries())));
            backtestTaskMapper.updateById(task);
            log.info("realtime score backtest finished, taskId={}, tradeDays={}", taskId, result.getBacktestTradeDays());
        } catch (Exception e) {
            log.error("realtime score backtest failed, taskId={}", taskId, e);
            markTaskFailed(taskId, e);
        }
    }

    /**
     * 执行核心回测计算。
     *
     * <p>选股名单只由评分结果决定；成本场景只改变净收益，不影响排序和 TopK 名单。</p>
     */
    private CalculationResult calculate(String taskId, RealtimeScoreBacktestRequest request) {
        List<RealtimeCandidateScoreResult> scores = loadScores(request);
        if (CollectionUtils.isEmpty(scores)) {
            return emptyCalculationResult(taskId, request);
        }

        Map<LocalDate, List<RealtimeCandidateScoreResult>> scoreMap = scores.stream()
                .collect(Collectors.groupingBy(RealtimeCandidateScoreResult::getTradeDate, LinkedHashMap::new, Collectors.toList()));
        Map<String, StockTailTradeSnapshot> snapshotMap = loadTailSnapshots(request);
        Map<String, org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot> executionMap = loadExecutionSnapshots(request);
        Map<LocalDate, LocalDate> nextDateMap = loadNextTradeDates(scoreMap.keySet());

        List<BacktestTradeDetail> allDetails = new ArrayList<>();
        List<BacktestDailySummary> allDailySummaries = new ArrayList<>();
        Set<LocalDate> activeTradeDates = new HashSet<>();
        int maxTopK = Collections.max(request.getTopKList());

        for (Map.Entry<LocalDate, List<RealtimeCandidateScoreResult>> entry : scoreMap.entrySet()) {
            LocalDate tradeDate = entry.getKey();
            List<RealtimeCandidateScoreResult> sortedScores = sortScores(entry.getValue());
            if (sortedScores.isEmpty()) {
                continue;
            }

            int selectedLimit = Math.min(maxTopK, sortedScores.size());
            List<RealtimeCandidateScoreResult> selectedScores = sortedScores.subList(0, selectedLimit);
            LocalDate nextTradeDate = nextDateMap.get(tradeDate);

            for (BigDecimal costScenarioBps : request.getCostScenarioBpsList()) {
                List<BacktestTradeDetail> scenarioDetails = new ArrayList<>();
                for (int i = 0; i < selectedScores.size(); i++) {
                    RealtimeCandidateScoreResult score = selectedScores.get(i);
                    // 使用本次稳定排序后的名次，避免原始 rankNo 为空或断档影响 rankNo <= topK 查询。
                    Integer effectiveRankNo = i + 1;
                    BacktestTradeDetail detail;
                    if (STRATEGY_LIMIT_BUY_TAKE_PROFIT_V1.equals(request.getStrategyVersion())) {
                        detail = buildTradeDetailV2(taskId, request, score, effectiveRankNo, nextTradeDate, executionMap, costScenarioBps);
                    } else {
                        detail = buildTradeDetail(taskId, request, score, effectiveRankNo, nextTradeDate, snapshotMap, costScenarioBps);
                    }
                    scenarioDetails.add(detail);
                }
                allDetails.addAll(scenarioDetails);

                for (Integer topK : request.getTopKList()) {
                    List<BacktestTradeDetail> validDetails = scenarioDetails.stream()
                            .filter(detail -> detail.getRankNo() != null && detail.getRankNo() <= topK)
                            .filter(detail -> detail.getNetReturnBps() != null)
                            .collect(Collectors.toList());
                    if (validDetails.isEmpty()) {
                        continue;
                    }
                    BacktestDailySummary dailySummary = buildDailySummary(taskId, tradeDate, nextTradeDate, topK, costScenarioBps, validDetails);
                    allDailySummaries.add(dailySummary);
                    activeTradeDates.add(tradeDate);
                }
            }
        }

        List<BacktestTopkSummary> topkSummaries = buildTopkSummaries(taskId, request, allDailySummaries, allDetails);
        CalculationResult result = new CalculationResult();
        result.setBacktestTradeDays(activeTradeDates.size());
        result.setTradeDetails(allDetails);
        result.setDailySummaries(allDailySummaries);
        result.setTopkSummaries(topkSummaries);
        return result;
    }

    /**
     * 加载回测区间内的评分结果。
     *
     * <p>该方法不调用评分引擎，确保回测只基于历史已落库评分。</p>
     * <p>策略版本（如 REALTIME_CANDIDATE_LIMIT_BUY_TAKE_PROFIT_V1）属于回测买卖规则，
     * 评分数据统一按默认版本 V1 加载，不影响评分过滤。</p>
     */
    private List<RealtimeCandidateScoreResult> loadScores(RealtimeScoreBacktestRequest request) {
        LambdaQueryWrapper<RealtimeCandidateScoreResult> query = new LambdaQueryWrapper<>();
        query.ge(RealtimeCandidateScoreResult::getTradeDate, request.getStartDate())
                .le(RealtimeCandidateScoreResult::getTradeDate, request.getEndDate());
        // 回测策略版本不用于过滤评分，评分统一加载默认版本
        query.eq(RealtimeCandidateScoreResult::getStrategyVersion, DEFAULT_STRATEGY_VERSION);
        if (Boolean.TRUE.equals(request.getExcludeInvalid())) {
            query.eq(RealtimeCandidateScoreResult::getValidFlag, true);
        }
        if (request.getMinScore() != null) {
            query.ge(RealtimeCandidateScoreResult::getFinalScore, request.getMinScore());
        }
        query.orderByAsc(RealtimeCandidateScoreResult::getTradeDate)
                .orderByAsc(RealtimeCandidateScoreResult::getRankNo)
                .orderByAsc(RealtimeCandidateScoreResult::getStockCode);

        List<RealtimeCandidateScoreResult> scores = realtimeCandidateScoreResultMapper.selectList(query);
        if (!StringUtils.hasText(request.getMinConfidenceLevel())) {
            return scores;
        }

        int minRank = confidenceRank(request.getMinConfidenceLevel());
        return scores.stream()
                .filter(score -> confidenceRank(score.getConfidenceLevel()) >= minRank)
                .collect(Collectors.toList());
    }

    /**
     * 加载尾盘快照，并按 tradeDate + stockCode 建立索引。
     */
    private Map<String, StockTailTradeSnapshot> loadTailSnapshots(RealtimeScoreBacktestRequest request) {
        LambdaQueryWrapper<StockTailTradeSnapshot> query = new LambdaQueryWrapper<>();
        query.ge(StockTailTradeSnapshot::getTradeDate, request.getStartDate())
                .le(StockTailTradeSnapshot::getTradeDate, request.getEndDate());
        List<StockTailTradeSnapshot> snapshots = stockTailTradeSnapshotMapper.selectList(query);
        Map<String, StockTailTradeSnapshot> snapshotMap = new HashMap<>();
        for (StockTailTradeSnapshot snapshot : snapshots) {
            snapshotMap.put(snapshotKey(snapshot.getTradeDate(), snapshot.getStockCode()), snapshot);
        }
        return snapshotMap;
    }

    /**
     * 加载盘中执行快照。
     */
    private Map<String, org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot> loadExecutionSnapshots(RealtimeScoreBacktestRequest request) {
        LambdaQueryWrapper<org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot> query = new LambdaQueryWrapper<>();
        // 买入需要在 T 日，卖出需要在 T+1 日，所以范围需要覆盖 T 到 T+1
        // 实际上 endDate 可能没有 T+1，所以多加载一些日期是安全的
        query.ge(org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot::getTradeDate, request.getStartDate())
                .le(org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot::getTradeDate, request.getEndDate().plusDays(10));
        List<org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot> list = stockIntradayExecutionSnapshotMapper.selectList(query);
        Map<String, org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot> map = new HashMap<>();
        for (org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot snapshot : list) {
            map.put(snapshotKey(snapshot.getTradeDate(), snapshot.getStockCode()), snapshot);
        }
        return map;
    }

    /**
     * 为每个评分交易日计算下一交易日。
     */
    private Map<LocalDate, LocalDate> loadNextTradeDates(Set<LocalDate> tradeDates) {
        Map<LocalDate, LocalDate> nextDateMap = new HashMap<>();
        for (LocalDate tradeDate : tradeDates) {
            LocalDate nextTradeDate = tradingDateMapper.findTradingDateSqlServerStockCode(tradeDate, 0);
            nextDateMap.put(tradeDate, nextTradeDate);
        }
        return nextDateMap;
    }

    /**
     * 按 finalScore 降序、rankNo 升序、stockCode 升序稳定排序。
     */
    private List<RealtimeCandidateScoreResult> sortScores(List<RealtimeCandidateScoreResult> scores) {
        List<RealtimeCandidateScoreResult> sorted = new ArrayList<>(scores);
        sorted.sort((left, right) -> {
            BigDecimal leftScore = left.getFinalScore() == null ? BigDecimal.ZERO : left.getFinalScore();
            BigDecimal rightScore = right.getFinalScore() == null ? BigDecimal.ZERO : right.getFinalScore();
            int scoreCompare = rightScore.compareTo(leftScore);
            if (scoreCompare != 0) {
                return scoreCompare;
            }

            Integer leftRank = left.getRankNo() == null ? Integer.MAX_VALUE : left.getRankNo();
            Integer rightRank = right.getRankNo() == null ? Integer.MAX_VALUE : right.getRankNo();
            int rankCompare = leftRank.compareTo(rightRank);
            if (rankCompare != 0) {
                return rankCompare;
            }

            String leftCode = left.getStockCode() == null ? "" : left.getStockCode();
            String rightCode = right.getStockCode() == null ? "" : right.getStockCode();
            return leftCode.compareTo(rightCode);
        });
        return sorted;
    }

    /**
     * 构建分阶段买卖回测明细。
     */
    private BacktestTradeDetail buildTradeDetailV2(
            String taskId,
            RealtimeScoreBacktestRequest request,
            RealtimeCandidateScoreResult score,
            Integer effectiveRankNo,
            LocalDate nextTradeDate,
            Map<String, org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot> executionMap,
            BigDecimal costScenarioBps
    ) {
        LocalDateTime now = LocalDateTime.now();
        org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot tSnapshot = executionMap.get(snapshotKey(score.getTradeDate(), score.getStockCode()));
        org.analyse.analysestock.analysis.entity.StockIntradayExecutionSnapshot tPlus1Snapshot = nextTradeDate == null ? null : executionMap.get(snapshotKey(nextTradeDate, score.getStockCode()));

        BigDecimal basePrice = score.getPrice1430();
        if (basePrice == null && tSnapshot != null) {
            basePrice = tSnapshot.getPrice1430();
        }

        BacktestTradeDetail detail = new BacktestTradeDetail();
        detail.setTaskId(taskId);
        detail.setTradeDate(score.getTradeDate());
        detail.setNextTradeDate(nextTradeDate);
        detail.setCostBps(costScenarioBps);
        detail.setStockCode(score.getStockCode());
        detail.setShortName(score.getShortName());
        detail.setRankNo(effectiveRankNo);
        detail.setScore(score.getFinalScore());
        detail.setConfidenceLevel(score.getConfidenceLevel());
        detail.setBuyPrice1430(basePrice);
        setCostComponents(detail, request, costScenarioBps);
        detail.setCreatedAt(now);

        if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
            detail.setInvalidReason("NO_BASE_PRICE_1430");
            return detail;
        }

        // 1. 买入判断
        boolean bought = false;
        if (tSnapshot != null) {
            // 14:35 后跌 3%
            BigDecimal trigger3 = basePrice.multiply(new BigDecimal("0.97"));
            if (tSnapshot.getLow14351444() != null && tSnapshot.getLow14351444().compareTo(trigger3) <= 0) {
                detail.setBuyTriggerPrice(trigger3);
                detail.setBuyPrice(trigger3);
                detail.setBuyTime("14:35-14:44");
                detail.setBuyRule("BUY_DROP_3PCT_AFTER_1435");
                bought = true;
            } else {
                // 14:45 后跌 2%
                BigDecimal trigger2 = basePrice.multiply(new BigDecimal("0.98"));
                if (tSnapshot.getLow14451454() != null && tSnapshot.getLow14451454().compareTo(trigger2) <= 0) {
                    detail.setBuyTriggerPrice(trigger2);
                    detail.setBuyPrice(trigger2);
                    detail.setBuyTime("14:45-14:54");
                    detail.setBuyRule("BUY_DROP_2PCT_AFTER_1445");
                    bought = true;
                } else {
                    // 14:55 后跌 1%
                    BigDecimal trigger1 = basePrice.multiply(new BigDecimal("0.99"));
                    if (tSnapshot.getLow14551500() != null && tSnapshot.getLow14551500().compareTo(trigger1) <= 0) {
                        detail.setBuyTriggerPrice(trigger1);
                        detail.setBuyPrice(trigger1);
                        detail.setBuyTime("14:55-15:00");
                        detail.setBuyRule("BUY_DROP_1PCT_AFTER_1455");
                        bought = true;
                    }
                }
            }
        }

        if (!bought) {
            detail.setBuyFilled(false);
            detail.setBuyRule("NOT_FILLED");
            return detail;
        }
        detail.setBuyFilled(true);

        // 2. 卖出判断
        if (nextTradeDate == null) {
            detail.setInvalidReason("NO_NEXT_TRADE_DATE");
            return detail;
        }
        if (tPlus1Snapshot == null) {
            detail.setInvalidReason("NO_T_PLUS_1_SNAPSHOT");
            return detail;
        }

        BigDecimal buyPrice = detail.getBuyPrice();
        boolean sold = false;
        // 09:35 前涨 3%
        BigDecimal sellTrigger3 = buyPrice.multiply(new BigDecimal("1.03"));
        if (tPlus1Snapshot.getHigh09300935() != null && tPlus1Snapshot.getHigh09300935().compareTo(sellTrigger3) >= 0) {
            detail.setSellTriggerPrice(sellTrigger3);
            detail.setSellPrice(sellTrigger3);
            detail.setSellTime("09:30-09:35");
            detail.setSellRule("SELL_PROFIT_3PCT_BEFORE_0935");
            sold = true;
        } else {
            // 09:40 前涨 2%
            BigDecimal sellTrigger2 = buyPrice.multiply(new BigDecimal("1.02"));
            if (tPlus1Snapshot.getHigh09360940() != null && tPlus1Snapshot.getHigh09360940().compareTo(sellTrigger2) >= 0) {
                detail.setSellTriggerPrice(sellTrigger2);
                detail.setSellPrice(sellTrigger2);
                detail.setSellTime("09:36-09:40");
                detail.setSellRule("SELL_PROFIT_2PCT_BEFORE_0940");
                sold = true;
            } else {
                // 09:44 前涨 1%
                BigDecimal sellTrigger1 = buyPrice.multiply(new BigDecimal("1.01"));
                if (tPlus1Snapshot.getHigh09410944() != null && tPlus1Snapshot.getHigh09410944().compareTo(sellTrigger1) >= 0) {
                    detail.setSellTriggerPrice(sellTrigger1);
                    detail.setSellPrice(sellTrigger1);
                    detail.setSellTime("09:41-09:44");
                    detail.setSellRule("SELL_PROFIT_1PCT_BEFORE_0944");
                    sold = true;
                } else {
                    // 09:45 强制卖出
                    if (tPlus1Snapshot.getPrice0945() != null) {
                        detail.setSellPrice(tPlus1Snapshot.getPrice0945());
                        detail.setSellTime("09:45");
                        detail.setSellRule("FORCE_SELL_0945");
                        sold = true;
                    } else {
                        detail.setInvalidReason("NO_SELL_PRICE_0945");
                        return detail;
                    }
                }
            }
        }
        detail.setSellFilled(sold);

        BigDecimal sellPrice = detail.getSellPrice();
        BigDecimal grossReturnBps = sellPrice.subtract(buyPrice)
                .divide(buyPrice, 8, RoundingMode.HALF_UP)
                .multiply(BPS_BASE)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal netReturnBps = grossReturnBps.subtract(costScenarioBps).setScale(SCALE, RoundingMode.HALF_UP);
        detail.setGrossReturnBps(grossReturnBps);
        detail.setNetReturnBps(netReturnBps);
        detail.setWinFlag(netReturnBps.compareTo(BigDecimal.ZERO) > 0);

        return detail;
    }
    private BacktestTradeDetail buildTradeDetail(
            String taskId,
            RealtimeScoreBacktestRequest request,
            RealtimeCandidateScoreResult score,
            Integer effectiveRankNo,
            LocalDate nextTradeDate,
            Map<String, StockTailTradeSnapshot> snapshotMap,
            BigDecimal costScenarioBps
    ) {
        LocalDateTime now = LocalDateTime.now();
        StockTailTradeSnapshot snapshot = snapshotMap.get(snapshotKey(score.getTradeDate(), score.getStockCode()));
        BigDecimal buyPrice = score.getPrice1430();
        if (buyPrice == null && snapshot != null) {
            buyPrice = snapshot.getPrice1430();
        }
        BigDecimal sellPrice = snapshot == null ? null : snapshot.getSellVwap09300945();

        BacktestTradeDetail detail = new BacktestTradeDetail();
        detail.setTaskId(taskId);
        detail.setTradeDate(score.getTradeDate());
        detail.setNextTradeDate(nextTradeDate);
        detail.setTopK(null);
        detail.setCostBps(costScenarioBps);
        detail.setStockCode(score.getStockCode());
        detail.setShortName(score.getShortName());
        detail.setRankNo(effectiveRankNo);
        detail.setScore(score.getFinalScore());
        detail.setConfidenceLevel(score.getConfidenceLevel());
        detail.setBuyPrice1430(buyPrice);
        detail.setSellVwap09300945(sellPrice);
        setCostComponents(detail, request, costScenarioBps);
        detail.setCreatedAt(now);

        String invalidReason = resolveInvalidReason(nextTradeDate, snapshot, buyPrice, sellPrice);
        if (invalidReason != null) {
            detail.setInvalidReason(invalidReason);
            return detail;
        }

        BigDecimal grossReturnBps = sellPrice.subtract(buyPrice)
                .divide(buyPrice, 8, RoundingMode.HALF_UP)
                .multiply(BPS_BASE)
                .setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal netReturnBps = grossReturnBps.subtract(costScenarioBps).setScale(SCALE, RoundingMode.HALF_UP);
        detail.setGrossReturnBps(grossReturnBps);
        detail.setNetReturnBps(netReturnBps);
        detail.setWinFlag(netReturnBps.compareTo(BigDecimal.ZERO) > 0);
        return detail;
    }

    /**
     * 保存成本拆分。
     *
     * <p>默认成本场景等于 costBps + slippageBps 时保留拆分；自定义成本场景按总成本保存。</p>
     */
    private void setCostComponents(BacktestTradeDetail detail, RealtimeScoreBacktestRequest request, BigDecimal costScenarioBps) {
        BigDecimal requestTotalCost = request.getCostBps().add(request.getSlippageBps());
        if (sameBps(requestTotalCost, costScenarioBps)) {
            detail.setCostBpsValue(request.getCostBps());
            detail.setSlippageBps(request.getSlippageBps());
            return;
        }
        detail.setCostBpsValue(costScenarioBps);
        detail.setSlippageBps(BigDecimal.ZERO);
    }

    /**
     * 判断单票是否具备完整收益计算数据。
     */
    private String resolveInvalidReason(LocalDate nextTradeDate, StockTailTradeSnapshot snapshot, BigDecimal buyPrice, BigDecimal sellPrice) {
        if (nextTradeDate == null) {
            return "NO_NEXT_TRADE_DATE";
        }
        if (snapshot == null) {
            return "NO_TAIL_SNAPSHOT";
        }
        if (buyPrice == null) {
            return "NO_BUY_PRICE_1430";
        }
        if (buyPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "INVALID_BUY_PRICE_1430";
        }
        if (sellPrice == null) {
            return "NO_SELL_VWAP";
        }
        if (sellPrice.compareTo(BigDecimal.ZERO) <= 0) {
            return "INVALID_SELL_VWAP";
        }
        return null;
    }

    /**
     * 由单票明细聚合出某日某 TopK 组合收益。
     */
    private BacktestDailySummary buildDailySummary(
            String taskId,
            LocalDate tradeDate,
            LocalDate nextTradeDate,
            Integer topK,
            BigDecimal costBps,
            List<BacktestTradeDetail> validDetails
    ) {
        BacktestDailySummary summary = new BacktestDailySummary();
        summary.setTaskId(taskId);
        summary.setTradeDate(tradeDate);
        summary.setNextTradeDate(nextTradeDate);
        summary.setTopK(topK);
        summary.setCostBps(costBps);
        summary.setSelectedCount(validDetails.size());
        summary.setAvgGrossReturnBps(avg(validDetails.stream().map(BacktestTradeDetail::getGrossReturnBps).collect(Collectors.toList())));
        summary.setAvgCostBps(costBps);
        summary.setAvgNetReturnBps(avg(validDetails.stream().map(BacktestTradeDetail::getNetReturnBps).collect(Collectors.toList())));
        summary.setWinFlag(summary.getAvgNetReturnBps().compareTo(BigDecimal.ZERO) > 0);

        BacktestTradeDetail best = validDetails.stream()
                .max(Comparator.comparing(BacktestTradeDetail::getNetReturnBps))
                .orElse(null);
        BacktestTradeDetail worst = validDetails.stream()
                .min(Comparator.comparing(BacktestTradeDetail::getNetReturnBps))
                .orElse(null);
        if (best != null) {
            summary.setBestStockCode(best.getStockCode());
            summary.setBestStockReturnBps(best.getNetReturnBps());
        }
        if (worst != null) {
            summary.setWorstStockCode(worst.getStockCode());
            summary.setWorstStockReturnBps(worst.getNetReturnBps());
        }
        summary.setCreatedAt(LocalDateTime.now());
        return summary;
    }

    /**
     * 按成本场景和 TopK 生成汇总结果。
     */
    private List<BacktestTopkSummary> buildTopkSummaries(
            String taskId,
            RealtimeScoreBacktestRequest request,
            List<BacktestDailySummary> dailySummaries,
            List<BacktestTradeDetail> tradeDetails
    ) {
        List<BacktestTopkSummary> summaries = new ArrayList<>();
        for (BigDecimal costBps : request.getCostScenarioBpsList()) {
            for (Integer topK : request.getTopKList()) {
                List<BacktestDailySummary> matchedDaily = dailySummaries.stream()
                        .filter(daily -> Objects.equals(daily.getTopK(), topK))
                        .filter(daily -> sameBps(daily.getCostBps(), costBps))
                        .collect(Collectors.toList());
                List<BacktestTradeDetail> matchedDetails = tradeDetails.stream()
                        .filter(detail -> detail.getRankNo() != null && detail.getRankNo() <= topK)
                        .filter(detail -> sameBps(detail.getCostBps(), costBps))
                        .collect(Collectors.toList());
                summaries.add(buildTopkSummary(taskId, topK, costBps, matchedDaily, matchedDetails));
            }
        }
        return summaries;
    }

    /**
     * 汇总单个 TopK + 成本场景的平均收益、胜率、总收益和最大单日亏损。
     */
    private BacktestTopkSummary buildTopkSummary(
            String taskId,
            Integer topK,
            BigDecimal costBps,
            List<BacktestDailySummary> dailySummaries,
            List<BacktestTradeDetail> tradeDetails
    ) {
        BacktestTopkSummary summary = new BacktestTopkSummary();
        summary.setTaskId(taskId);
        summary.setTopK(topK);
        summary.setCostBps(costBps);
        summary.setTradeDays(dailySummaries.size());
        summary.setCreatedAt(LocalDateTime.now());

        if (dailySummaries.isEmpty()) {
            summary.setAvgNetReturnBps(BigDecimal.ZERO.setScale(SCALE));
            summary.setDailyWinRate(BigDecimal.ZERO.setScale(SCALE));
            summary.setStockWinRate(BigDecimal.ZERO.setScale(SCALE));
            summary.setTotalReturnBps(BigDecimal.ZERO.setScale(SCALE));
            summary.setMaxSingleDayLossBps(BigDecimal.ZERO.setScale(SCALE));
            summary.setAvgSelectedCount(BigDecimal.ZERO.setScale(SCALE));
            return summary;
        }

        summary.setAvgNetReturnBps(avg(dailySummaries.stream()
                .map(BacktestDailySummary::getAvgNetReturnBps)
                .collect(Collectors.toList())));
        summary.setDailyWinRate(rate(dailySummaries.stream()
                .filter(daily -> Boolean.TRUE.equals(daily.getWinFlag()))
                .count(), dailySummaries.size()));
        summary.setTotalReturnBps(sum(dailySummaries.stream()
                .map(BacktestDailySummary::getAvgNetReturnBps)
                .collect(Collectors.toList())));
        summary.setMaxSingleDayLossBps(dailySummaries.stream()
                .map(BacktestDailySummary::getAvgNetReturnBps)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO)
                .setScale(SCALE, RoundingMode.HALF_UP));
        summary.setAvgSelectedCount(avg(dailySummaries.stream()
                .map(daily -> BigDecimal.valueOf(daily.getSelectedCount()))
                .collect(Collectors.toList())));

        // 统计新指标
        summary.setCandidateCount(tradeDetails.size());
        long boughtCount = tradeDetails.stream().filter(d -> Boolean.TRUE.equals(d.getBuyFilled())).count();
        summary.setBoughtCount((int) boughtCount);
        summary.setBuyFillRate(rate(boughtCount, tradeDetails.size()));

        summary.setBuy3pctCount((int) tradeDetails.stream().filter(d -> "BUY_DROP_3PCT_AFTER_1435".equals(d.getBuyRule())).count());
        summary.setBuy2pctCount((int) tradeDetails.stream().filter(d -> "BUY_DROP_2PCT_AFTER_1445".equals(d.getBuyRule())).count());
        summary.setBuy1pctCount((int) tradeDetails.stream().filter(d -> "BUY_DROP_1PCT_AFTER_1455".equals(d.getBuyRule())).count());

        summary.setSell3pctCount((int) tradeDetails.stream().filter(d -> "SELL_PROFIT_3PCT_BEFORE_0935".equals(d.getSellRule())).count());
        summary.setSell2pctCount((int) tradeDetails.stream().filter(d -> "SELL_PROFIT_2PCT_BEFORE_0940".equals(d.getSellRule())).count());
        summary.setSell1pctCount((int) tradeDetails.stream().filter(d -> "SELL_PROFIT_1PCT_BEFORE_0944".equals(d.getSellRule())).count());
        summary.setForceSell0945Count((int) tradeDetails.stream().filter(d -> "FORCE_SELL_0945".equals(d.getSellRule())).count());

        long stockWinCount = tradeDetails.stream()
                .filter(detail -> detail.getNetReturnBps() != null && detail.getNetReturnBps().compareTo(BigDecimal.ZERO) > 0)
                .count();
        long validStockCount = tradeDetails.stream().filter(d -> d.getNetReturnBps() != null).count();
        summary.setStockWinRate(rate(stockWinCount, validStockCount));
        return summary;
    }

    /**
     * 无评分结果时仍生成空汇总，便于前端明确显示 0 个有效交易日。
     */
    private CalculationResult emptyCalculationResult(String taskId, RealtimeScoreBacktestRequest request) {
        CalculationResult result = new CalculationResult();
        result.setBacktestTradeDays(0);
        result.setTradeDetails(Collections.emptyList());
        result.setDailySummaries(Collections.emptyList());
        result.setTopkSummaries(buildTopkSummaries(taskId, request, Collections.emptyList(), Collections.emptyList()));
        return result;
    }

    /**
     * 归一化请求参数并做基础校验。
     */
    private RealtimeScoreBacktestRequest normalizeRequest(RealtimeScoreBacktestRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("request cannot be null");
        }
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (request.getEndDate().isBefore(request.getStartDate())) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        RealtimeScoreBacktestRequest normalized = new RealtimeScoreBacktestRequest();
        BeanUtils.copyProperties(request, normalized);

        normalized.setTopKList(normalizeTopKList(request.getTopKList()));
        normalized.setCostBps(defaultZero(request.getCostBps()));
        normalized.setSlippageBps(defaultZero(request.getSlippageBps()));
        normalized.setCostScenarioBpsList(normalizeCostScenarioList(
                request.getCostScenarioBpsList(),
                normalized.getCostBps().add(normalized.getSlippageBps())
        ));
        normalized.setStrategyVersion(StringUtils.hasText(request.getStrategyVersion())
                ? request.getStrategyVersion()
                : DEFAULT_STRATEGY_VERSION);
        normalized.setExcludeInvalid(request.getExcludeInvalid() == null || request.getExcludeInvalid());

        if (StringUtils.hasText(normalized.getMinConfidenceLevel()) && confidenceRank(normalized.getMinConfidenceLevel()) < 0) {
            throw new IllegalArgumentException("unknown minConfidenceLevel: " + normalized.getMinConfidenceLevel());
        }
        return normalized;
    }

    /**
     * 规范化 TopK 列表：去空、去重、排序。
     */
    private List<Integer> normalizeTopKList(List<Integer> topKList) {
        List<Integer> source = CollectionUtils.isEmpty(topKList) ? Arrays.asList(5, 10, 20) : topKList;
        List<Integer> normalized = source.stream()
                .filter(Objects::nonNull)
                .filter(topK -> topK > 0)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("topKList must contain at least one positive integer");
        }
        return normalized;
    }

    /**
     * 规范化成本场景；未传时补齐默认四组成本敏感性。
     */
    private List<BigDecimal> normalizeCostScenarioList(List<BigDecimal> costScenarioBpsList, BigDecimal defaultTotalCostBps) {
        List<BigDecimal> source = new ArrayList<>();
        if (CollectionUtils.isEmpty(costScenarioBpsList)) {
            source.add(BigDecimal.ZERO);
            source.add(new BigDecimal("10"));
            source.add(defaultTotalCostBps);
            source.add(new BigDecimal("50"));
        } else {
            source.addAll(costScenarioBpsList);
        }

        List<BigDecimal> normalized = new ArrayList<>();
        for (BigDecimal cost : source) {
            BigDecimal value = defaultZero(cost).setScale(SCALE, RoundingMode.HALF_UP);
            if (value.compareTo(BigDecimal.ZERO) < 0) {
                throw new IllegalArgumentException("cost bps cannot be negative");
            }
            if (normalized.stream().noneMatch(existing -> sameBps(existing, value))) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    /**
     * BigDecimal 空值归零，并统一精度。
     */
    private BigDecimal defaultZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(SCALE) : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 将任务标记为运行中。
     */
    private void markTaskRunning(String taskId) {
        BacktestTask task = new BacktestTask();
        task.setTaskId(taskId);
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        task.setErrorMessage(null);
        backtestTaskMapper.updateById(task);
    }

    /**
     * 将任务标记为失败，并截断过长错误信息。
     */
    private void markTaskFailed(String taskId, Exception e) {
        BacktestTask task = new BacktestTask();
        task.setTaskId(taskId);
        task.setStatus(STATUS_FAILED);
        task.setFinishedAt(LocalDateTime.now());
        String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        task.setErrorMessage(message.length() > 2000 ? message.substring(0, 2000) : message);
        backtestTaskMapper.updateById(task);
    }

    /**
     * 查询任务并保证任务存在。
     */
    private BacktestTask getRequiredTask(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("taskId is required");
        }
        BacktestTask task = backtestTaskMapper.selectById(taskId);
        if (task == null) {
            throw new IllegalArgumentException("backtest task not found: " + taskId);
        }
        return task;
    }

    /**
     * 状态到前端进度的简单映射。
     */
    private int resolveProgress(String status) {
        if (STATUS_FINISHED.equals(status) || STATUS_FAILED.equals(status)) {
            return 100;
        }
        if (STATUS_RUNNING.equals(status)) {
            return 50;
        }
        return 0;
    }

    /**
     * 分批写入单票明细。
     */
    private void insertDetails(List<BacktestTradeDetail> details) {
        if (CollectionUtils.isEmpty(details)) {
            return;
        }
        for (int i = 0; i < details.size(); i += BATCH_SIZE) {
            backtestTradeDetailMapper.insertBatch(details.subList(i, Math.min(i + BATCH_SIZE, details.size())));
        }
    }

    /**
     * 分批写入每日汇总。
     */
    private void insertDailySummaries(List<BacktestDailySummary> dailySummaries) {
        if (CollectionUtils.isEmpty(dailySummaries)) {
            return;
        }
        for (int i = 0; i < dailySummaries.size(); i += BATCH_SIZE) {
            backtestDailySummaryMapper.insertBatch(dailySummaries.subList(i, Math.min(i + BATCH_SIZE, dailySummaries.size())));
        }
    }

    /**
     * 分批写入 TopK 汇总。
     */
    private void insertTopkSummaries(List<BacktestTopkSummary> topkSummaries) {
        if (CollectionUtils.isEmpty(topkSummaries)) {
            return;
        }
        for (int i = 0; i < topkSummaries.size(); i += BATCH_SIZE) {
            backtestTopkSummaryMapper.insertBatch(topkSummaries.subList(i, Math.min(i + BATCH_SIZE, topkSummaries.size())));
        }
    }

    /**
     * 组装汇总接口响应。
     */
    private BacktestSummaryResponse buildSummaryResponse(BacktestTask task, List<BacktestTopkSummary> summaries) {
        BacktestSummaryResponse response = new BacktestSummaryResponse();
        response.setTaskId(task.getTaskId());
        response.setStartDate(task.getStartDate());
        response.setEndDate(task.getEndDate());
        response.setBacktestTradeDays(task.getBacktestTradeDays());
        response.setStrategyVersion(task.getStrategyVersion());

        List<BacktestTopKResult> results = summaries.stream()
                .map(this::toTopKResult)
                .collect(Collectors.toList());
        response.setTopKResults(results);

        Map<String, List<BacktestTopKResult>> costSensitivity = new LinkedHashMap<>();
        for (BacktestTopKResult result : results) {
            String key = formatBps(result.getCostBps());
            costSensitivity.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
        }
        response.setCostSensitivity(costSensitivity);
        return response;
    }

    /**
     * 将数据库汇总实体转换为接口结果对象。
     */
    private BacktestTopKResult toTopKResult(BacktestTopkSummary summary) {
        BacktestTopKResult result = new BacktestTopKResult();
        result.setTopK(summary.getTopK());
        result.setCostBps(summary.getCostBps());
        result.setTradeDays(summary.getTradeDays());
        result.setAvgNetReturnBps(summary.getAvgNetReturnBps());
        result.setDailyWinRate(summary.getDailyWinRate());
        result.setStockWinRate(summary.getStockWinRate());
        result.setTotalReturnBps(summary.getTotalReturnBps());
        result.setMaxSingleDayLossBps(summary.getMaxSingleDayLossBps());
        result.setAvgSelectedCount(summary.getAvgSelectedCount());

        result.setCandidateCount(summary.getCandidateCount());
        result.setBoughtCount(summary.getBoughtCount());
        result.setBuyFillRate(summary.getBuyFillRate());
        result.setBuy3pctCount(summary.getBuy3pctCount());
        result.setBuy2pctCount(summary.getBuy2pctCount());
        result.setBuy1pctCount(summary.getBuy1pctCount());
        result.setSell3pctCount(summary.getSell3pctCount());
        result.setSell2pctCount(summary.getSell2pctCount());
        result.setSell1pctCount(summary.getSell1pctCount());
        result.setForceSell0945Count(summary.getForceSell0945Count());
        return result;
    }

    /**
     * 计算平均值。
     */
    private BigDecimal avg(List<BigDecimal> values) {
        List<BigDecimal> validValues = values.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (validValues.isEmpty()) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return sum(validValues).divide(BigDecimal.valueOf(validValues.size()), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算求和值。
     */
    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream()
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 计算比例。
     */
    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO.setScale(SCALE);
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 将置信度字符串转换为可比较等级。
     */
    private int confidenceRank(String confidenceLevel) {
        if (!StringUtils.hasText(confidenceLevel)) {
            return -1;
        }
        String normalized = confidenceLevel.trim().toUpperCase();
        switch (normalized) {
            case "VERY_LOW_CONFIDENCE":
                return 0;
            case "LOW":
            case "LOW_CONFIDENCE":
                return 1;
            case "MEDIUM_LOW_CONFIDENCE":
                return 2;
            case "MEDIUM":
            case "MEDIUM_CONFIDENCE":
                return 3;
            case "HIGH":
            case "HIGH_CONFIDENCE":
                return 4;
            default:
                return -1;
        }
    }

    /**
     * 比较两个 bps 值是否数值相等。
     */
    private boolean sameBps(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return false;
        }
        return left.compareTo(right) == 0;
    }

    /**
     * 生成尾盘快照索引 key。
     */
    private String snapshotKey(LocalDate tradeDate, String stockCode) {
        return tradeDate + "|" + stockCode;
    }

    /**
     * 格式化成本场景 key，避免 25.0000 这类展示。
     */
    private String formatBps(BigDecimal value) {
        if (value == null) {
            return "null";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * 回测计算的内存中间结果。
     */
    private static class CalculationResult {

        /**
         * 有效参与回测的交易日数。
         */
        private int backtestTradeDays;

        /**
         * 单票明细结果。
         */
        private List<BacktestTradeDetail> tradeDetails;

        /**
         * 每日组合汇总结果。
         */
        private List<BacktestDailySummary> dailySummaries;

        /**
         * TopK 汇总结果。
         */
        private List<BacktestTopkSummary> topkSummaries;

        int getBacktestTradeDays() {
            return backtestTradeDays;
        }

        void setBacktestTradeDays(int backtestTradeDays) {
            this.backtestTradeDays = backtestTradeDays;
        }

        List<BacktestTradeDetail> getTradeDetails() {
            return tradeDetails;
        }

        void setTradeDetails(List<BacktestTradeDetail> tradeDetails) {
            this.tradeDetails = tradeDetails;
        }

        List<BacktestDailySummary> getDailySummaries() {
            return dailySummaries;
        }

        void setDailySummaries(List<BacktestDailySummary> dailySummaries) {
            this.dailySummaries = dailySummaries;
        }

        List<BacktestTopkSummary> getTopkSummaries() {
            return topkSummaries;
        }

        void setTopkSummaries(List<BacktestTopkSummary> topkSummaries) {
            this.topkSummaries = topkSummaries;
        }
    }
}
