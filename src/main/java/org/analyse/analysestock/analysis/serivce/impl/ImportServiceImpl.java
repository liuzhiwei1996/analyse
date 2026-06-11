package org.analyse.analysestock.analysis.serivce.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.entity.*;
import org.analyse.analysestock.analysis.mapper.*;
import org.analyse.analysestock.analysis.serivce.ImportService;
import org.analyse.analysestock.analysis.vo.GenerationMissingDateItem;
import org.analyse.analysestock.analysis.vo.GenerationMissingDateResponse;
import org.analyse.analysestock.analysis.vo.MissingStockDataItem;
import org.analyse.analysestock.analysis.vo.SnapshotTaskProgress;
import org.analyse.analysestock.config.util.RestTemplateUtil;
import org.analyse.analysestock.config.util.StockCodeUtil;
import org.analyse.analysestock.realtimecandidate.calculator.CostCalculator;
import org.analyse.analysestock.realtimecandidate.calculator.ShortSampleCalculator;
import org.analyse.analysestock.realtimecandidate.calculator.v3.DailyTrendCalculatorV3;
import org.analyse.analysestock.realtimecandidate.calculator.v3.SectorMarketCalculatorV3;
import org.analyse.analysestock.realtimecandidate.calculator.v3.ShortSampleCalculatorV3;
import org.analyse.analysestock.realtimecandidate.calculator.v3.V3FilterCalculator;
import org.analyse.analysestock.realtimecandidate.config.CostConfig;
import org.analyse.analysestock.realtimecandidate.config.RealtimeStrategyConfig;
import org.analyse.analysestock.realtimecandidate.dto.RealtimeCandidateScoreRecord;
import org.analyse.analysestock.realtimecandidate.dto.RealtimeFactorSnapshot;
import org.analyse.analysestock.realtimecandidate.dto.V3FactorSnapshot;
import org.analyse.analysestock.realtimecandidate.engine.RealtimeCandidateScoreEngine;
import org.analyse.analysestock.realtimecandidate.engine.RealtimeCandidateScoreEngineV3;
import org.analyse.analysestock.util.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @Author: keenan
 * @Description:
 * @Date: create in 2026/6/4 15:07
 */
@Service
@Slf4j
public class ImportServiceImpl implements ImportService {

    @Autowired
    private PubStockInfoMapper pubStockInfoMapper;

    @Autowired
    private StockMinuteDataMapper stockMinuteDataMapper;

    @Autowired
    private StockDataDailyAllMapper stockDataDailyAllMapper;

    @Autowired
    private TradingDateMapper tradingDateMapper;

    @Autowired
    private StockTailTradeSnapshotMapper stockTailTradeSnapshotMapper;

    @Autowired
    private StockDailyFactorSnapshotMapper stockDailyFactorSnapshotMapper;

    @Autowired
    private MarketContextSnapshotMapper marketContextSnapshotMapper;

    @Autowired
    private SectorContextSnapshotMapper sectorContextSnapshotMapper;

    @Autowired
    private StockShortSampleStatsMapper stockShortSampleStatsMapper;

    @Autowired
    private RealtimeCandidateScoreResultMapper realtimeCandidateScoreResultMapper;

    @Autowired
    private StockIntradayExecutionSnapshotMapper stockIntradayExecutionSnapshotMapper;

    @Autowired
    private RestTemplateUtil restTemplateUtil;

    @Autowired
    @Qualifier("importExecutor")
    private Executor importExecutor;

    private final Map<String, SnapshotTaskProgress> v3SnapshotTaskProgressMap = new ConcurrentHashMap<>();

    private static final String MINUTE_URL = "http://10.0.11.15:5757/BQreal/rds/RDS.do?pkgtype=MinuteKLine&code=%s&min=1&end=%s&count=240";


    @Autowired
    private RealtimeCandidateScoreEngine scoreEngine;

    // ==================== V3 Mapper ====================
    @Autowired
    private RealtimeCandidateScoreResultV3Mapper realtimeCandidateScoreResultV3Mapper;

    @Autowired
    private StockTailTradeSnapshotV3Mapper stockTailTradeSnapshotV3Mapper;

    @Autowired
    private StockShortSampleStatsV3Mapper stockShortSampleStatsV3Mapper;

    @Autowired
    private MarketContextSnapshotV3Mapper marketContextSnapshotV3Mapper;

    @Autowired
    private SectorContextSnapshotV3Mapper sectorContextSnapshotV3Mapper;

    @Value("${constant.url.rds}")
    private String rdsUrl;

    @Override
    public Integer  importStockMinuteData(String code, LocalDate date) {

        List<LocalDate> tradeDates = new ArrayList<>();
        if (date != null) {
            tradeDates.add(date);
        } else {
            tradeDates = tradingDateMapper.findByMinuteTradeDate();
        }
        // 1. 获取所有股票信息
        List<PubStockInfo> stockInfos = pubStockInfoMapper.findByStockCode(code);
        if (CollectionUtils.isEmpty(stockInfos)) {
            log.info("没有找到股票信息");
            return 0;
        }

        if (stockInfos.size() > 1) {
            return importStockMinuteDataParallel(stockInfos, tradeDates);
        }

        int totalImported = 0;
        for (PubStockInfo stockInfo : stockInfos) {
            totalImported += importSingleStockMinuteData(stockInfo, tradeDates);
        }
        return totalImported;
    }

    private int importStockMinuteDataParallel(List<PubStockInfo> stockInfos, List<LocalDate> tradeDates) {
        AtomicInteger totalImported = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = stockInfos.stream()
                .map(stockInfo -> CompletableFuture.runAsync(() -> {
                    totalImported.addAndGet(importSingleStockMinuteData(stockInfo, tradeDates));
                }, importExecutor))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return totalImported.get();
    }

    private int importSingleStockMinuteData(PubStockInfo stockInfo, List<LocalDate> tradeDates) {
        int importedCount = 0;
        String symbol = stockInfo.getSymbol();
        for (LocalDate tradeDate : tradeDates) {
            String dateStr = tradeDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
            try {
                String apiSymbol = StockCodeUtil.addMarketPrefix(symbol);
                String stockCode = apiSymbol.substring(1);
                // 2. 查询该股票当天已有的最大时间
                LambdaQueryWrapper<StockMinuteData> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(StockMinuteData::getStockCode, Integer.valueOf(stockCode))
                        .eq(StockMinuteData::getTradeDate, tradeDate)
                        .orderByDesc(StockMinuteData::getTime)
                        .last("limit 1");
                StockMinuteData lastData = stockMinuteDataMapper.selectOne(queryWrapper);
                int lastTime = lastData == null ? 0 : lastData.getTime();

                // 3. 构造 URL 并请求数据
                String url = String.format(MINUTE_URL, apiSymbol, dateStr);
                String response = restTemplateUtil.getForObject(url, "分时数据导入", 0);
                if (response == null) {
                    continue;
                }

                JSONObject jsonObject = JSON.parseObject(response);
                if (jsonObject == null || !"1".equals(jsonObject.getString("result"))) {
                    continue;
                }

                JSONObject dataObj = jsonObject.getJSONObject("data");
                if (dataObj == null) continue;
                JSONArray list = dataObj.getJSONArray("list");
                if (list == null || list.isEmpty()) continue;

                List<StockMinuteData> toSave = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    JSONArray item = list.getJSONArray(i);
                    // [日期, 时间, 开盘, 最高, 最低, 收盘, 成交量, 成交额]
                    // 对应示例：20160308, 945, 3.9, 3.96, 3.6, 3.7, 597980, 2498541
                    LocalDate rdsDate = LocalDate.parse(item.getString(0), DateTimeFormatter.ofPattern("yyyyMMdd"));
                    if (!rdsDate.equals(tradeDate)) {
                        continue;
                    }
                    int time = item.getInteger(1);
                    if (time <= lastTime) {
                        continue;
                    }
                    StockMinuteData data = new StockMinuteData();
                    data.setStockCode(Integer.valueOf(stockCode));
                    data.setTradeDate(rdsDate);
                    data.setTime(time);
                    data.setPrice(item.getBigDecimal(5)); // 收盘价作为当前分钟价格
                    data.setHighPrice(item.getBigDecimal(3));
                    data.setLowPrice(item.getBigDecimal(4));
                    data.setMinuteVolume(item.getLong(6));
                    data.setMinuteAmount(item.getBigDecimal(7));
                    data.setCreateTime(LocalDateTime.now());

                    toSave.add(data);
                }

                if (!toSave.isEmpty()) {
                    stockMinuteDataMapper.insertBatch(toSave);
                    importedCount += toSave.size();
                    log.info("股票 {} 导入了 {} 条分钟数据", symbol, toSave.size());
                }
            } catch (Exception e) {
                log.error("导入股票 {} 分时数据异常", symbol, e);
            }
        }
        return importedCount;
    }

    @Override
    public Integer importStockDailyData(String stockCode, LocalDate tradeDate) {
        String dateStr = null;
        if (tradeDate != null) {
            dateStr = tradeDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        // 1. 获取所有股票信息
        List<PubStockInfo> stockInfos = pubStockInfoMapper.findByStockCode(stockCode);
        if (CollectionUtils.isEmpty(stockInfos)) {
            log.info("没有找到股票信息");
            return 0;
        }

        if (stockInfos.size() > 1) {
            return importStockDailyDataParallel(stockInfos, dateStr);
        }

        int totalImported = 0;
        for (PubStockInfo stockInfo : stockInfos) {
            totalImported += importSingleStockDailyData(stockInfo, dateStr);
        }
        return totalImported;
    }

    private int importStockDailyDataParallel(List<PubStockInfo> stockInfos, String dateStr) {
        AtomicInteger totalImported = new AtomicInteger(0);
        List<CompletableFuture<Void>> futures = stockInfos.stream()
                .map(stockInfo -> CompletableFuture.runAsync(() -> {
                    totalImported.addAndGet(importSingleStockDailyData(stockInfo, dateStr));
                }, importExecutor))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return totalImported.get();
    }

    private int importSingleStockDailyData(PubStockInfo stockInfo, String dateStr) {
        String symbol = stockInfo.getSymbol();
        return addStockDataDailyAll(symbol, dateStr);
    }

    public int addStockDataDailyAll(String stockCode, String dateStr) {
        //从RDS的hiskline 接口导入
        log.info("stock_data_daily_all(全股) 导入:{},时间:{}", stockCode, dateStr);
        int count = 200;
        String dailyCode = StockCodeUtil.addMarketPrefix(stockCode);
        if (!StringUtils.isEmpty(dateStr)) {
            count = 1;
        }

        List<StockDataDailyAll> stockDataDailies = new ArrayList<>();
        //获取上市日期
        LocalDate ipoDate = pubStockInfoMapper.findIpoDateByStockCode(stockCode);
        LocalDate defaultIpoDate = LocalDate.parse("20250101", DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (ipoDate == null || ipoDate.compareTo(defaultIpoDate) < 0) {
            ipoDate = LocalDate.parse("20250101", DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        String startTime = ipoDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        getRdsReferenceStockCodeAll(dailyCode, startTime, dateStr, stockDataDailies, count, 0);
        //获取前复权数据
        List<StockDataDailyAll> afterList = new ArrayList<>();
        //获取后复权数据
        List<StockDataDailyAll> beforeList = new ArrayList<>();
        getRdsReferenceStockCodeAll(dailyCode, startTime, dateStr, afterList, count, 2);
        getRdsReferenceStockCodeAll(dailyCode, startTime, dateStr, beforeList, count, 1);
        if (!dailyCode.equals(stockCode)) {
            stockDataDailies.forEach(stockDataDailyAll -> {
                stockDataDailyAll.setStockCode(stockCode);
            });
        }
        setClosePrice(afterList, beforeList, stockDataDailies);
        ListUtils.sort(stockDataDailies, true, "tradeDate");
        LocalDate tradeDate = null;
        if (!StringUtils.isEmpty(dateStr)) {
            tradeDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        LocalDate missingDate = null;
        if (tradeDate == null) {
            missingDate = tradingDateMapper.getNewsetTradingDate(LocalDate.now().plusDays(-1));
        } else {
            missingDate = tradeDate;
        }
        Integer x = addDataMissing(stockCode, dateStr, stockDataDailies, missingDate, count);
        //如果插入缺失日期数据获取上一交易日时为null 说明传入日期有误 无需进行后面的步骤
        if (x != null) return x;
        stockDataDailyAllMapper.deleteByStockCodeAndTradeDate(stockCode, tradeDate);
        insertStockDataDailyAll(stockDataDailies);
        return 1;
    }

    private int insertStockDataDailyAll(List<StockDataDailyAll> stockDataDailies) {
        int result = 0;
        if (stockDataDailies.size() > 0) {
            result = stockDataDailyAllMapper.bulkInsert(stockDataDailies);
        }
        return result;
    }

    /**
     * 通用方法，使用泛型和Class参数来兼容不同类型的股票数据实体
     */
    private <T extends AbstractStockDataDaily> void getRdsReferenceStockCodeAllGeneric(String stockCode, String start, String end, List<T> stockDataDailies, int count, int power, Class<T> clazz) {
        DateTimeFormatter yyyyMMdd = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate ipoTime = LocalDate.parse(start, yyyyMMdd);
        LocalDate endTime = null;
        if (CharSequenceUtil.isNotBlank(end) || count == 1) {
            endTime = LocalDate.parse(end, yyyyMMdd);
            if (endTime.compareTo(ipoTime) < 0) {
                return;
            }
        }
        String url = rdsUrl + "?pkgtype=hiskline&code=" + stockCode + "&count=" + count + "&start=" + start + "&end=" + end + "&power=" + power;
        String str = restTemplateUtil.getForObject(url, "日K数据导入", 0);
        if (CharSequenceUtil.isNotBlank(str)) {
            JSONArray jsonArray = JSON.parseObject(str).getJSONObject("data").getJSONArray("day");
            if (!jsonArray.isEmpty()) {
                T stockDataDaily;
                for (int i = 0; i < jsonArray.size(); i++) {
                    try {
                        stockDataDaily = clazz.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        log.error("创建股票数据实例失败: " + clazz.getSimpleName(), e);
                        continue;
                    }

                    JSONArray jsonArray2 = jsonArray.getJSONArray(i);
                    //排除暗盘数据
                    LocalDate tradeDate = LocalDate.parse(jsonArray2.getString(0), yyyyMMdd);
                    if (count == 1) {
                        if (tradeDate.compareTo(endTime) != 0) {
                            continue;
                        }
                    }
                    if (ipoTime.compareTo(tradeDate) > 0) {
                        continue;
                    }
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
                    try {
                        Date date = simpleDateFormat.parse(jsonArray2.getString(0));
                        tradeDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }

                    // 使用抽象基类的通用方法设置值
                    stockDataDaily.setTradeDate(tradeDate);
                    stockDataDaily.setOpen(jsonArray2.getBigDecimal(1));
                    stockDataDaily.setHighest(jsonArray2.getBigDecimal(2));
                    stockDataDaily.setLowest(jsonArray2.getBigDecimal(3));
                    stockDataDaily.setClose(jsonArray2.getBigDecimal(4));
                    stockDataDaily.setAmount(jsonArray2.getBigDecimal(6));
                    stockDataDaily.setVolume(jsonArray2.getLong(5));
                    stockDataDaily.setClosePrevious(jsonArray2.getBigDecimal(7));
                    stockDataDaily.setStockCode(stockCode);

                    stockDataDailies.add(stockDataDaily);
                }
                if (jsonArray.size() > 0 && count != 1) {
                    String dateString = LocalDate.parse(String.valueOf(((JSONArray) jsonArray.get(0)).get(0)), DateTimeFormatter.ofPattern("yyyyMMdd")).minusDays(1).format(DateTimeFormatter.ofPattern("yyyyMMdd"));
                    getRdsReferenceStockCodeAllGeneric(stockCode, start, dateString, stockDataDailies, count, power, clazz);
                }
            } else {
                log.debug("调用Rds接口获取收盘价,未取到数据 {}", url);
            }
        }
    }

    /**
     * stock_data_daily_all - 使用抽象基类的通用方法，兼容StockDataDailyETF和StockDataDailyAll
     *
     * @param stockCode
     * @param start
     * @param end
     * @param stockDataDailies
     * @param count
     */
    public void getRdsReferenceStockCodeAll(String stockCode, String start, String end, List<StockDataDailyAll> stockDataDailies, int count, int power) {
        getRdsReferenceStockCodeAllGeneric(stockCode, start, end, stockDataDailies, count, power, StockDataDailyAll.class);
    }


    private Integer addDataMissing(String stockCode, String dateStr, List<StockDataDailyAll> stockDataDailies, LocalDate tradeDate, int count) {
        //插入缺失日期列表的数据
        for (int j = 1; j < stockDataDailies.size(); j++) {
            //上一个交易日数据
            StockDataDailyAll up = stockDataDailies.get(j - 1);
            //当前交易日数据
            StockDataDailyAll current = stockDataDailies.get(j);
            LocalDate tradeDate2 = current.getTradeDate();
            LocalDate lastDay = tradingDateMapper.findTradingDateSqlServerStockCode(up.getTradeDate(), 0);
            //下一个交易日小于当前交易日期
            if (lastDay.compareTo(tradeDate2) < 0) {
                //上一个交易日无数据
                StockDataDailyAll copyData = new StockDataDailyAll();
                copyData.setStockCode(stockCode);
                copyData.setTradeDate(lastDay);
                copyData.setCloseForead(up.getCloseForead());
                copyData.setCloseBackad(up.getCloseBackad());
                copyData.setClose(up.getClose());
                copyData.setClosePrevious(up.getClosePrevious());
                stockDataDailies.add(copyData);
                //排序
                ListUtils.sort(stockDataDailies, true, "tradeDate");
            }
        }
        if (stockDataDailies.size() > 0) {
            //插入暂停交易日期列表的数据
            StockDataDailyAll lastStockDataDaily = stockDataDailies.get(stockDataDailies.size() - 1);
            LocalDate lastDate = lastStockDataDaily.getTradeDate();
            while (lastDate.compareTo(tradeDate) <= 0 && count == 200) {
                //查询下一个交易日
                lastDate = tradingDateMapper.findTradingDateSqlServerStockCode(lastDate, 0);
                //上一个交易日无数据
                StockDataDailyAll copyData = new StockDataDailyAll();
                copyData.setStockCode(stockCode);
                copyData.setTradeDate(lastDate);
                copyData.setCloseForead(lastStockDataDaily.getCloseForead());
                copyData.setCloseBackad(lastStockDataDaily.getCloseBackad());
                copyData.setClose(lastStockDataDaily.getClose());
                copyData.setClosePrevious(lastStockDataDaily.getClosePrevious());
                stockDataDailies.add(copyData);
            }
        } else if (CharSequenceUtil.isNotBlank(dateStr)) {
            //补充每日增量确缺失交易日
            int tradeDateCount = tradingDateMapper.isTradeDate(tradeDate);
            if (tradeDateCount > 0) {
                //获取上一个交易日数据
                StockDataDailyAll lastStockData = stockDataDailyAllMapper.findByStockCodeAndLtTradeDate(stockCode, tradeDate);
                if (lastStockData == null) {
                    return 0;
                }
                StockDataDailyAll stockDataDaily = new StockDataDailyAll();
                stockDataDaily.setStockCode(stockCode);
                stockDataDaily.setTradeDate(tradeDate);
                stockDataDaily.setClose(lastStockData.getClose());
                stockDataDaily.setCloseForead(lastStockData.getCloseForead());
                stockDataDaily.setCloseBackad(lastStockData.getCloseBackad());
                stockDataDaily.setClosePrevious(lastStockData.getClosePrevious());
                stockDataDailies.add(stockDataDaily);
            }
        }
        return null;
    }

    @Override
    public void prepareTailTradeSnapshot(LocalDate tradeDate) {
        log.info("开始多线程生成 {} 的分钟窗口快照", tradeDate);
        stockTailTradeSnapshotMapper.deleteByTradeDate(tradeDate);
        List<PubStockInfo> stockInfos = pubStockInfoMapper.selectList(null);
        if (CollectionUtils.isEmpty(stockInfos)) return;
        LocalDate nextTradeDate = tradingDateMapper.findTradingDateSqlServerStockCode(tradeDate, 0);

        List<CompletableFuture<StockTailTradeSnapshot>> futures = stockInfos.stream()
                .map(stockInfo -> CompletableFuture.supplyAsync(() -> {
                    String symbol = stockInfo.getSymbol();
                    Integer stockCodeInt;
                    try {
                        stockCodeInt = Integer.parseInt(symbol);
                    } catch (NumberFormatException e) {
                        return null;
                    }

                    LambdaQueryWrapper<StockMinuteData> queryWrapper = new LambdaQueryWrapper<>();
                    queryWrapper.eq(StockMinuteData::getStockCode, stockCodeInt)
                            .eq(StockMinuteData::getTradeDate, tradeDate)
                            .orderByAsc(StockMinuteData::getTime);
                    List<StockMinuteData> minutes = stockMinuteDataMapper.selectList(queryWrapper);

                    if (CollectionUtils.isEmpty(minutes)) return null;

                    StockTailTradeSnapshot snapshot = new StockTailTradeSnapshot();
                    snapshot.setStockCode(symbol);
                    snapshot.setTradeDate(tradeDate);
                    snapshot.setCreatedAt(LocalDateTime.now());
                    snapshot.setUpdatedAt(LocalDateTime.now());

                    BigDecimal amountBefore1430 = BigDecimal.ZERO;
                    long volumeBefore1430 = 0L;
                    BigDecimal highBefore1430 = null;
                    BigDecimal lowBefore1430 = null;
                    BigDecimal tailAmount14001430 = BigDecimal.ZERO;
                    long tailVolume14001430 = 0L;

                    for (StockMinuteData m : minutes) {
                        int time = m.getTime();
                        if (time <= 1430) {
                            amountBefore1430 = amountBefore1430.add(m.getMinuteAmount() != null ? m.getMinuteAmount() : BigDecimal.ZERO);
                            volumeBefore1430 += (m.getMinuteVolume() != null ? m.getMinuteVolume() : 0L);
                            if (highBefore1430 == null || (m.getHighPrice() != null && m.getHighPrice().compareTo(highBefore1430) > 0)) {
                                highBefore1430 = m.getHighPrice();
                            }
                            if (lowBefore1430 == null || (m.getLowPrice() != null && m.getLowPrice().compareTo(lowBefore1430) < 0)) {
                                lowBefore1430 = m.getLowPrice();
                            }
                            if (time == 1400) snapshot.setPrice1400(m.getPrice());
                            if (time == 1430) snapshot.setPrice1430(m.getPrice());
                            if (time > 1400 && time <= 1430) {
                                tailAmount14001430 = tailAmount14001430.add(m.getMinuteAmount() != null ? m.getMinuteAmount() : BigDecimal.ZERO);
                                tailVolume14001430 += (m.getMinuteVolume() != null ? m.getMinuteVolume() : 0L);
                            }
                        }
                    }
                    snapshot.setAmountBefore1430(amountBefore1430);
                    snapshot.setVolumeBefore1430(volumeBefore1430);
                    snapshot.setHighBefore1430(highBefore1430);
                    snapshot.setLowBefore1430(lowBefore1430);
                    snapshot.setTailAmount14001430(tailAmount14001430);
                    snapshot.setTailVolume14001430(tailVolume14001430);
                    fillNextMorningSellVwap(snapshot, stockCodeInt, nextTradeDate);
                    snapshot.setValidFlag(snapshot.getPrice1400() != null && snapshot.getPrice1430() != null);
                    if (!Boolean.TRUE.equals(snapshot.getValidFlag())) {
                        snapshot.setInvalidReason("NO_BUY_PRICE_1430");
                    } else if (snapshot.getSellVwap09300945() == null) {
                        snapshot.setInvalidReason("NO_SELL_VWAP");
                    }
                    return snapshot;
                }, importExecutor))
                .collect(Collectors.toList());

        List<StockTailTradeSnapshot> allSnapshots = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ListUtils.splitListByCount(allSnapshots, 500).forEach(stockTailTradeSnapshotMapper::insertBatch);
        log.info("{} 的分钟窗口快照生成完成，共 {} 条", tradeDate, allSnapshots.size());
    }

    private void fillNextMorningSellVwap(StockTailTradeSnapshot snapshot, Integer stockCodeInt, LocalDate nextTradeDate) {
        if (nextTradeDate == null) {
            return;
        }

        LambdaQueryWrapper<StockMinuteData> sellQuery = new LambdaQueryWrapper<>();
        sellQuery.eq(StockMinuteData::getStockCode, stockCodeInt)
                .eq(StockMinuteData::getTradeDate, nextTradeDate)
                .ge(StockMinuteData::getTime, 930)
                .le(StockMinuteData::getTime, 945)
                .orderByAsc(StockMinuteData::getTime);
        List<StockMinuteData> sellMinutes = stockMinuteDataMapper.selectList(sellQuery);
        if (CollectionUtils.isEmpty(sellMinutes)) {
            return;
        }

        BigDecimal sellAmount = BigDecimal.ZERO;
        long sellVolume = 0L;
        for (StockMinuteData minute : sellMinutes) {
            sellAmount = sellAmount.add(minute.getMinuteAmount() != null ? minute.getMinuteAmount() : BigDecimal.ZERO);
            sellVolume += minute.getMinuteVolume() != null ? minute.getMinuteVolume() : 0L;
        }

        snapshot.setSellAmount09300945(sellAmount);
        snapshot.setSellVolume09300945(sellVolume);
        if (sellVolume > 0 && sellAmount.compareTo(BigDecimal.ZERO) > 0) {
            // RDS 分钟线成交量单位是“手”，VWAP 价格需要换算成每股价格。
            BigDecimal sellShares = BigDecimal.valueOf(sellVolume).multiply(BigDecimal.valueOf(100));
            snapshot.setSellVwap09300945(sellAmount.divide(sellShares, 4, RoundingMode.HALF_UP));
        }
    }

    @Override
    public void prepareDailyFactorSnapshot(LocalDate tradeDate) {
        log.info("开始多线程生成 {} 的日K因子快照", tradeDate);
        stockDailyFactorSnapshotMapper.deleteByTradeDate(tradeDate);
        List<PubStockInfo> stockInfos = pubStockInfoMapper.selectList(null);
        if (CollectionUtils.isEmpty(stockInfos)) return;

        List<CompletableFuture<StockDailyFactorSnapshot>> futures = stockInfos.stream()
                .map(info -> CompletableFuture.supplyAsync(() -> {
                    String stockCode = info.getSymbol();
                    LambdaQueryWrapper<StockDataDailyAll> query = new LambdaQueryWrapper<>();
                    query.eq(StockDataDailyAll::getStockCode, stockCode)
                            .le(StockDataDailyAll::getTradeDate, tradeDate)
                            .orderByDesc(StockDataDailyAll::getTradeDate)
                            .last("limit 65");

                    List<StockDataDailyAll> dailyBars = stockDataDailyAllMapper.selectList(query);
                    if (dailyBars == null || dailyBars.isEmpty()) return null;

                    StockDataDailyAll latest = dailyBars.get(0);
                    if (!latest.getTradeDate().equals(tradeDate)) return null;

                    StockDailyFactorSnapshot snapshot = new StockDailyFactorSnapshot();
                    snapshot.setStockCode(stockCode);
                    snapshot.setTradeDate(tradeDate);
                    snapshot.setClosePrevious(latest.getClosePrevious());

                    if (dailyBars.size() > 1) snapshot.setReturn1d(calculateReturn(latest, dailyBars.get(1)));
                    if (dailyBars.size() > 3) snapshot.setReturn3d(calculateReturn(latest, dailyBars.get(3)));
                    if (dailyBars.size() > 5) snapshot.setReturn5d(calculateReturn(latest, dailyBars.get(5)));
                    if (dailyBars.size() > 10) snapshot.setReturn10d(calculateReturn(latest, dailyBars.get(10)));
                    if (dailyBars.size() > 20) {
                        snapshot.setReturn20d(calculateReturn(latest, dailyBars.get(20)));
                        List<StockDataDailyAll> window20 = dailyBars.subList(0, Math.min(dailyBars.size(), 20));
                        BigDecimal high20 = window20.stream().map(StockDataDailyAll::getHighest).max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                        BigDecimal low20 = window20.stream().map(StockDataDailyAll::getLowest).min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
                        snapshot.setDailyHigh20d(high20);
                        snapshot.setDailyLow20d(low20);
                        if (high20.compareTo(low20) > 0) {
                            snapshot.setPosition20d(latest.getCloseForead().subtract(low20).divide(high20.subtract(low20), 4, RoundingMode.HALF_UP));
                        }
                    }
                    snapshot.setAvgAmount5d(calculateAvgAmount(dailyBars, 5));
                    snapshot.setAvgAmount20d(calculateAvgAmount(dailyBars, 20));
                    snapshot.setAvgAmount60d(calculateAvgAmount(dailyBars, 60));
                    snapshot.setVolatility5d(calculateVolatility(dailyBars, 5));
                    snapshot.setVolatility20d(calculateVolatility(dailyBars, 20));
                    snapshot.setValidFlag(true);
                    snapshot.setCreatedAt(LocalDateTime.now());
                    snapshot.setUpdatedAt(LocalDateTime.now());
                    return snapshot;
                }, importExecutor))
                .collect(Collectors.toList());

        List<StockDailyFactorSnapshot> allSnapshots = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ListUtils.splitListByCount(allSnapshots, 500).forEach(stockDailyFactorSnapshotMapper::insertBatch);
        log.info("{} 的日K因子快照生成完成，共 {} 条", tradeDate, allSnapshots.size());
    }

    private BigDecimal calculateReturn(StockDataDailyAll current, StockDataDailyAll base) {
        if (base.getCloseForead() == null || base.getCloseForead().compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return current.getCloseForead().subtract(base.getCloseForead()).divide(base.getCloseForead(), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateAvgAmount(List<StockDataDailyAll> bars, int days) {
        int limit = Math.min(bars.size(), days);
        if (limit == 0) return BigDecimal.ZERO;
        BigDecimal sum = BigDecimal.ZERO;
        for (int i = 0; i < limit; i++) {
            sum = sum.add(bars.get(i).getAmount());
        }
        return sum.divide(BigDecimal.valueOf(limit), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateVolatility(List<StockDataDailyAll> bars, int days) {
        int limit = Math.min(bars.size(), days);
        if (limit < 2) return BigDecimal.ZERO;
        
        List<BigDecimal> returns = new ArrayList<>();
        for (int i = 0; i < limit - 1; i++) {
            returns.add(calculateReturn(bars.get(i), bars.get(i+1)));
        }
        if (returns.size() < 2) return BigDecimal.ZERO;
        
        BigDecimal avg = returns.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(returns.size()), 8, RoundingMode.HALF_UP);
        BigDecimal variance = returns.stream()
                .map(r -> r.subtract(avg).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(returns.size() - 1), 8, RoundingMode.HALF_UP);
        
        return BigDecimal.valueOf(Math.sqrt(variance.doubleValue())).setScale(4, RoundingMode.HALF_UP);
    }

    @Override
    public void prepareMarketContextSnapshot(LocalDate tradeDate) {
        log.info("开始生成 {} 的市场环境快照", tradeDate);
        marketContextSnapshotMapper.deleteByTradeDate(tradeDate);
        
        LambdaQueryWrapper<StockTailTradeSnapshot> query = new LambdaQueryWrapper<>();
        query.eq(StockTailTradeSnapshot::getTradeDate, tradeDate).eq(StockTailTradeSnapshot::getValidFlag, true);
        List<StockTailTradeSnapshot> tails = stockTailTradeSnapshotMapper.selectList(query);
        
        if (tails.isEmpty()) {
            log.warn("{} 没有有效的个股尾盘快照，跳过市场环境快照生成", tradeDate);
            return;
        }

        MarketContextSnapshot market = new MarketContextSnapshot();
        market.setTradeDate(tradeDate);
        market.setValidStockCount(tails.size());

        // 计算涨跌家数 (以 14:30 价格相对于昨收)
        long upCount = tails.stream().filter(t -> t.getPrice1430().compareTo(t.getPrice1400()) > 0).count(); // 这里逻辑可能需要修正，通常市场广度看全天
        // 修正：假设 StockTailTradeSnapshot 里有全天涨跌幅更好，如果没有，先用 14:30 动量替代示意
        market.setUpStockCount((int) upCount);
        market.setDownStockCount(tails.size() - (int) upCount);
        
        if (tails.size() > 0) {
            market.setMarketBreadth1430(BigDecimal.valueOf(upCount).divide(BigDecimal.valueOf(tails.size()), 4, RoundingMode.HALF_UP));
            
            BigDecimal sumMomentum = tails.stream()
                    .map(t -> t.getPrice1430().divide(t.getPrice1400(), 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            market.setMarketAvgTailMomentum(sumMomentum.divide(BigDecimal.valueOf(tails.size()), 4, RoundingMode.HALF_UP));
        }

        market.setStrongMarketFlag(market.getMarketBreadth1430() != null && market.getMarketBreadth1430().compareTo(new BigDecimal("0.6")) > 0);
        market.setWeakMarketFlag(market.getMarketBreadth1430() != null && market.getMarketBreadth1430().compareTo(new BigDecimal("0.4")) < 0);
        
        market.setCreatedAt(LocalDateTime.now());
        market.setUpdatedAt(LocalDateTime.now());
        
        marketContextSnapshotMapper.insert(market);

        // 批量计算并插入板块环境快照
        prepareSectorContextSnapshots(tradeDate, tails);
    }

    private void prepareSectorContextSnapshots(LocalDate tradeDate, List<StockTailTradeSnapshot> tails) {
        sectorContextSnapshotMapper.deleteByTradeDate(tradeDate);
        Map<String, PubStockInfo> infoMap = pubStockInfoMapper.selectList(null).stream()
                .filter(i -> i.getSector() != null)
                .collect(Collectors.toMap(PubStockInfo::getSymbol, i -> i, (a, b) -> a));

        Map<String, List<StockTailTradeSnapshot>> sectorGroups = tails.stream()
                .filter(t -> infoMap.containsKey(t.getStockCode()))
                .collect(Collectors.groupingBy(t -> infoMap.get(t.getStockCode()).getSector()));

        List<SectorContextSnapshot> sectorSnapshots = new ArrayList<>();
        for (Map.Entry<String, List<StockTailTradeSnapshot>> entry : sectorGroups.entrySet()) {
            String sectorName = entry.getKey();
            List<StockTailTradeSnapshot> sectorTails = entry.getValue();

            SectorContextSnapshot ss = new SectorContextSnapshot();
            ss.setTradeDate(tradeDate);
            ss.setSector(sectorName);
            ss.setValidStockCount(sectorTails.size());

            long upCount = sectorTails.stream().filter(t -> t.getPrice1430().compareTo(t.getPrice1400()) > 0).count();
            ss.setSectorBreadth1430(BigDecimal.valueOf(upCount).divide(BigDecimal.valueOf(sectorTails.size()), 4, RoundingMode.HALF_UP));

            BigDecimal sumMomentum = sectorTails.stream()
                    .map(t -> t.getPrice1430().divide(t.getPrice1400(), 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            ss.setSectorAvgTailMomentum(sumMomentum.divide(BigDecimal.valueOf(sectorTails.size()), 4, RoundingMode.HALF_UP));

            ss.setCreatedAt(LocalDateTime.now());
            ss.setUpdatedAt(LocalDateTime.now());
            sectorSnapshots.add(ss);
        }

        if (!sectorSnapshots.isEmpty()) {
            sectorContextSnapshotMapper.insertBatch(sectorSnapshots);
        }
    }

    @Override
    public void prepareShortSampleStats(LocalDate tradeDate) {
        log.info("开始多线程生成 {} 的短样本统计快照", tradeDate);
        stockShortSampleStatsMapper.deleteByTradeDate(tradeDate);
        List<PubStockInfo> stockInfos = pubStockInfoMapper.selectList(null);
        if (CollectionUtils.isEmpty(stockInfos)) return;

        CostConfig costConfig = new CostConfig();
        BigDecimal roundTripCost = CostCalculator.calculateRoundTripCostBps(costConfig);

        List<CompletableFuture<StockShortSampleStats>> futures = stockInfos.stream()
                .map(info -> CompletableFuture.supplyAsync(() -> {
                    String stockCode = info.getSymbol();
                    ShortSampleCalculator calculator = new ShortSampleCalculator();

                    LambdaQueryWrapper<StockMinuteData> mQuery = new LambdaQueryWrapper<>();
                    mQuery.eq(StockMinuteData::getStockCode, stockCode)
                            .le(StockMinuteData::getTradeDate, tradeDate)
                            .orderByDesc(StockMinuteData::getTradeDate)
                            .last("limit 7200");
                    List<StockMinuteData> minutes = stockMinuteDataMapper.selectList(mQuery);

                    LambdaQueryWrapper<StockDataDailyAll> dQuery = new LambdaQueryWrapper<>();
                    dQuery.eq(StockDataDailyAll::getStockCode, stockCode)
                            .le(StockDataDailyAll::getTradeDate, tradeDate)
                            .orderByDesc(StockDataDailyAll::getTradeDate)
                            .last("limit 40");
                    List<StockDataDailyAll> dailies = stockDataDailyAllMapper.selectList(dQuery);

                    if (minutes.isEmpty() || dailies.isEmpty()) return null;

                    org.analyse.analysestock.realtimecandidate.dto.ShortSampleStats stats = calculator.calculate(stockCode, minutes, dailies, costConfig, 1);
                    if (stats.getShortSampleCount() <= 0) return null;

                    StockShortSampleStats entity = new StockShortSampleStats();
                    entity.setStockCode(stockCode);
                    entity.setTradeDate(tradeDate);
                    entity.setSampleCount(stats.getShortSampleCount());
                    entity.setShortWinRate(stats.getShortWinRate());
                    entity.setAvgNetReturnBps(stats.getShortAvgNetReturnBps());
                    entity.setAvgWinBps(stats.getShortAvgWinBps());
                    entity.setAvgLossBps(stats.getShortAvgLossBps());
                    entity.setProfitLossRatio(stats.getShortProfitLossRatio());
                    entity.setWinCount((int) (stats.getShortSampleCount() * stats.getShortWinRate().doubleValue()));
                    entity.setAvgGrossReturnBps(stats.getShortAvgNetReturnBps().add(roundTripCost));
                    entity.setAvgCostBps(roundTripCost);
                    entity.setCreatedAt(LocalDateTime.now());
                    entity.setUpdatedAt(LocalDateTime.now());
                    return entity;
                }, importExecutor))
                .collect(Collectors.toList());

        List<StockShortSampleStats> allStats = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ListUtils.splitListByCount(allStats, 500).forEach(stockShortSampleStatsMapper::insertBatch);
        log.info("{} 的短样本统计快照生成完成，共 {} 条", tradeDate, allStats.size());
    }


    @Override
    public List<RealtimeCandidateScoreRecord> calculateRealtimeCandidateScores(String stockCode, LocalDate tradeDate) {
        if (tradeDate == null) tradeDate = LocalDate.now();
        if (StringUtils.hasText(stockCode) && "ALL".equalsIgnoreCase(stockCode.trim())) {
            stockCode = null;
        }
        
        // 1. 确保快照已生成
        // 这里只是为了演示，实际应该由定时任务提前跑完。
        // 如果数据量大，这里的同步调用会很慢，但在单票测试时可以接受。
        
        // 2. 加载快照并组装 RealtimeFactorSnapshot
        List<RealtimeFactorSnapshot> snapshots = buildFactorSnapshots(tradeDate, stockCode);
        
        if (snapshots.isEmpty()) {
            log.warn("{} 没有找到快照数据，尝试旧流程或先生成快照", tradeDate);
            // 兜底：如果没快照，也可以调用 calculateForSingleDate(stockCode, tradeDate)
        }

        // 3. 调用评分引擎
        RealtimeStrategyConfig strategyConfig = new RealtimeStrategyConfig();
        CostConfig costConfig = new CostConfig();

        List<RealtimeCandidateScoreRecord> records = scoreEngine.calculateWithSnapshots(tradeDate, snapshots, strategyConfig, costConfig);
        saveRealtimeCandidateScoreResults(tradeDate, stockCode, records);
        return records;
    }

    @Override
    public GenerationMissingDateResponse findMissingGenerationDates(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate cannot be before startDate");
        }

        List<LocalDate> tradeDates = findTradeDates(startDate, endDate);
        Set<LocalDate> tailDates = findExistingDates(stockTailTradeSnapshotMapper, startDate, endDate, StockTailTradeSnapshot::getTradeDate);
        Set<LocalDate> dailyFactorDates = findExistingDates(stockDailyFactorSnapshotMapper, startDate, endDate, StockDailyFactorSnapshot::getTradeDate);
        Set<LocalDate> shortSampleDates = findExistingDates(stockShortSampleStatsMapper, startDate, endDate, StockShortSampleStats::getTradeDate);
        Set<LocalDate> marketDates = findExistingDates(marketContextSnapshotMapper, startDate, endDate, MarketContextSnapshot::getTradeDate);
        Set<LocalDate> sectorDates = findExistingDates(sectorContextSnapshotMapper, startDate, endDate, SectorContextSnapshot::getTradeDate);
        Set<LocalDate> scoreDates = findExistingDates(realtimeCandidateScoreResultMapper, startDate, endDate, RealtimeCandidateScoreResult::getTradeDate);

        List<GenerationMissingDateItem> items = new ArrayList<>();
        List<LocalDate> missingFactorDates = new ArrayList<>();
        List<LocalDate> missingScoreDates = new ArrayList<>();

        for (LocalDate tradeDate : tradeDates) {
            List<String> missingFactorItems = new ArrayList<>();
            addMissingFactorItem(missingFactorItems, tailDates, tradeDate, "尾盘交易快照");
            addMissingFactorItem(missingFactorItems, dailyFactorDates, tradeDate, "日K因子快照");
            addMissingFactorItem(missingFactorItems, shortSampleDates, tradeDate, "短样本统计快照");
            addMissingFactorItem(missingFactorItems, marketDates, tradeDate, "市场环境快照");
            addMissingFactorItem(missingFactorItems, sectorDates, tradeDate, "板块环境快照");

            boolean factorGenerated = missingFactorItems.isEmpty();
            boolean scoreGenerated = scoreDates.contains(tradeDate);
            if (!factorGenerated) {
                missingFactorDates.add(tradeDate);
            }
            if (!scoreGenerated) {
                missingScoreDates.add(tradeDate);
            }

            GenerationMissingDateItem item = new GenerationMissingDateItem();
            item.setTradeDate(tradeDate);
            item.setFactorGenerated(factorGenerated);
            item.setScoreGenerated(scoreGenerated);
            item.setMissingFactorItems(missingFactorItems);
            items.add(item);
        }

        GenerationMissingDateResponse response = new GenerationMissingDateResponse();
        response.setStartDate(startDate);
        response.setEndDate(endDate);
        response.setTradeDates(tradeDates);
        response.setTradeDayCount(tradeDates.size());
        response.setMissingFactorDates(missingFactorDates);
        response.setMissingScoreDates(missingScoreDates);
        response.setMissingFactorCount(missingFactorDates.size());
        response.setMissingScoreCount(missingScoreDates.size());
        response.setItems(items);
        return response;
    }

    private List<LocalDate> findTradeDates(LocalDate startDate, LocalDate endDate) {
        QueryWrapper<TradingDate> query = new QueryWrapper<>();
        query.select("DISTINCT trading_date")
                .ge("trading_date", startDate)
                .le("trading_date", endDate)
                .orderByAsc("trading_date");
        return tradingDateMapper.selectList(query).stream()
                .map(TradingDate::getTradingDate)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private <T> Set<LocalDate> findExistingDates(
            BaseMapper<T> mapper,
            LocalDate startDate,
            LocalDate endDate,
            Function<T, LocalDate> tradeDateGetter
    ) {
        QueryWrapper<T> query = new QueryWrapper<>();
        query.select("trade_date")
                .ge("trade_date", startDate)
                .le("trade_date", endDate)
                .groupBy("trade_date");
        return mapper.selectList(query).stream()
                .map(tradeDateGetter)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private void addMissingFactorItem(List<String> missingItems, Set<LocalDate> generatedDates, LocalDate tradeDate, String itemName) {
        if (!generatedDates.contains(tradeDate)) {
            missingItems.add(itemName);
        }
    }

    private void saveRealtimeCandidateScoreResults(LocalDate tradeDate, String stockCode, List<RealtimeCandidateScoreRecord> records) {
        if (StringUtils.hasText(stockCode)) {
            log.info("{} 单票评分查询不落库，stockCode={}", tradeDate, stockCode);
            return;
        }
        if (CollectionUtils.isEmpty(records)) {
            log.warn("{} 全市场评分结果为空，跳过 realtime_candidate_score_result 落库", tradeDate);
            return;
        }

        String strategyVersion = records.stream()
                .map(RealtimeCandidateScoreRecord::getStrategyVersion)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("V1");

        List<RealtimeCandidateScoreResult> entities = records.stream()
                .map(record -> {
                    RealtimeCandidateScoreResult entity = new RealtimeCandidateScoreResult();
                    entity.setTradeDate(tradeDate);
                    entity.setStockCode(record.getStockCode());
                    entity.setShortName(record.getShortName());
                    entity.setPrice1430(record.getPrice1430());
                    entity.setFinalScore(record.getFinalScore());
                    entity.setRankNo(record.getRankNo());
                    entity.setConfidenceLevel(record.getConfidenceLevel() == null ? null : record.getConfidenceLevel().name());
                    entity.setValidFlag(record.isValidFlag());
                    entity.setStrategyVersion(StringUtils.hasText(record.getStrategyVersion()) ? record.getStrategyVersion() : strategyVersion);
                    entity.setCreatedAt(record.getCreatedAt() == null ? LocalDateTime.now() : record.getCreatedAt());
                    return entity;
                })
                .collect(Collectors.toList());

        realtimeCandidateScoreResultMapper.deleteByTradeDateAndStrategyVersion(tradeDate, strategyVersion);
        ListUtils.splitListByCount(entities, 500).forEach(realtimeCandidateScoreResultMapper::insertBatch);
        log.info("{} 全市场评分结果已落库，strategyVersion={}, count={}", tradeDate, strategyVersion, entities.size());
    }

    private List<RealtimeFactorSnapshot> buildFactorSnapshots(LocalDate tradeDate, String stockCode) {
        // 查询各个快照表并合并
        LambdaQueryWrapper<StockTailTradeSnapshot> tailWrapper = new LambdaQueryWrapper<>();
        tailWrapper.eq(StockTailTradeSnapshot::getTradeDate, tradeDate);
        if (stockCode != null) tailWrapper.eq(StockTailTradeSnapshot::getStockCode, stockCode);
        List<StockTailTradeSnapshot> tails = stockTailTradeSnapshotMapper.selectList(tailWrapper);

        List<RealtimeFactorSnapshot> results = new ArrayList<>();
        Map<String, PubStockInfo> infoMap = pubStockInfoMapper.selectList(null).stream()
                .collect(Collectors.toMap(PubStockInfo::getSymbol, i -> i, (a, b) -> a));

        for (StockTailTradeSnapshot t : tails) {
            RealtimeFactorSnapshot s = new RealtimeFactorSnapshot();
            s.setTradeDate(tradeDate);
            s.setStockCode(t.getStockCode());
            PubStockInfo info = infoMap.get(t.getStockCode());
            if (info != null) {
                s.setShortName(info.getShortName());
                s.setMarket(info.getMarkets());
                s.setSector(info.getSector());
            }
            s.setPrice1400(t.getPrice1400());
            s.setPrice1430(t.getPrice1430());

            // 计算动量
            if (t.getPrice1400() != null && t.getPrice1400().compareTo(java.math.BigDecimal.ZERO) > 0) {
                s.setTailMomentum(t.getPrice1430().divide(t.getPrice1400(), 4, java.math.RoundingMode.HALF_UP).subtract(java.math.BigDecimal.ONE));
            }
            s.setTailAmount14001430(t.getTailAmount14001430());
            if (t.getAmountBefore1430() != null && t.getAmountBefore1430().compareTo(BigDecimal.ZERO) > 0) {
                s.setTailVolumeRatio(t.getTailAmount14001430().divide(t.getAmountBefore1430(), 4, RoundingMode.HALF_UP));
            }

            // 填充其他快照字段 (DailyFactor, ShortSampleStats 等)
            LambdaQueryWrapper<StockDailyFactorSnapshot> dailyWrapper = new LambdaQueryWrapper<>();
            dailyWrapper.eq(StockDailyFactorSnapshot::getStockCode, t.getStockCode())
                    .eq(StockDailyFactorSnapshot::getTradeDate, tradeDate);
            StockDailyFactorSnapshot df = stockDailyFactorSnapshotMapper.selectOne(dailyWrapper);
            if (df != null) {
                s.setReturn5d(df.getReturn5d());
                s.setReturn20d(df.getReturn20d());
                s.setVolatility20d(df.getVolatility20d());
                s.setAvgAmount20d(df.getAvgAmount20d());
                s.setIntradayPosition(df.getPosition20d());
            }

            LambdaQueryWrapper<MarketContextSnapshot> marketWrapper = new LambdaQueryWrapper<>();
            marketWrapper.eq(MarketContextSnapshot::getTradeDate, tradeDate);
            MarketContextSnapshot mc = marketContextSnapshotMapper.selectOne(marketWrapper);
            if (mc != null) {
                s.setMarketBreadth1430(mc.getMarketBreadth1430());
                s.setMarketReturn1430(mc.getMarketReturn1430());
                s.setRegimeScore(mc.getRegimeScore());
            }

            LambdaQueryWrapper<StockShortSampleStats> shortWrapper = new LambdaQueryWrapper<>();
            shortWrapper.eq(StockShortSampleStats::getStockCode, t.getStockCode())
                    .eq(StockShortSampleStats::getTradeDate, tradeDate);
            StockShortSampleStats ss = stockShortSampleStatsMapper.selectOne(shortWrapper);
            if (ss != null) {
                s.setShortSampleCount(ss.getSampleCount());
                s.setShortWinRate(ss.getShortWinRate());
                s.setShortAvgNetReturnBps(ss.getAvgNetReturnBps());
            }

            results.add(s);
        }
        return results;
    }



    @Override
    public void prepareIntradayExecutionSnapshot(LocalDate tradeDate) {
        log.info("开始准备日期 {} 的盘中执行快照数据", tradeDate);
        stockIntradayExecutionSnapshotMapper.deleteByTradeDate(tradeDate);

        // 1. 获取当天的所有股票分钟数据
        LambdaQueryWrapper<StockMinuteData> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(StockMinuteData::getTradeDate, tradeDate);
        List<StockMinuteData> allMinuteData = stockMinuteDataMapper.selectList(queryWrapper);
        if (CollectionUtils.isEmpty(allMinuteData)) {
            log.warn("日期 {} 没有分钟数据", tradeDate);
            return;
        }

        // 按股票分组
        Map<Integer, List<StockMinuteData>> stockGroup = allMinuteData.stream()
                .collect(Collectors.groupingBy(StockMinuteData::getStockCode));

        List<StockIntradayExecutionSnapshot> snapshots = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<Integer, List<StockMinuteData>> entry : stockGroup.entrySet()) {
            Integer stockCodeInt = entry.getKey();
            String stockCode = String.format("%06d", stockCodeInt);
            List<StockMinuteData> minuteList = entry.getValue();

            StockIntradayExecutionSnapshot snapshot = new StockIntradayExecutionSnapshot();
            snapshot.setStockCode(stockCode);
            snapshot.setTradeDate(tradeDate);
            snapshot.setValidFlag(true);
            snapshot.setCreatedAt(now);

            // T 日买入窗口判断
            // 14:30 价格
            minuteList.stream().filter(m -> m.getTime() == 1430).findFirst()
                    .ifPresent(m -> snapshot.setPrice1430(m.getPrice()));

            // 14:35-14:44 最低价
            snapshot.setLow14351444(getMinLowPrice(minuteList, 1435, 1444));
            // 14:45-14:54 最低价
            snapshot.setLow14451454(getMinLowPrice(minuteList, 1445, 1454));
            // 14:55-15:00 最低价
            snapshot.setLow14551500(getMinLowPrice(minuteList, 1455, 1500));

            // T+1 日卖出窗口判断 (注意：此方法是为 tradeDate 准备数据的)
            // 09:30-09:35 最高价
            snapshot.setHigh09300935(getMaxHighPrice(minuteList, 930, 935));
            // 09:36-09:40 最高价
            snapshot.setHigh09360940(getMaxHighPrice(minuteList, 936, 940));
            // 09:41-09:44 最高价
            snapshot.setHigh09410944(getMaxHighPrice(minuteList, 941, 944));
            // 09:45 价格
            minuteList.stream().filter(m -> m.getTime() == 945).findFirst()
                    .ifPresent(m -> snapshot.setPrice0945(m.getPrice()));

            snapshots.add(snapshot);
        }

        if (!snapshots.isEmpty()) {
            ListUtils.splitListByCount(snapshots, 1000).forEach(stockIntradayExecutionSnapshotMapper::insertBatch);
            log.info("日期 {} 的盘中执行快照数据准备完成，共 {} 条", tradeDate, snapshots.size());
        }
    }

    private BigDecimal getMinLowPrice(List<StockMinuteData> minuteList, int startTime, int endTime) {
        return minuteList.stream()
                .filter(m -> m.getTime() >= startTime && m.getTime() <= endTime)
                .map(StockMinuteData::getLowPrice)
                .filter(Objects::nonNull)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }

    private BigDecimal getMaxHighPrice(List<StockMinuteData> minuteList, int startTime, int endTime) {
        return minuteList.stream()
                .filter(m -> m.getTime() >= startTime && m.getTime() <= endTime)
                .map(StockMinuteData::getHighPrice)
                .filter(Objects::nonNull)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }


    @Override
    public List<MissingStockDataItem> checkMissingData(String stockCode, LocalDate startDate, LocalDate endDate) {
        List<MissingStockDataItem> result = new ArrayList<>();

        // 1. 查询该日期范围内的所有交易日
        LambdaQueryWrapper<TradingDate> dateWrapper = new LambdaQueryWrapper<>();
        dateWrapper.select(TradingDate::getTradingDate)
                .ge(TradingDate::getTradingDate, startDate)
                .le(TradingDate::getTradingDate, endDate)
                .eq(TradingDate::getIsDeleted, 0)
                .orderByAsc(TradingDate::getTradingDate);
        List<TradingDate> tradingDates = tradingDateMapper.selectList(dateWrapper);
        List<LocalDate> allTradeDates = tradingDates.stream()
                .map(TradingDate::getTradingDate)
                .collect(Collectors.toList());

        if (allTradeDates.isEmpty()) {
            log.warn("日期范围 {} ~ {} 内无交易日", startDate, endDate);
            return result;
        }

        // 2. 查询股票列表
        List<PubStockInfo> allStocks;
        if (CharSequenceUtil.isNotBlank(stockCode)) {
            allStocks = pubStockInfoMapper.findByStockCode(stockCode);
        } else {
            allStocks = pubStockInfoMapper.selectList(null);
        }

        if (allStocks.isEmpty()) {
            log.warn("未找到任何股票信息");
            return result;
        }

        log.info("开始检测 {} 只股票在 {} ~ {} 范围内的数据缺失情况", allStocks.size(), startDate, endDate);

        // 3. 查询已有日K和分时数据的股票代码集合（按日期分组）
        Set<String> stocksWithDaily = new HashSet<>(stockDataDailyAllMapper.findStockCodesWithDailyData(startDate, endDate));
        Set<String> stocksWithMinute = new HashSet<>(stockMinuteDataMapper.findStockCodesWithMinuteData(startDate, endDate));

        // 4. 逐只股票检查
        for (PubStockInfo stock : allStocks) {
            String code = stock.getSymbol();

            // 跳过已退市的股票
            if (stock.getDelistDate() != null && stock.getDelistDate().isBefore(startDate)) {
                continue;
            }

            // 跳过未上市的股票
            if (stock.getListingDate() != null && stock.getListingDate().isAfter(endDate)) {
                continue;
            }

            // 确定该股票实际应包含的交易日范围
            LocalDate effectiveStart = startDate;
            LocalDate effectiveEnd = endDate;
            if (stock.getListingDate() != null && stock.getListingDate().isAfter(effectiveStart)) {
                effectiveStart = stock.getListingDate();
            }
            if (stock.getDelistDate() != null && stock.getDelistDate().isBefore(effectiveEnd)) {
                effectiveEnd = stock.getDelistDate();
            }

            // 获取该股票实际已有的日K和分时数据日期
            Set<LocalDate> existingDailyDates = new HashSet<>(
                    stockDataDailyAllMapper.findTradeDatesWithDailyData(code, effectiveStart, effectiveEnd));
            Set<LocalDate> existingMinuteDates = new HashSet<>(
                    stockMinuteDataMapper.findTradeDatesWithMinuteData(code, effectiveStart, effectiveEnd));

            // 对每个交易日检查
            for (LocalDate tradeDate : allTradeDates) {
                if (tradeDate.isBefore(effectiveStart) || tradeDate.isAfter(effectiveEnd)) {
                    continue;
                }

                boolean hasDaily = existingDailyDates.contains(tradeDate);
                boolean hasMinute = existingMinuteDates.contains(tradeDate);

                if (!hasDaily || !hasMinute) {
                    MissingStockDataItem item = new MissingStockDataItem();
                    item.setStockCode(code);
                    item.setStockName(stock.getShortName());
                    item.setTradeDate(tradeDate);
                    item.setMissingDaily(!hasDaily);
                    item.setMissingMinute(!hasMinute);
                    result.add(item);
                }
            }
        }

        log.info("数据缺失检测完成，共发现 {} 条缺失记录", result.size());
        return result;
    }

    private void setClosePrice( List<StockDataDailyAll> afterList, List<StockDataDailyAll> beforeList, List<StockDataDailyAll> stockDataDailies) {
        Map<LocalDate, BigDecimal> afterMap = afterList.stream().collect(Collectors.toMap(item -> item.getTradeDate(), item -> item.getClose()));
        Map<LocalDate, BigDecimal> beforeMap = beforeList.stream().collect(Collectors.toMap(item -> item.getTradeDate(), item -> item.getClose()));
        //填充复权前复权后数据
        ListUtils.sort(stockDataDailies, true, "tradeDate");

        for (StockDataDailyAll stockDataDaily : stockDataDailies) {
            BigDecimal afterClose = afterMap.get(stockDataDaily.getTradeDate());
            BigDecimal beforeClose = beforeMap.get(stockDataDaily.getTradeDate());
            stockDataDaily.setCloseForead((beforeClose == null || beforeClose.compareTo(BigDecimal.ZERO) == 0 ? stockDataDaily.getClose() : beforeClose));
            stockDataDaily.setCloseBackad(afterClose);

        }
    }

    // ==================== V3 方法 ====================

    /**
     * 计算 V3 实时候选股评分。
     */
    @Override
    public java.util.List<RealtimeCandidateScoreResultV3> calculateRealtimeCandidateScoresV3(String stockCode, LocalDate tradeDate) {
        if (tradeDate == null) tradeDate = LocalDate.now();
        if (StringUtils.hasText(stockCode) && "ALL".equalsIgnoreCase(stockCode.trim())) {
            stockCode = null;
        }
        if (StringUtils.hasText(stockCode)) {
            stockCode = normalizeStockCode(stockCode);
        }

        RealtimeCandidateScoreEngineV3 v3Engine = new RealtimeCandidateScoreEngineV3();
        RealtimeStrategyConfig strategyConfig = buildStrategyConfig();
        CostConfig costConfig = buildCostConfig();

        List<V3FactorSnapshot> snapshots = buildV3FactorSnapshots(tradeDate, stockCode, strategyConfig);
        if (CollectionUtils.isEmpty(snapshots)) {
            log.warn("V3: no factor snapshots found, date={}, stockCode={}", tradeDate, stockCode);
            return Collections.emptyList();
        }

        List<RealtimeCandidateScoreResultV3> records = v3Engine.calculateWithSnapshots(
                tradeDate, snapshots, strategyConfig, costConfig);
        saveRealtimeCandidateScoreResultsV3(tradeDate, stockCode, records);
        return records;
    }

    @Override
    public List<V3FactorSnapshot> listRealtimeCandidateFactorSnapshotsV3(String stockCode, LocalDate tradeDate) {
        if (tradeDate == null) tradeDate = LocalDate.now();
        if (StringUtils.hasText(stockCode) && "ALL".equalsIgnoreCase(stockCode.trim())) {
            stockCode = null;
        }
        if (StringUtils.hasText(stockCode)) {
            stockCode = normalizeStockCode(stockCode);
        }

        return buildV3FactorSnapshots(tradeDate, stockCode, buildStrategyConfig());
    }

    @Override
    public SnapshotTaskProgress startPrepareSnapshotsV3(LocalDate tradeDate) {
        if (tradeDate == null) tradeDate = LocalDate.now();
        LocalDate finalTradeDate = tradeDate;

        SnapshotTaskProgress runningTask = findRunningV3SnapshotTask(finalTradeDate);
        if (runningTask != null) {
            return runningTask;
        }

        String taskId = "V3-SNAPSHOT-" + finalTradeDate + "-" + System.currentTimeMillis();
        SnapshotTaskProgress progress = SnapshotTaskProgress.create(taskId, finalTradeDate);
        v3SnapshotTaskProgressMap.put(taskId, progress);

        CompletableFuture.runAsync(() -> {
            try {
                prepareTailTradeSnapshotV3(finalTradeDate, progress);
                prepareShortSampleStatsV3(finalTradeDate, progress);
                progress.startStage("MARKET_CONTEXT", 1, "正在生成 V3 市场和板块环境快照");
                prepareMarketContextSnapshotV3(finalTradeDate);
                progress.update(1, 1, "V3 市场和板块环境快照生成完成");
                progress.complete("V3 快照生成完成");
            } catch (Exception e) {
                log.error("V3: async snapshot task failed, date={}, taskId={}", finalTradeDate, taskId, e);
                progress.fail(e);
            }
        }, importExecutor);

        return progress;
    }

    @Override
    public SnapshotTaskProgress getPrepareSnapshotsProgressV3(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return SnapshotTaskProgress.notFound(taskId);
        }
        return v3SnapshotTaskProgressMap.getOrDefault(taskId, SnapshotTaskProgress.notFound(taskId));
    }

    private SnapshotTaskProgress findRunningV3SnapshotTask(LocalDate tradeDate) {
        return v3SnapshotTaskProgressMap.values().stream()
                .filter(progress -> tradeDate.equals(progress.getTradeDate()))
                .filter(progress -> "RUNNING".equals(progress.getStatus()))
                .findFirst()
                .orElse(null);
    }


    /**
     * 准备 V3 增强尾盘快照。
     */
    @Override
    public void prepareTailTradeSnapshotV3(LocalDate tradeDate) {
        prepareTailTradeSnapshotV3(tradeDate, null);
    }

    private void prepareTailTradeSnapshotV3(LocalDate tradeDate, SnapshotTaskProgress progress) {
        if (tradeDate == null) tradeDate = LocalDate.now();
        log.info("V3: prepare tail snapshots, date={}", tradeDate);
        stockTailTradeSnapshotV3Mapper.deleteByTradeDate(tradeDate);

        List<PubStockInfo> stockInfos = pubStockInfoMapper.selectList(null);
        if (CollectionUtils.isEmpty(stockInfos)) return;
        if (progress != null) {
            progress.startStage("TAIL_SNAPSHOT", stockInfos.size(), "正在生成 V3 尾盘快照");
        }

        LocalDate finalTradeDate = tradeDate;
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger generated = new AtomicInteger(0);
        List<CompletableFuture<StockTailTradeSnapshotV3>> futures = stockInfos.stream()
                .map(info -> CompletableFuture.supplyAsync(() -> {
                    String stockCode = info.getSymbol();
                    List<StockMinuteData> stockMinutes = loadMinuteBars(stockCode, finalTradeDate, finalTradeDate);
                    int currentProcessed = processed.incrementAndGet();
                    if (CollectionUtils.isEmpty(stockMinutes)) {
                        if (progress != null) {
                            progress.update(currentProcessed, generated.get(), null);
                        }
                        if (currentProcessed % 500 == 0) {
                            log.info("V3: tail snapshot progress, date={}, processed={}, generated={}",
                                    finalTradeDate, currentProcessed, generated.get());
                        }
                        return null;
                    }

                    StockTailTradeSnapshotV3 snapshot = buildTailTradeSnapshotV3(stockCode, finalTradeDate, stockMinutes);
                    int currentGenerated = generated.incrementAndGet();
                    if (progress != null) {
                        progress.update(currentProcessed, currentGenerated, null);
                    }
                    if (currentProcessed % 500 == 0) {
                        log.info("V3: tail snapshot progress, date={}, processed={}, generated={}",
                                finalTradeDate, currentProcessed, currentGenerated);
                    }
                    return snapshot;
                }, importExecutor))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<StockTailTradeSnapshotV3> snapshots = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ListUtils.splitListByCount(snapshots, 500).forEach(stockTailTradeSnapshotV3Mapper::insertBatch);
        if (progress != null) {
            progress.update(stockInfos.size(), snapshots.size(), "V3 尾盘快照生成完成");
        }
        log.info("V3: tail snapshots prepared, date={}, count={}", tradeDate, snapshots.size());
    }


    /**
     * 准备 V3 短样本统计。
     */
    @Override
    public void prepareShortSampleStatsV3(LocalDate tradeDate) {
        prepareShortSampleStatsV3(tradeDate, null);
    }

    private void prepareShortSampleStatsV3(LocalDate tradeDate, SnapshotTaskProgress progress) {
        if (tradeDate == null) tradeDate = LocalDate.now();
        log.info("V3: prepare short sample stats, date={}", tradeDate);
        stockShortSampleStatsV3Mapper.deleteByTradeDate(tradeDate);

        RealtimeStrategyConfig strategyConfig = buildStrategyConfig();
        CostConfig costConfig = buildCostConfig();
        LocalDate startDate = tradeDate.minusDays(strategyConfig.getMinuteLookbackDays() + 30);

        List<PubStockInfo> stockInfos = pubStockInfoMapper.selectList(null);
        if (CollectionUtils.isEmpty(stockInfos)) return;
        if (progress != null) {
            progress.startStage("SHORT_SAMPLE", stockInfos.size(), "正在生成 V3 短样本统计");
        }

        Map<String, List<StockTailTradeSnapshotV3>> tailMap = loadTailSnapshotHistoryV3(startDate, tradeDate);

        LocalDate finalTradeDate = tradeDate;
        LocalDate finalStartDate = startDate;
        CostConfig finalCostConfig = costConfig;
        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger generated = new AtomicInteger(0);
        List<CompletableFuture<StockShortSampleStatsV3>> futures = stockInfos.stream()
                .map(info -> CompletableFuture.supplyAsync(() -> {
                    String stockCode = info.getSymbol();
                    String normalizedCode = normalizeStockCode(stockCode);
                    V3FactorSnapshot snapshot = new V3FactorSnapshot();
                    ShortSampleCalculatorV3 calculator = new ShortSampleCalculatorV3();

                    List<StockTailTradeSnapshotV3> tailSnapshots = tailMap.getOrDefault(normalizedCode, Collections.emptyList());
                    if (!tailSnapshots.isEmpty()) {
                        calculator.calculateFromSnapshots(snapshot, tailSnapshots, finalCostConfig, finalTradeDate);
                    } else {
                        List<StockMinuteData> stockMinutes = loadMinuteBars(stockCode, finalStartDate, finalTradeDate.minusDays(1));
                        calculator.calculateForStockMinutes(snapshot, stockMinutes, finalCostConfig, finalTradeDate);
                    }

                    int currentProcessed = processed.incrementAndGet();
                    if (snapshot.getShortSampleCount() == null || snapshot.getShortSampleCount() <= 0) {
                        if (progress != null) {
                            progress.update(currentProcessed, generated.get(), null);
                        }
                        if (currentProcessed % 500 == 0) {
                            log.info("V3: short sample progress, date={}, processed={}, generated={}",
                                    finalTradeDate, currentProcessed, generated.get());
                        }
                        return null;
                    }

                    StockShortSampleStatsV3 stats = buildShortSampleStatsV3(stockCode, finalTradeDate, snapshot);
                    int currentGenerated = generated.incrementAndGet();
                    if (progress != null) {
                        progress.update(currentProcessed, currentGenerated, null);
                    }
                    if (currentProcessed % 500 == 0) {
                        log.info("V3: short sample progress, date={}, processed={}, generated={}",
                                finalTradeDate, currentProcessed, currentGenerated);
                    }
                    return stats;
                }, importExecutor))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        List<StockShortSampleStatsV3> statsList = futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        ListUtils.splitListByCount(statsList, 500).forEach(stockShortSampleStatsV3Mapper::insertBatch);
        if (progress != null) {
            progress.update(stockInfos.size(), statsList.size(), "V3 短样本统计生成完成");
        }
        log.info("V3: short sample stats prepared, date={}, count={}", tradeDate, statsList.size());
    }


    /**
     * 准备 V3 市场环境快照。
     */
    @Override
    public void prepareMarketContextSnapshotV3(LocalDate tradeDate) {
        if (tradeDate == null) tradeDate = LocalDate.now();
        log.info("V3: prepare market context snapshots, date={}", tradeDate);
        marketContextSnapshotV3Mapper.deleteByTradeDate(tradeDate);
        sectorContextSnapshotV3Mapper.deleteByTradeDate(tradeDate);

        List<StockTailTradeSnapshotV3> tails = stockTailTradeSnapshotV3Mapper.selectList(
                new LambdaQueryWrapper<StockTailTradeSnapshotV3>()
                        .eq(StockTailTradeSnapshotV3::getTradeDate, tradeDate)
                        .eq(StockTailTradeSnapshotV3::getValidFlag, true));
        if (CollectionUtils.isEmpty(tails)) {
            log.warn("V3: no valid tail snapshots for market context, date={}", tradeDate);
            return;
        }

        List<StockDataDailyAll> dailyBars = loadDailyBars(null, tradeDate.minusDays(10), tradeDate);
        Map<String, List<StockDataDailyAll>> dailyMap = dailyBars.stream()
                .collect(Collectors.groupingBy(StockDataDailyAll::getStockCode));
        Map<String, PubStockInfo> infoMap = pubStockInfoMapper.selectList(null).stream()
                .collect(Collectors.toMap(PubStockInfo::getSymbol, i -> i, (a, b) -> a));

        List<V3FactorSnapshot> snapshots = new ArrayList<>();
        for (StockTailTradeSnapshotV3 tail : tails) {
            PubStockInfo info = infoMap.get(tail.getStockCode());
            if (info == null) continue;

            V3FactorSnapshot snapshot = buildBaseSnapshotFromTailV3(tail, info);
            fillPreviousClose(snapshot, dailyMap.getOrDefault(tail.getStockCode(), Collections.emptyList()), tradeDate);
            if (snapshot.getReturnTo1430() != null) {
                snapshots.add(snapshot);
            }
        }
        if (snapshots.isEmpty()) return;

        saveMarketContextV3(tradeDate, snapshots, tails.size());
        saveSectorContextV3(tradeDate, snapshots);
    }



    private List<V3FactorSnapshot> buildV3FactorSnapshots(
            LocalDate tradeDate,
            String stockCode,
            RealtimeStrategyConfig strategyConfig) {

        List<PubStockInfo> stockInfos = loadStockInfos(stockCode);
        if (CollectionUtils.isEmpty(stockInfos)) return Collections.emptyList();

        Map<String, PubStockInfo> infoMap = stockInfos.stream()
                .collect(Collectors.toMap(PubStockInfo::getSymbol, i -> i, (a, b) -> a));

        LambdaQueryWrapper<StockTailTradeSnapshotV3> tailQuery = new LambdaQueryWrapper<>();
        tailQuery.eq(StockTailTradeSnapshotV3::getTradeDate, tradeDate)
                .eq(StockTailTradeSnapshotV3::getValidFlag, true);
        if (StringUtils.hasText(stockCode)) {
            tailQuery.eq(StockTailTradeSnapshotV3::getStockCode, stockCode);
        }
        List<StockTailTradeSnapshotV3> tails = stockTailTradeSnapshotV3Mapper.selectList(tailQuery);
        if (CollectionUtils.isEmpty(tails)) return Collections.emptyList();

        LocalDate dataStartDate = tradeDate.minusDays(strategyConfig.getMinuteLookbackDays() + 30);
        Map<String, List<StockDataDailyAll>> dailyMap = loadDailyBars(stockCode, dataStartDate, tradeDate).stream()
                .collect(Collectors.groupingBy(StockDataDailyAll::getStockCode));

        Map<String, StockShortSampleStatsV3> shortStatsMap = loadShortSampleStatsV3(stockCode, tradeDate);
        MarketContextSnapshotV3 marketContext = loadMarketContextV3(tradeDate);
        Map<String, SectorContextSnapshotV3> sectorContextMap = loadSectorContextV3(tradeDate);

        DailyTrendCalculatorV3 dailyTrendCalculator = new DailyTrendCalculatorV3();
        V3FilterCalculator filterCalculator = new V3FilterCalculator();

        List<V3FactorSnapshot> snapshots = new ArrayList<>();
        for (StockTailTradeSnapshotV3 tail : tails) {
            PubStockInfo info = infoMap.get(tail.getStockCode());
            if (info == null) continue;

            V3FactorSnapshot snapshot = buildBaseSnapshotFromTailV3(tail, info);
            List<StockDataDailyAll> sDaily = dailyMap.getOrDefault(tail.getStockCode(), Collections.emptyList());
            dailyTrendCalculator.calculate(snapshot, sDaily, tradeDate);
            fillPreviousClose(snapshot, sDaily, tradeDate);

            if (!filterCalculator.apply(snapshot, info, strategyConfig, tradeDate)) {
                continue;
            }

            applyShortSampleStatsV3(snapshot, shortStatsMap.get(tail.getStockCode()));
            applyMarketContextV3(snapshot, marketContext);
            applySectorContextV3(snapshot, sectorContextMap.get(info.getSector()));
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private V3FactorSnapshot buildBaseSnapshotFromTailV3(StockTailTradeSnapshotV3 tail, PubStockInfo info) {
        V3FactorSnapshot snapshot = new V3FactorSnapshot();
        snapshot.setStockCode(tail.getStockCode());
        snapshot.setTradeDate(tail.getTradeDate());
        snapshot.setShortName(info.getShortName());
        snapshot.setMarket(info.getMarkets());
        snapshot.setSector(info.getSector());
        snapshot.setPrice1400(tail.getPrice1400());
        snapshot.setPrice1430(tail.getPrice1430());
        snapshot.setTodayTailAmount(tail.getTailAmount14001430());
        snapshot.setAmountBefore1430(tail.getAmountBefore1430());
        snapshot.setIntradayLowBefore1430(tail.getLowBefore1430());
        snapshot.setIntradayHighBefore1430(tail.getHighBefore1430());

        if (tail.getPrice1400() != null && tail.getPrice1400().compareTo(BigDecimal.ZERO) > 0
                && tail.getPrice1430() != null) {
            snapshot.setTailMomentum(tail.getPrice1430()
                    .divide(tail.getPrice1400(), 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE));
        }
        if (tail.getHighBefore1430() != null && tail.getLowBefore1430() != null && tail.getPrice1430() != null) {
            BigDecimal range = tail.getHighBefore1430().subtract(tail.getLowBefore1430());
            if (range.compareTo(BigDecimal.ZERO) > 0) {
                snapshot.setIntradayPosition(tail.getPrice1430().subtract(tail.getLowBefore1430())
                        .divide(range, 4, RoundingMode.HALF_UP));
            } else {
                snapshot.setIntradayPosition(new BigDecimal("0.5"));
            }
        }
        return snapshot;
    }

    private void fillPreviousClose(V3FactorSnapshot snapshot, List<StockDataDailyAll> dailyBars, LocalDate tradeDate) {
        StockDataDailyAll prevDayK = dailyBars.stream()
                .filter(d -> d.getTradeDate() != null && d.getTradeDate().isBefore(tradeDate))
                .max(Comparator.comparing(StockDataDailyAll::getTradeDate))
                .orElse(null);
        if (prevDayK == null || prevDayK.getCloseForead() == null || prevDayK.getCloseForead().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        snapshot.setClosePrevious(prevDayK.getCloseForead());
        if (snapshot.getPrice1430() != null) {
            snapshot.setReturnTo1430(snapshot.getPrice1430()
                    .divide(prevDayK.getCloseForead(), 6, RoundingMode.HALF_UP)
                    .subtract(BigDecimal.ONE));
        }
    }

    private void applyShortSampleStatsV3(V3FactorSnapshot snapshot, StockShortSampleStatsV3 stats) {
        if (stats == null) {
            snapshot.setShortSampleCount(0);
            return;
        }
        snapshot.setShortSampleCount(stats.getSampleCount());
        snapshot.setBuyFillRate(stats.getBuyFillRate());
        snapshot.setBuy3PctFillRate(stats.getBuy3PctFillRate());
        snapshot.setBuy2PctFillRate(stats.getBuy2PctFillRate());
        snapshot.setBuy1PctFillRate(stats.getBuy1PctFillRate());
        snapshot.setNotFilledRate(stats.getNotFilledRate());
        snapshot.setTakeProfit1PctRate(stats.getTakeProfit1PctRate());
        snapshot.setTakeProfit2PctRate(stats.getTakeProfit2PctRate());
        snapshot.setTakeProfit3PctRate(stats.getTakeProfit3PctRate());
        snapshot.setForceSellRate(stats.getForceSellRate());
        snapshot.setAvgNetReturnBps(stats.getAvgNetReturnBps());
        snapshot.setShortAvgNetReturnBps(stats.getAvgNetReturnBps());
        snapshot.setAvgGrossReturnBps(stats.getAvgGrossReturnBps());
        snapshot.setAvgTakeProfitReturnBps(stats.getAvgTakeProfitReturnBps());
        snapshot.setAvgForceSellReturnBps(stats.getAvgForceSellReturnBps());
        snapshot.setMaxLossBps(stats.getMaxLossBps());
        snapshot.setBuy3PctAvgNetReturnBps(stats.getBuy3PctAvgNetReturnBps());
        snapshot.setBuy2PctAvgNetReturnBps(stats.getBuy2PctAvgNetReturnBps());
        snapshot.setBuy1PctAvgNetReturnBps(stats.getBuy1PctAvgNetReturnBps());
        snapshot.setPostBuyDrawdownAvg(stats.getPostBuyDrawdownAvg());
        snapshot.setForceSellLossRate(stats.getForceSellLossRate());
        snapshot.setShortRawWinRate(stats.getRawWinRate());
        snapshot.setShortAdjustedWinRate(stats.getAdjustedWinRate());
    }

    private void applyMarketContextV3(V3FactorSnapshot snapshot, MarketContextSnapshotV3 marketContext) {
        if (marketContext == null) return;
        snapshot.setMarketBreadth(marketContext.getMarketBreadth1430());
        snapshot.setMarketReturn1430(marketContext.getMarketReturn1430());
        snapshot.setMarketRegime(marketContext.getMarketRegime());
    }

    private void applySectorContextV3(V3FactorSnapshot snapshot, SectorContextSnapshotV3 sectorContext) {
        if (sectorContext == null) return;
        snapshot.setSectorStrength(sectorContext.getSectorStrength1430());
        snapshot.setSectorBreadth(sectorContext.getSectorBreadth1430());
        snapshot.setSectorRank(sectorContext.getSectorRank());
        if (snapshot.getReturnTo1430() != null && sectorContext.getSectorStrength1430() != null) {
            snapshot.setRelativeStrength1430(snapshot.getReturnTo1430().subtract(sectorContext.getSectorStrength1430()));
        }
    }

    private Map<String, StockShortSampleStatsV3> loadShortSampleStatsV3(String stockCode, LocalDate tradeDate) {
        LambdaQueryWrapper<StockShortSampleStatsV3> query = new LambdaQueryWrapper<>();
        query.eq(StockShortSampleStatsV3::getTradeDate, tradeDate);
        if (StringUtils.hasText(stockCode)) query.eq(StockShortSampleStatsV3::getStockCode, stockCode);
        return stockShortSampleStatsV3Mapper.selectList(query).stream()
                .collect(Collectors.toMap(StockShortSampleStatsV3::getStockCode, s -> s, (a, b) -> a));
    }

    private MarketContextSnapshotV3 loadMarketContextV3(LocalDate tradeDate) {
        return marketContextSnapshotV3Mapper.selectOne(
                new LambdaQueryWrapper<MarketContextSnapshotV3>()
                        .eq(MarketContextSnapshotV3::getTradeDate, tradeDate));
    }

    private Map<String, SectorContextSnapshotV3> loadSectorContextV3(LocalDate tradeDate) {
        return sectorContextSnapshotV3Mapper.selectList(
                        new LambdaQueryWrapper<SectorContextSnapshotV3>()
                                .eq(SectorContextSnapshotV3::getTradeDate, tradeDate))
                .stream()
                .collect(Collectors.toMap(SectorContextSnapshotV3::getSectorName, s -> s, (a, b) -> a));
    }

    private void saveRealtimeCandidateScoreResultsV3(
            LocalDate tradeDate,
            String stockCode,
            List<RealtimeCandidateScoreResultV3> records) {
        if (StringUtils.hasText(stockCode)) {
            log.info("V3: single stock score is not persisted, date={}, stockCode={}", tradeDate, stockCode);
            return;
        }
        if (CollectionUtils.isEmpty(records)) {
            log.warn("V3: empty market score result, skip persist, date={}", tradeDate);
            return;
        }
        realtimeCandidateScoreResultV3Mapper.deleteByTradeDateAndStrategyVersion(
                tradeDate, RealtimeCandidateScoreEngineV3.STRATEGY_VERSION);
        ListUtils.splitListByCount(records, 500).forEach(realtimeCandidateScoreResultV3Mapper::insertBatch);
        log.info("V3: score results persisted, date={}, count={}", tradeDate, records.size());
    }

    private StockTailTradeSnapshotV3 buildTailTradeSnapshotV3(
            String stockCode,
            LocalDate tradeDate,
            List<StockMinuteData> minutes) {
        StockTailTradeSnapshotV3 snapshot = new StockTailTradeSnapshotV3();
        snapshot.setStockCode(stockCode);
        snapshot.setTradeDate(tradeDate);
        snapshot.setCreatedAt(LocalDateTime.now());
        snapshot.setUpdatedAt(LocalDateTime.now());

        BigDecimal amountBefore1430 = BigDecimal.ZERO;
        long volumeBefore1430 = 0L;
        BigDecimal tailAmount = BigDecimal.ZERO;
        long tailVolume = 0L;
        BigDecimal closeAmount = BigDecimal.ZERO;
        BigDecimal sellAmount = BigDecimal.ZERO;
        long sellVolume = 0L;
        StockMinuteData closeMinute = null;

        for (StockMinuteData minute : minutes) {
            if (minute.getTime() == null) continue;
            int time = minute.getTime();
            BigDecimal amount = minute.getMinuteAmount() != null ? minute.getMinuteAmount() : BigDecimal.ZERO;
            long volume = minute.getMinuteVolume() != null ? minute.getMinuteVolume() : 0L;
            closeAmount = closeAmount.add(amount);

            if (closeMinute == null || time > closeMinute.getTime()) {
                closeMinute = minute;
            }
            if (time == 1400) snapshot.setPrice1400(minute.getPrice());
            if (time == 1430) snapshot.setPrice1430(minute.getPrice());
            if (time == 945) snapshot.setPrice0945(minute.getPrice());

            if (time <= 1430) {
                amountBefore1430 = amountBefore1430.add(amount);
                volumeBefore1430 += volume;
                snapshot.setHighBefore1430(max(snapshot.getHighBefore1430(), minute.getHighPrice()));
                snapshot.setLowBefore1430(min(snapshot.getLowBefore1430(), minute.getLowPrice()));
            }
            if (time >= 1400 && time <= 1430) {
                tailAmount = tailAmount.add(amount);
                tailVolume += volume;
            }
            if (time >= 1435 && time <= 1444) {
                snapshot.setLow14351444(min(snapshot.getLow14351444(), minute.getLowPrice()));
            } else if (time >= 1445 && time <= 1454) {
                snapshot.setLow14451454(min(snapshot.getLow14451454(), minute.getLowPrice()));
            } else if (time >= 1455 && time <= 1500) {
                snapshot.setLow14551500(min(snapshot.getLow14551500(), minute.getLowPrice()));
            }
            if (time >= 930 && time <= 935) {
                snapshot.setHigh09300935(max(snapshot.getHigh09300935(), minute.getHighPrice()));
            } else if (time >= 936 && time <= 940) {
                snapshot.setHigh09360940(max(snapshot.getHigh09360940(), minute.getHighPrice()));
            } else if (time >= 941 && time <= 944) {
                snapshot.setHigh09410944(max(snapshot.getHigh09410944(), minute.getHighPrice()));
            }
            if (time >= 930 && time <= 945) {
                sellAmount = sellAmount.add(amount);
                sellVolume += volume;
            }
        }

        snapshot.setAmountBefore1430(amountBefore1430);
        snapshot.setVolumeBefore1430(volumeBefore1430);
        snapshot.setTailAmount14001430(tailAmount);
        snapshot.setTailVolume14001430(tailVolume);
        snapshot.setCloseAmount(closeAmount);
        if (closeMinute != null) snapshot.setClosePrice(closeMinute.getPrice());
        snapshot.setSellAmount09300945(sellAmount);
        snapshot.setSellVolume09300945(sellVolume);
        if (sellVolume > 0 && sellAmount.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal shares = BigDecimal.valueOf(sellVolume).multiply(BigDecimal.valueOf(100));
            snapshot.setVwap09300945(sellAmount.divide(shares, 4, RoundingMode.HALF_UP));
        }

        boolean valid = snapshot.getPrice1400() != null && snapshot.getPrice1430() != null;
        snapshot.setValidFlag(valid);
        if (!valid) {
            snapshot.setInvalidReason(snapshot.getPrice1400() == null ? "MISSING_1400_PRICE" : "MISSING_1430_PRICE");
        }
        return snapshot;
    }

    private Map<String, List<StockTailTradeSnapshotV3>> loadTailSnapshotHistoryV3(LocalDate startDate, LocalDate tradeDate) {
        List<StockTailTradeSnapshotV3> list = stockTailTradeSnapshotV3Mapper.selectList(
                new LambdaQueryWrapper<StockTailTradeSnapshotV3>()
                        .ge(StockTailTradeSnapshotV3::getTradeDate, startDate)
                        .lt(StockTailTradeSnapshotV3::getTradeDate, tradeDate)
                        .eq(StockTailTradeSnapshotV3::getValidFlag, true));
        return list.stream().collect(Collectors.groupingBy(s -> normalizeStockCode(s.getStockCode())));
    }

    private StockShortSampleStatsV3 buildShortSampleStatsV3(String stockCode, LocalDate tradeDate, V3FactorSnapshot snapshot) {
        StockShortSampleStatsV3 stats = new StockShortSampleStatsV3();
        stats.setStockCode(stockCode);
        stats.setTradeDate(tradeDate);
        stats.setBuyFillRate(snapshot.getBuyFillRate());
        stats.setBuy3PctFillRate(snapshot.getBuy3PctFillRate());
        stats.setBuy2PctFillRate(snapshot.getBuy2PctFillRate());
        stats.setBuy1PctFillRate(snapshot.getBuy1PctFillRate());
        stats.setNotFilledRate(snapshot.getNotFilledRate());
        stats.setTakeProfit1PctRate(snapshot.getTakeProfit1PctRate());
        stats.setTakeProfit2PctRate(snapshot.getTakeProfit2PctRate());
        stats.setTakeProfit3PctRate(snapshot.getTakeProfit3PctRate());
        stats.setForceSellRate(snapshot.getForceSellRate());
        stats.setAvgNetReturnBps(snapshot.getAvgNetReturnBps());
        stats.setAvgGrossReturnBps(snapshot.getAvgGrossReturnBps());
        stats.setAvgTakeProfitReturnBps(snapshot.getAvgTakeProfitReturnBps());
        stats.setAvgForceSellReturnBps(snapshot.getAvgForceSellReturnBps());
        stats.setMaxLossBps(snapshot.getMaxLossBps());
        stats.setBuy3PctAvgNetReturnBps(snapshot.getBuy3PctAvgNetReturnBps());
        stats.setBuy2PctAvgNetReturnBps(snapshot.getBuy2PctAvgNetReturnBps());
        stats.setBuy1PctAvgNetReturnBps(snapshot.getBuy1PctAvgNetReturnBps());
        stats.setPostBuyDrawdownAvg(snapshot.getPostBuyDrawdownAvg());
        stats.setForceSellLossRate(snapshot.getForceSellLossRate());
        stats.setRawWinRate(snapshot.getShortRawWinRate());
        stats.setAdjustedWinRate(snapshot.getShortAdjustedWinRate());
        stats.setSampleCount(snapshot.getShortSampleCount());
        stats.setCreatedAt(LocalDateTime.now());
        return stats;
    }

    private void saveMarketContextV3(LocalDate tradeDate, List<V3FactorSnapshot> snapshots, int totalTailCount) {
        List<BigDecimal> returns = snapshots.stream()
                .map(V3FactorSnapshot::getReturnTo1430)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        long upCount = returns.stream().filter(r -> r.compareTo(BigDecimal.ZERO) > 0).count();
        long strongCount = returns.stream().filter(r -> r.compareTo(new BigDecimal("0.02")) >= 0).count();
        long weakCount = returns.stream().filter(r -> r.compareTo(new BigDecimal("-0.02")) <= 0).count();
        long limitUpCount = returns.stream().filter(r -> r.compareTo(new BigDecimal("0.095")) > 0).count();
        long limitDownCount = returns.stream().filter(r -> r.compareTo(new BigDecimal("-0.095")) < 0).count();

        BigDecimal marketBreadth = rate(upCount, returns.size());
        MarketContextSnapshotV3 market = new MarketContextSnapshotV3();
        market.setTradeDate(tradeDate);
        market.setMarketBreadth1430(marketBreadth);
        market.setMarketReturn1430(avg(returns));
        market.setMarketStrongStockRatio(rate(strongCount, returns.size()));
        market.setMarketWeakStockRatio(rate(weakCount, returns.size()));
        market.setLimitUpCount((int) limitUpCount);
        market.setLimitDownCount((int) limitDownCount);
        market.setMarketRegime(new SectorMarketCalculatorV3().determineMarketRegime(marketBreadth));
        market.setTotalValidStocks(totalTailCount);
        market.setTotalFilteredStocks(snapshots.size());
        market.setCreatedAt(LocalDateTime.now());
        marketContextSnapshotV3Mapper.insert(market);
    }

    private void saveSectorContextV3(LocalDate tradeDate, List<V3FactorSnapshot> snapshots) {
        Map<String, List<V3FactorSnapshot>> sectorGroups = snapshots.stream()
                .filter(s -> s.getSector() != null)
                .collect(Collectors.groupingBy(V3FactorSnapshot::getSector));

        List<SectorContextSnapshotV3> sectors = new ArrayList<>();
        for (Map.Entry<String, List<V3FactorSnapshot>> entry : sectorGroups.entrySet()) {
            List<V3FactorSnapshot> sectorStocks = entry.getValue();
            List<BigDecimal> returns = sectorStocks.stream()
                    .map(V3FactorSnapshot::getReturnTo1430)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            long upCount = returns.stream().filter(r -> r.compareTo(BigDecimal.ZERO) > 0).count();

            SectorContextSnapshotV3 sector = new SectorContextSnapshotV3();
            sector.setTradeDate(tradeDate);
            sector.setSectorName(entry.getKey());
            sector.setSectorStrength1430(avg(returns));
            sector.setSectorBreadth1430(rate(upCount, returns.size()));
            sector.setSectorVolumeRatio(avg(sectorStocks.stream()
                    .map(V3FactorSnapshot::getTailAmountRatio)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList())));
            sector.setStockCount(sectorStocks.size());
            sector.setCreatedAt(LocalDateTime.now());
            sectors.add(sector);
        }

        sectors.sort((a, b) -> {
            BigDecimal av = a.getSectorStrength1430() != null ? a.getSectorStrength1430() : BigDecimal.ZERO;
            BigDecimal bv = b.getSectorStrength1430() != null ? b.getSectorStrength1430() : BigDecimal.ZERO;
            return bv.compareTo(av);
        });
        for (int i = 0; i < sectors.size(); i++) {
            sectors.get(i).setSectorRank(i + 1);
        }
        if (!sectors.isEmpty()) {
            sectorContextSnapshotV3Mapper.insertBatch(sectors);
        }
    }

    private Map<String, List<StockMinuteData>> groupMinuteBarsByStockCode(List<StockMinuteData> minuteBars) {
        if (CollectionUtils.isEmpty(minuteBars)) return Collections.emptyMap();
        return minuteBars.stream()
                .filter(m -> m.getStockCode() != null)
                .collect(Collectors.groupingBy(m -> normalizeStockCode(m.getStockCode())));
    }

    private String normalizeStockCode(Integer stockCode) {
        return String.format("%06d", stockCode);
    }

    private String normalizeStockCode(String stockCode) {
        if (stockCode == null) return null;
        String pureStockCode = stockCode.length() > 6 ? stockCode.substring(stockCode.length() - 6) : stockCode;
        if (pureStockCode.length() < 6) {
            try {
                return String.format("%06d", Integer.parseInt(pureStockCode));
            } catch (NumberFormatException e) {
                return pureStockCode;
            }
        }
        return pureStockCode;
    }

    private BigDecimal max(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.compareTo(current) > 0) return candidate;
        return current;
    }

    private BigDecimal min(BigDecimal current, BigDecimal candidate) {
        if (candidate == null) return current;
        if (current == null || candidate.compareTo(current) < 0) return candidate;
        return current;
    }

    private BigDecimal avg(List<BigDecimal> values) {
        List<BigDecimal> valid = values.stream().filter(Objects::nonNull).collect(Collectors.toList());
        if (valid.isEmpty()) return BigDecimal.ZERO;
        BigDecimal sum = valid.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(valid.size()), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal rate(long numerator, long denominator) {
        if (denominator <= 0) return BigDecimal.ZERO;
        return BigDecimal.valueOf(numerator).divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    /**
     * 构建策略配置。
     */
    private RealtimeStrategyConfig buildStrategyConfig() {
        RealtimeStrategyConfig config = new RealtimeStrategyConfig();
        // V3 配置使用默认值，后续可外部化
        return config;
    }

    /**
     * 构建成本配置。
     */
    private CostConfig buildCostConfig() {
        CostConfig config = new CostConfig();
        // V3 默认成本 25bps，后续可外部化
        return config;
    }

    /**
     * 加载股票基础信息。
     */
    private List<PubStockInfo> loadStockInfos(String stockCode) {
        if (StringUtils.hasText(stockCode)) {
            return pubStockInfoMapper.findByStockCode(normalizeStockCode(stockCode));
        }
        return pubStockInfoMapper.selectList(new LambdaQueryWrapper<>());
    }

    /**
     * 加载日K数据。
     */
    private List<StockDataDailyAll> loadDailyBars(String stockCode, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<StockDataDailyAll> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(stockCode)) {
            query.eq(StockDataDailyAll::getStockCode, stockCode);
        }
        query.ge(StockDataDailyAll::getTradeDate, startDate)
                .le(StockDataDailyAll::getTradeDate, endDate);
        return stockDataDailyAllMapper.selectList(query);
    }

    /**
     * 加载分钟线数据。
     */
    private List<StockMinuteData> loadMinuteBars(String stockCode, LocalDate startDate, LocalDate endDate) {
        LambdaQueryWrapper<StockMinuteData> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(stockCode)) {
            query.eq(StockMinuteData::getStockCode, Integer.valueOf(stockCode));
        }
        query.ge(StockMinuteData::getTradeDate, startDate)
                .le(StockMinuteData::getTradeDate, endDate);
        return stockMinuteDataMapper.selectList(query);
    }
}
