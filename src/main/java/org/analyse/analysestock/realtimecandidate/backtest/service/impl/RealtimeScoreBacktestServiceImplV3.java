package org.analyse.analysestock.realtimecandidate.backtest.service.impl;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.entity.*;
import org.analyse.analysestock.analysis.mapper.*;
import org.analyse.analysestock.realtimecandidate.backtest.dto.*;
import org.analyse.analysestock.realtimecandidate.backtest.service.RealtimeScoreBacktestServiceV3;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * V3 回测服务实现。
 *
 * <p>核心变化：</p>
 * <ul>
 *   <li>从 realtime_candidate_score_result_v3 加载评分</li>
 *   <li>使用 stock_tail_trade_snapshot_v3 进行买卖模拟</li>
 *   <li>新增随机选股基线、BottomK 基线</li>
 *   <li>复用 V2 的分阶段买卖规则判断逻辑</li>
 * </ul>
 */
@Service
@Slf4j
public class RealtimeScoreBacktestServiceImplV3 implements RealtimeScoreBacktestServiceV3 {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_FINISHED = "FINISHED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STRATEGY_VERSION = "REALTIME_CANDIDATE_EXECUTION_FIT_V3";
    private static final BigDecimal BPS_BASE = new BigDecimal("10000");
    private static final int SCALE = 4;
    private static final int BATCH_SIZE = 500;

    @Autowired
    private BacktestTaskMapper backtestTaskMapper;

    @Autowired
    private BacktestTopkSummaryMapper backtestTopkSummaryMapper;

    @Autowired
    private BacktestDailySummaryMapper backtestDailySummaryMapper;

    @Autowired
    private BacktestTradeDetailMapper backtestTradeDetailMapper;

    @Autowired
    private RealtimeCandidateScoreResultV3Mapper scoreResultV3Mapper;

    @Autowired
    private StockTailTradeSnapshotV3Mapper tailSnapshotV3Mapper;

    @Autowired
    private TradingDateMapper tradingDateMapper;

    @Autowired
    @Qualifier("importExecutor")
    private Executor importExecutor;

    @Override
    public BacktestTaskCreateResponse runBacktest(V3BacktestRequest request) {
        V3BacktestRequest normalized = normalizeRequest(request);
        String taskId = UUID.randomUUID().toString().replace("-", "");

        BacktestTask task = new BacktestTask();
        task.setTaskId(taskId);
        task.setStartDate(normalized.getStartDate());
        task.setEndDate(normalized.getEndDate());
        task.setTopKList(JSON.toJSONString(normalized.getEffectiveTopKList()));
        task.setCostScenarioBpsList(JSON.toJSONString(normalized.getCostScenarioBpsList()));
        task.setStrategyVersion(STRATEGY_VERSION);
        task.setStatus(STATUS_PENDING);
        task.setBacktestTradeDays(0);
        task.setCreatedAt(LocalDateTime.now());
        task.setRequestJson(JSON.toJSONString(normalized));
        backtestTaskMapper.insert(task);

        CompletableFuture.runAsync(() -> executeTask(taskId, normalized), importExecutor);
        return new BacktestTaskCreateResponse(taskId, STATUS_PENDING);
    }

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

    @Override
    public List<BacktestDailySummary> listDaily(String taskId, Integer topK, BigDecimal costBps,
                                                 LocalDate startDate, LocalDate endDate) {
        getRequiredTask(taskId);
        LambdaQueryWrapper<BacktestDailySummary> query = new LambdaQueryWrapper<>();
        query.eq(BacktestDailySummary::getTaskId, taskId);
        if (topK != null) query.eq(BacktestDailySummary::getTopK, topK);
        if (costBps != null) query.eq(BacktestDailySummary::getCostBps, costBps);
        if (startDate != null) query.ge(BacktestDailySummary::getTradeDate, startDate);
        if (endDate != null) query.le(BacktestDailySummary::getTradeDate, endDate);
        query.orderByAsc(BacktestDailySummary::getTradeDate).orderByAsc(BacktestDailySummary::getTopK);
        return backtestDailySummaryMapper.selectList(query);
    }

    @Override
    public List<BacktestTradeDetail> listDetails(String taskId, LocalDate tradeDate, Integer topK,
                                                  BigDecimal costBps, String stockCode) {
        getRequiredTask(taskId);
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<BacktestTradeDetail> query = new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
        query.eq("task_id", taskId);
        if (tradeDate != null) query.eq("trade_date", tradeDate);
        if (topK != null) query.le("rank_no", topK);
        if (costBps != null) query.eq("cost_bps", costBps);
        if (StringUtils.hasText(stockCode)) query.eq("stock_code", stockCode);
        query.orderByAsc("trade_date", "rank_no", "stock_code");
        return backtestTradeDetailMapper.selectList(query);
    }

    // ==================== 任务执行 ====================

    private void executeTask(String taskId, V3BacktestRequest request) {
        markTaskRunning(taskId);
        try {
            backtestTopkSummaryMapper.deleteByTaskId(taskId);
            backtestDailySummaryMapper.deleteByTaskId(taskId);
            backtestTradeDetailMapper.deleteByTaskId(taskId);

            List<RealtimeCandidateScoreResultV3> scores = loadScores(request);
            if (CollectionUtils.isEmpty(scores)) {
                markTaskFinished(taskId, 0);
                return;
            }

            Map<LocalDate, List<RealtimeCandidateScoreResultV3>> scoreMap = scores.stream()
                    .collect(Collectors.groupingBy(RealtimeCandidateScoreResultV3::getTradeDate,
                            LinkedHashMap::new, Collectors.toList()));

            Map<String, StockTailTradeSnapshotV3> snapshotMap = loadTailSnapshotsV3(request);
            Map<LocalDate, LocalDate> nextDateMap = loadNextTradeDates(scoreMap.keySet());

            List<BacktestTradeDetail> allDetails = new ArrayList<>();
            List<BacktestDailySummary> allDailySummaries = new ArrayList<>();
            Set<LocalDate> activeTradeDates = new HashSet<>();
            int maxTopK = Collections.max(request.getEffectiveTopKList());

            // 获取全市场有效股票池（用于随机基线）
            List<String> allValidStockCodes = scores.stream()
                    .map(RealtimeCandidateScoreResultV3::getStockCode)
                    .distinct().collect(Collectors.toList());

            for (Map.Entry<LocalDate, List<RealtimeCandidateScoreResultV3>> entry : scoreMap.entrySet()) {
                LocalDate tradeDate = entry.getKey();
                List<RealtimeCandidateScoreResultV3> sortedScores = sortScores(entry.getValue());
                if (sortedScores.isEmpty()) continue;

                int selectedLimit = Math.min(maxTopK, sortedScores.size());
                List<RealtimeCandidateScoreResultV3> selectedScores = sortedScores.subList(0, selectedLimit);
                LocalDate nextTradeDate = nextDateMap.get(tradeDate);

                for (BigDecimal costScenarioBps : request.getCostScenarioBpsList()) {
                    // TopK 明细
                    List<BacktestTradeDetail> scenarioDetails = buildTradeDetails(
                            taskId, request, selectedScores, nextTradeDate, snapshotMap, costScenarioBps);
                    allDetails.addAll(scenarioDetails);

                    for (Integer topK : request.getEffectiveTopKList()) {
                        List<BacktestTradeDetail> validDetails = scenarioDetails.stream()
                                .filter(d -> d.getRankNo() != null && d.getRankNo() <= topK)
                                .filter(d -> d.getNetReturnBps() != null)
                                .collect(Collectors.toList());
                        if (!validDetails.isEmpty()) {
                            BacktestDailySummary ds = buildDailySummary(taskId, tradeDate, nextTradeDate, topK, costScenarioBps, validDetails);
                            allDailySummaries.add(ds);
                            activeTradeDates.add(tradeDate);
                        }
                    }

                    // 随机基线
                    if (Boolean.TRUE.equals(request.getEnableRandomBaseline())) {
                        for (Integer topK : request.getEffectiveTopKList()) {
                            List<BacktestTradeDetail> randomDetails = generateRandomBaseline(
                                    taskId, request, sortedScores, topK, nextTradeDate,
                                    snapshotMap, costScenarioBps, request.getRandomIterations());
                            allDetails.addAll(randomDetails);

                            List<BacktestTradeDetail> validRandom = randomDetails.stream()
                                    .filter(d -> d.getNetReturnBps() != null)
                                    .collect(Collectors.toList());
                            if (!validRandom.isEmpty()) {
                                BacktestDailySummary ds = buildDailySummary(taskId, tradeDate, nextTradeDate, topK, costScenarioBps, validRandom);
                                allDailySummaries.add(ds);
                            }
                        }
                    }

                    // BottomK 基线
                    if (Boolean.TRUE.equals(request.getEnableBottomKBaseline())) {
                        List<RealtimeCandidateScoreResultV3> bottomScores = new ArrayList<>(sortedScores);
                        Collections.reverse(bottomScores); // 最低分排前面
                        List<RealtimeCandidateScoreResultV3> bottomSelected = bottomScores.subList(0, Math.min(maxTopK, bottomScores.size()));

                        List<BacktestTradeDetail> bottomDetails = buildTradeDetails(
                                taskId, request, bottomSelected, nextTradeDate, snapshotMap, costScenarioBps);
                        allDetails.addAll(bottomDetails);

                        for (Integer topK : request.getEffectiveTopKList()) {
                            List<BacktestTradeDetail> validBottom = bottomDetails.stream()
                                    .filter(d -> d.getRankNo() != null && d.getRankNo() <= topK)
                                    .filter(d -> d.getNetReturnBps() != null)
                                    .collect(Collectors.toList());
                            if (!validBottom.isEmpty()) {
                                BacktestDailySummary ds = buildDailySummary(taskId, tradeDate, nextTradeDate, topK, costScenarioBps, validBottom);
                                allDailySummaries.add(ds);
                            }
                        }
                    }
                }
            }

            // 汇总
            List<BacktestTopkSummary> topkSummaries = buildTopkSummaries(taskId, request, allDailySummaries, allDetails);
            insertDetails(allDetails);
            insertDailySummaries(allDailySummaries);
            insertTopkSummaries(topkSummaries);

            markTaskFinished(taskId, activeTradeDates.size());
            log.info("V3 backtest finished, taskId={}, tradeDays={}", taskId, activeTradeDates.size());
        } catch (Exception e) {
            log.error("V3 backtest failed, taskId={}", taskId, e);
            markTaskFailed(taskId, e);
        }
    }

    // ==================== 随机选股基线 ====================

    private List<BacktestTradeDetail> generateRandomBaseline(
            String taskId, V3BacktestRequest request,
            List<RealtimeCandidateScoreResultV3> sortedScores,
            int topK, LocalDate nextTradeDate,
            Map<String, StockTailTradeSnapshotV3> snapshotMap,
            BigDecimal costScenarioBps, int iterations) {

        List<BacktestTradeDetail> allRandomDetails = new ArrayList<>();
        int poolSize = sortedScores.size();
        if (poolSize <= topK) return allRandomDetails;

        for (int iter = 0; iter < iterations; iter++) {
            // 随机选 topK 只
            List<RealtimeCandidateScoreResultV3> pool = new ArrayList<>(sortedScores);
            Collections.shuffle(pool, ThreadLocalRandom.current());
            List<RealtimeCandidateScoreResultV3> randomSelected = pool.subList(0, topK);

            List<BacktestTradeDetail> details = buildTradeDetails(
                    taskId, request, randomSelected, nextTradeDate, snapshotMap, costScenarioBps);
            // 标记为随机基线
            for (BacktestTradeDetail d : details) {
                d.setRankNo(d.getRankNo() != null ? d.getRankNo() : topK);
            }
            allRandomDetails.addAll(details);
        }
        return allRandomDetails;
    }

    // ==================== 买卖模拟（复用 V2 逻辑） ====================

    private List<BacktestTradeDetail> buildTradeDetails(
            String taskId, V3BacktestRequest request,
            List<RealtimeCandidateScoreResultV3> scores,
            LocalDate nextTradeDate,
            Map<String, StockTailTradeSnapshotV3> snapshotMap,
            BigDecimal costScenarioBps) {

        LocalDateTime now = LocalDateTime.now();
        List<BacktestTradeDetail> details = new ArrayList<>();
        int rank = 0;

        for (RealtimeCandidateScoreResultV3 score : scores) {
            rank++;
            StockTailTradeSnapshotV3 snapshot = snapshotMap.get(snapshotKey(score.getTradeDate(), score.getStockCode()));

            BigDecimal basePrice = score.getPrice1430();

            BacktestTradeDetail detail = new BacktestTradeDetail();
            detail.setTaskId(taskId);
            detail.setTradeDate(score.getTradeDate());
            detail.setNextTradeDate(nextTradeDate);
            detail.setCostBps(costScenarioBps);
            detail.setStockCode(score.getStockCode());
            detail.setShortName(score.getShortName());
            detail.setRankNo(rank);
            detail.setScore(score.getFinalScore());
            detail.setConfidenceLevel(score.getConfidenceLevel());
            detail.setBuyPrice1430(basePrice);
            detail.setCostBpsValue(costScenarioBps);
            detail.setSlippageBps(BigDecimal.ZERO);
            detail.setCreatedAt(now);

            if (basePrice == null || basePrice.compareTo(BigDecimal.ZERO) <= 0) {
                detail.setInvalidReason("NO_BASE_PRICE_1430");
                details.add(detail);
                continue;
            }

            // 模拟分阶段买入
            boolean bought = simulateBuy(detail, basePrice, snapshot);
            if (!bought) {
                detail.setBuyFilled(false);
                detail.setBuyRule("NOT_FILLED");
                details.add(detail);
                continue;
            }
            detail.setBuyFilled(true);

            // 模拟分阶段卖出
            if (nextTradeDate == null) {
                detail.setInvalidReason("NO_NEXT_TRADE_DATE");
                details.add(detail);
                continue;
            }

            StockTailTradeSnapshotV3 tPlus1Snapshot = snapshotMap.get(snapshotKey(nextTradeDate, score.getStockCode()));
            if (tPlus1Snapshot == null) {
                detail.setInvalidReason("NO_T_PLUS_1_SNAPSHOT");
                details.add(detail);
                continue;
            }

            boolean sold = simulateSell(detail, tPlus1Snapshot);
            detail.setSellFilled(sold);

            if (sold) {
                BigDecimal buyPrice = detail.getBuyPrice();
                BigDecimal sellPrice = detail.getSellPrice();
                BigDecimal grossReturnBps = sellPrice.subtract(buyPrice)
                        .divide(buyPrice, 8, RoundingMode.HALF_UP)
                        .multiply(BPS_BASE).setScale(SCALE, RoundingMode.HALF_UP);
                BigDecimal netReturnBps = grossReturnBps.subtract(costScenarioBps).setScale(SCALE, RoundingMode.HALF_UP);
                detail.setGrossReturnBps(grossReturnBps);
                detail.setNetReturnBps(netReturnBps);
                detail.setWinFlag(netReturnBps.compareTo(BigDecimal.ZERO) > 0);
            }

            details.add(detail);
        }
        return details;
    }

    private boolean simulateBuy(BacktestTradeDetail detail, BigDecimal basePrice, StockTailTradeSnapshotV3 snapshot) {
        if (snapshot == null) return false;

        BigDecimal trigger3 = basePrice.multiply(new BigDecimal("0.97"));
        BigDecimal trigger2 = basePrice.multiply(new BigDecimal("0.98"));
        BigDecimal trigger1 = basePrice.multiply(new BigDecimal("0.99"));

        if (snapshot.getLow14351444() != null && snapshot.getLow14351444().compareTo(trigger3) <= 0) {
            detail.setBuyTriggerPrice(trigger3);
            detail.setBuyPrice(trigger3);
            detail.setBuyTime("14:35-14:44");
            detail.setBuyRule("BUY_DROP_3PCT_AFTER_1435");
            return true;
        }
        if (snapshot.getLow14451454() != null && snapshot.getLow14451454().compareTo(trigger2) <= 0) {
            detail.setBuyTriggerPrice(trigger2);
            detail.setBuyPrice(trigger2);
            detail.setBuyTime("14:45-14:54");
            detail.setBuyRule("BUY_DROP_2PCT_AFTER_1445");
            return true;
        }
        if (snapshot.getLow14551500() != null && snapshot.getLow14551500().compareTo(trigger1) <= 0) {
            detail.setBuyTriggerPrice(trigger1);
            detail.setBuyPrice(trigger1);
            detail.setBuyTime("14:55-15:00");
            detail.setBuyRule("BUY_DROP_1PCT_AFTER_1455");
            return true;
        }
        return false;
    }

    private boolean simulateSell(BacktestTradeDetail detail, StockTailTradeSnapshotV3 tPlus1Snapshot) {
        BigDecimal buyPrice = detail.getBuyPrice();
        BigDecimal sellTrigger3 = buyPrice.multiply(new BigDecimal("1.03"));
        BigDecimal sellTrigger2 = buyPrice.multiply(new BigDecimal("1.02"));
        BigDecimal sellTrigger1 = buyPrice.multiply(new BigDecimal("1.01"));

        if (tPlus1Snapshot.getHigh09300935() != null && tPlus1Snapshot.getHigh09300935().compareTo(sellTrigger3) >= 0) {
            detail.setSellTriggerPrice(sellTrigger3);
            detail.setSellPrice(sellTrigger3);
            detail.setSellTime("09:30-09:35");
            detail.setSellRule("SELL_PROFIT_3PCT_BEFORE_0935");
            return true;
        }
        if (tPlus1Snapshot.getHigh09360940() != null && tPlus1Snapshot.getHigh09360940().compareTo(sellTrigger2) >= 0) {
            detail.setSellTriggerPrice(sellTrigger2);
            detail.setSellPrice(sellTrigger2);
            detail.setSellTime("09:36-09:40");
            detail.setSellRule("SELL_PROFIT_2PCT_BEFORE_0940");
            return true;
        }
        if (tPlus1Snapshot.getHigh09410944() != null && tPlus1Snapshot.getHigh09410944().compareTo(sellTrigger1) >= 0) {
            detail.setSellTriggerPrice(sellTrigger1);
            detail.setSellPrice(sellTrigger1);
            detail.setSellTime("09:41-09:44");
            detail.setSellRule("SELL_PROFIT_1PCT_BEFORE_0944");
            return true;
        }
        if (tPlus1Snapshot.getPrice0945() != null) {
            detail.setSellPrice(tPlus1Snapshot.getPrice0945());
            detail.setSellTime("09:45");
            detail.setSellRule("FORCE_SELL_0945");
            return true;
        }
        detail.setInvalidReason("NO_SELL_PRICE_0945");
        return false;
    }

    // ==================== 数据加载 ====================

    private List<RealtimeCandidateScoreResultV3> loadScores(V3BacktestRequest request) {
        LambdaQueryWrapper<RealtimeCandidateScoreResultV3> query = new LambdaQueryWrapper<>();
        query.ge(RealtimeCandidateScoreResultV3::getTradeDate, request.getStartDate())
                .le(RealtimeCandidateScoreResultV3::getTradeDate, request.getEndDate())
                .eq(RealtimeCandidateScoreResultV3::getStrategyVersion, STRATEGY_VERSION);
        if (Boolean.TRUE.equals(request.getExcludeInvalid())) {
            query.eq(RealtimeCandidateScoreResultV3::getValidFlag, true);
        }
        query.orderByAsc(RealtimeCandidateScoreResultV3::getTradeDate)
                .orderByAsc(RealtimeCandidateScoreResultV3::getRankNo)
                .orderByAsc(RealtimeCandidateScoreResultV3::getStockCode);
        return scoreResultV3Mapper.selectList(query);
    }

    private Map<String, StockTailTradeSnapshotV3> loadTailSnapshotsV3(V3BacktestRequest request) {
        LambdaQueryWrapper<StockTailTradeSnapshotV3> query = new LambdaQueryWrapper<>();
        query.ge(StockTailTradeSnapshotV3::getTradeDate, request.getStartDate())
                .le(StockTailTradeSnapshotV3::getTradeDate, request.getEndDate().plusDays(10));
        List<StockTailTradeSnapshotV3> list = tailSnapshotV3Mapper.selectList(query);
        Map<String, StockTailTradeSnapshotV3> map = new HashMap<>();
        for (StockTailTradeSnapshotV3 s : list) {
            map.put(snapshotKey(s.getTradeDate(), s.getStockCode()), s);
        }
        return map;
    }

    private Map<LocalDate, LocalDate> loadNextTradeDates(Set<LocalDate> tradeDates) {
        Map<LocalDate, LocalDate> nextDateMap = new HashMap<>();
        for (LocalDate tradeDate : tradeDates) {
            nextDateMap.put(tradeDate, tradingDateMapper.findTradingDateSqlServerStockCode(tradeDate, 0));
        }
        return nextDateMap;
    }

    // ==================== 汇总 ====================

    private List<BacktestTopkSummary> buildTopkSummaries(String taskId, V3BacktestRequest request,
                                                          List<BacktestDailySummary> dailySummaries,
                                                          List<BacktestTradeDetail> tradeDetails) {
        List<BacktestTopkSummary> summaries = new ArrayList<>();
        for (BigDecimal costBps : request.getCostScenarioBpsList()) {
            for (Integer topK : request.getEffectiveTopKList()) {
                List<BacktestDailySummary> matchedDaily = dailySummaries.stream()
                        .filter(d -> Objects.equals(d.getTopK(), topK))
                        .filter(d -> sameBps(d.getCostBps(), costBps))
                        .collect(Collectors.toList());
                List<BacktestTradeDetail> matchedDetails = tradeDetails.stream()
                        .filter(d -> d.getRankNo() != null && d.getRankNo() <= topK)
                        .filter(d -> sameBps(d.getCostBps(), costBps))
                        .collect(Collectors.toList());
                summaries.add(buildTopkSummary(taskId, topK, costBps, "TOP_K", matchedDaily, matchedDetails));
            }
        }
        return summaries;
    }

    private BacktestTopkSummary buildTopkSummary(String taskId, Integer topK, BigDecimal costBps,
                                                  String baselineType,
                                                  List<BacktestDailySummary> dailySummaries,
                                                  List<BacktestTradeDetail> tradeDetails) {
        BacktestTopkSummary summary = new BacktestTopkSummary();
        summary.setTaskId(taskId);
        summary.setTopK(topK);
        summary.setCostBps(costBps);
        summary.setTradeDays(dailySummaries.size());
        summary.setBaselineType(baselineType);
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

        summary.setAvgNetReturnBps(avg(dailySummaries.stream().map(BacktestDailySummary::getAvgNetReturnBps).collect(Collectors.toList())));
        summary.setDailyWinRate(rate(dailySummaries.stream().filter(d -> Boolean.TRUE.equals(d.getWinFlag())).count(), dailySummaries.size()));
        summary.setTotalReturnBps(sum(dailySummaries.stream().map(BacktestDailySummary::getAvgNetReturnBps).collect(Collectors.toList())));
        summary.setMaxSingleDayLossBps(dailySummaries.stream().map(BacktestDailySummary::getAvgNetReturnBps).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        summary.setAvgSelectedCount(avg(dailySummaries.stream().map(d -> BigDecimal.valueOf(d.getSelectedCount())).collect(Collectors.toList())));

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

        long stockWinCount = tradeDetails.stream().filter(d -> d.getNetReturnBps() != null && d.getNetReturnBps().compareTo(BigDecimal.ZERO) > 0).count();
        long validStockCount = tradeDetails.stream().filter(d -> d.getNetReturnBps() != null).count();
        summary.setStockWinRate(rate(stockWinCount, validStockCount));
        return summary;
    }

    private BacktestDailySummary buildDailySummary(String taskId, LocalDate tradeDate, LocalDate nextTradeDate,
                                                    Integer topK, BigDecimal costBps,
                                                    List<BacktestTradeDetail> validDetails) {
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

        BacktestTradeDetail best = validDetails.stream().max(Comparator.comparing(BacktestTradeDetail::getNetReturnBps)).orElse(null);
        BacktestTradeDetail worst = validDetails.stream().min(Comparator.comparing(BacktestTradeDetail::getNetReturnBps)).orElse(null);
        if (best != null) { summary.setBestStockCode(best.getStockCode()); summary.setBestStockReturnBps(best.getNetReturnBps()); }
        if (worst != null) { summary.setWorstStockCode(worst.getStockCode()); summary.setWorstStockReturnBps(worst.getNetReturnBps()); }
        summary.setCreatedAt(LocalDateTime.now());
        return summary;
    }

    private BacktestSummaryResponse buildSummaryResponse(BacktestTask task, List<BacktestTopkSummary> summaries) {
        BacktestSummaryResponse response = new BacktestSummaryResponse();
        response.setTaskId(task.getTaskId());
        response.setStartDate(task.getStartDate());
        response.setEndDate(task.getEndDate());
        response.setBacktestTradeDays(task.getBacktestTradeDays());
        response.setStrategyVersion(task.getStrategyVersion());

        List<BacktestTopKResult> results = summaries.stream().map(this::toTopKResult).collect(Collectors.toList());
        response.setTopKResults(results);

        Map<String, List<BacktestTopKResult>> costSensitivity = new LinkedHashMap<>();
        for (BacktestTopKResult result : results) {
            String key = formatBps(result.getCostBps());
            costSensitivity.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
        }
        response.setCostSensitivity(costSensitivity);
        return response;
    }

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

    // ==================== 辅助方法 ====================

    private List<RealtimeCandidateScoreResultV3> sortScores(List<RealtimeCandidateScoreResultV3> scores) {
        List<RealtimeCandidateScoreResultV3> sorted = new ArrayList<>(scores);
        sorted.sort((a, b) -> {
            BigDecimal sa = a.getFinalScore() != null ? a.getFinalScore() : BigDecimal.ZERO;
            BigDecimal sb = b.getFinalScore() != null ? b.getFinalScore() : BigDecimal.ZERO;
            int cmp = sb.compareTo(sa);
            if (cmp != 0) return cmp;
            Integer ra = a.getRankNo() != null ? a.getRankNo() : Integer.MAX_VALUE;
            Integer rb = b.getRankNo() != null ? b.getRankNo() : Integer.MAX_VALUE;
            cmp = ra.compareTo(rb);
            if (cmp != 0) return cmp;
            return (a.getStockCode() != null ? a.getStockCode() : "").compareTo(b.getStockCode() != null ? b.getStockCode() : "");
        });
        return sorted;
    }

    private V3BacktestRequest normalizeRequest(V3BacktestRequest request) {
        if (request == null) throw new IllegalArgumentException("request cannot be null");
        if (request.getStartDate() == null || request.getEndDate() == null)
            throw new IllegalArgumentException("startDate and endDate are required");
        if (request.getEndDate().isBefore(request.getStartDate()))
            throw new IllegalArgumentException("endDate cannot be before startDate");

        V3BacktestRequest normalized = new V3BacktestRequest();
        BeanUtils.copyProperties(request, normalized);
        if (CollectionUtils.isEmpty(normalized.getCostScenarioBpsList())) {
            normalized.setCostScenarioBpsList(Arrays.asList(
                    BigDecimal.ZERO, new BigDecimal("10"), new BigDecimal("25"), new BigDecimal("50")));
        }
        if (normalized.getExcludeInvalid() == null) normalized.setExcludeInvalid(true);
        if (normalized.getRandomIterations() == null) normalized.setRandomIterations(100);
        return normalized;
    }

    private void markTaskRunning(String taskId) {
        BacktestTask task = new BacktestTask();
        task.setTaskId(taskId);
        task.setStatus(STATUS_RUNNING);
        task.setStartedAt(LocalDateTime.now());
        backtestTaskMapper.updateById(task);
    }

    private void markTaskFinished(String taskId, int tradeDays) {
        BacktestTask task = getRequiredTask(taskId);
        task.setStatus(STATUS_FINISHED);
        task.setBacktestTradeDays(tradeDays);
        task.setFinishedAt(LocalDateTime.now());
        backtestTaskMapper.updateById(task);
    }

    private void markTaskFailed(String taskId, Exception e) {
        BacktestTask task = new BacktestTask();
        task.setTaskId(taskId);
        task.setStatus(STATUS_FAILED);
        task.setFinishedAt(LocalDateTime.now());
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        task.setErrorMessage(msg.length() > 2000 ? msg.substring(0, 2000) : msg);
        backtestTaskMapper.updateById(task);
    }

    private BacktestTask getRequiredTask(String taskId) {
        BacktestTask task = backtestTaskMapper.selectById(taskId);
        if (task == null) throw new IllegalArgumentException("backtest task not found: " + taskId);
        return task;
    }

    private int resolveProgress(String status) {
        if (STATUS_FINISHED.equals(status) || STATUS_FAILED.equals(status)) return 100;
        if (STATUS_RUNNING.equals(status)) return 50;
        return 0;
    }

    private String snapshotKey(LocalDate tradeDate, String stockCode) {
        return tradeDate + "|" + stockCode;
    }

    private String formatBps(BigDecimal value) {
        return value == null ? "null" : value.stripTrailingZeros().toPlainString();
    }

    private boolean sameBps(BigDecimal a, BigDecimal b) {
        return a != null && b != null && a.compareTo(b) == 0;
    }

    private BigDecimal avg(List<BigDecimal> values) {
        List<BigDecimal> valid = values.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (valid.isEmpty()) return BigDecimal.ZERO.setScale(SCALE);
        return sum(valid).divide(BigDecimal.valueOf(valid.size()), SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal sum(List<BigDecimal> values) {
        return values.stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO.setScale(SCALE);
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), SCALE, RoundingMode.HALF_UP);
    }

    private void insertDetails(List<BacktestTradeDetail> details) {
        if (CollectionUtils.isEmpty(details)) return;
        for (int i = 0; i < details.size(); i += BATCH_SIZE) {
            backtestTradeDetailMapper.insertBatch(details.subList(i, Math.min(i + BATCH_SIZE, details.size())));
        }
    }

    private void insertDailySummaries(List<BacktestDailySummary> summaries) {
        if (CollectionUtils.isEmpty(summaries)) return;
        for (int i = 0; i < summaries.size(); i += BATCH_SIZE) {
            backtestDailySummaryMapper.insertBatch(summaries.subList(i, Math.min(i + BATCH_SIZE, summaries.size())));
        }
    }

    private void insertTopkSummaries(List<BacktestTopkSummary> summaries) {
        if (CollectionUtils.isEmpty(summaries)) return;
        for (int i = 0; i < summaries.size(); i += BATCH_SIZE) {
            backtestTopkSummaryMapper.insertBatch(summaries.subList(i, Math.min(i + BATCH_SIZE, summaries.size())));
        }
    }
}
