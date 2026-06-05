package org.analyse.analysestock.analysis.serivce.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.entity.*;
import org.analyse.analysestock.analysis.mapper.PubStockInfoMapper;
import org.analyse.analysestock.analysis.mapper.StockDataDailyAllMapper;
import org.analyse.analysestock.analysis.mapper.StockMinuteDataMapper;
import org.analyse.analysestock.analysis.mapper.TradingDateMapper;
import org.analyse.analysestock.analysis.serivce.ImportService;
import org.analyse.analysestock.config.util.RestTemplateUtil;
import org.analyse.analysestock.config.util.StockCodeUtil;
import org.analyse.analysestock.realtimecandidate.config.CostConfig;
import org.analyse.analysestock.realtimecandidate.config.RealtimeStrategyConfig;
import org.analyse.analysestock.realtimecandidate.dto.RealtimeCandidateScoreRecord;
import org.analyse.analysestock.realtimecandidate.engine.RealtimeCandidateScoreEngine;
import org.analyse.analysestock.util.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
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
    private RestTemplateUtil restTemplateUtil;

    @Autowired
    @Qualifier("importExecutor")
    private Executor importExecutor;

    private static final String MINUTE_URL = "http://10.0.11.15:5757/BQreal/rds/RDS.do?pkgtype=MinuteKLine&code=%s&min=1&end=%s&count=240";


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
        LocalDate defaultIpoDate = LocalDate.parse("20200101", DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (ipoDate == null || ipoDate.compareTo(defaultIpoDate) < 0) {
            ipoDate = LocalDate.parse("20200101", DateTimeFormatter.ofPattern("yyyyMMdd"));
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
        ListUtils.sort(stockDataDailies, true, "tradeDate");
        LocalDate tradeDate = null;
        if (!StringUtils.isEmpty(dateStr)) {
            tradeDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        LocalDate missingDate = null;
        if (tradeDate == null) {
            missingDate = tradingDateMapper.getNewsetTradingDate(LocalDate.now().plusDays(-1), 1);
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
            LocalDate lastDay = tradingDateMapper.findTradingDateSqlServerStockCode(up.getTradeDate(), 0, 1);
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
                lastDate = tradingDateMapper.findTradingDateSqlServerStockCode(lastDate, 0, 1);
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

    private final RealtimeCandidateScoreEngine scoreEngine = new RealtimeCandidateScoreEngine();

    @Override
    public List<RealtimeCandidateScoreRecord> calculateRealtimeCandidateScores(String stockCode, LocalDate tradeDate) {
        List<LocalDate> targetDates = new ArrayList<>();
        if (tradeDate != null) {
            targetDates.add(tradeDate);
        } else {
            targetDates = stockMinuteDataMapper.findAllTradeDates();
            if (CollectionUtils.isEmpty(targetDates)) {
                log.warn("未找到任何交易日期记录");
                return Collections.emptyList();
            }
        }

        List<RealtimeCandidateScoreRecord> allResults = new ArrayList<>();
        for (LocalDate date : targetDates) {
            allResults.addAll(calculateForSingleDate(stockCode, date));
        }
        return allResults;
    }

    private List<RealtimeCandidateScoreRecord> calculateForSingleDate(String stockCode, LocalDate tradeDate) {
        log.info("开始计算股票 {} 在 {} 的实时候选股评分", stockCode == null ? "ALL" : stockCode, tradeDate);

        // 1. 获取基础信息
        QueryWrapper<PubStockInfo> stockInfoQuery = new QueryWrapper<>();
        if (stockCode != null) {
            stockInfoQuery.eq("symbol", stockCode);
        }
        List<PubStockInfo> stockInfos = pubStockInfoMapper.selectList(stockInfoQuery);
        if (CollectionUtils.isEmpty(stockInfos)) {
            log.warn("未找到对应的股票基础信息: {}", stockCode);
            return Collections.emptyList();
        }

        // 2. 获取分钟线的日期窗口 (最近21个交易日，相对于当前计算的 tradeDate)
        // 注意：findByMinuteTradeDate() 目前可能是固定逻辑，可能需要传入 tradeDate
        // 但根据现有逻辑，它返回的是库里最近的21天。
        // 如果是回测所有历史交易日，findByMinuteTradeDate 可能需要调整。
        List<LocalDate> minuteDates = tradingDateMapper.findByMinuteTradeDate();
        if (CollectionUtils.isEmpty(minuteDates)) {
            log.warn("未找到分钟线交易日期");
            return Collections.emptyList();
        }

        // 3. 获取分钟线数据
        QueryWrapper<StockMinuteData> minuteQuery = new QueryWrapper<>();
        minuteQuery.in("trade_date", minuteDates);
        if (stockCode != null) {
            // 注意：StockMinuteData.stockCode 是 Integer 还是 String? 
            // 实体类中是 Integer.
            try {
                minuteQuery.eq("stock_code", Integer.parseInt(stockCode));
            } catch (NumberFormatException e) {
                log.error("股票代码格式错误: {}", stockCode);
            }
        }
        List<StockMinuteData> minuteBars = stockMinuteDataMapper.selectList(minuteQuery);

        // 4. 获取日K数据
        QueryWrapper<StockDataDailyAll> dailyQuery = new QueryWrapper<>();
        dailyQuery.ge("trade_date", tradeDate.minusYears(1));
        dailyQuery.le("trade_date", tradeDate);
        if (stockCode != null) {
            dailyQuery.eq("stock_code", stockCode);
        }
        List<StockDataDailyAll> dailyBars = stockDataDailyAllMapper.selectList(dailyQuery);

        // 5. 执行计算
        RealtimeStrategyConfig strategyConfig = new RealtimeStrategyConfig();
        CostConfig costConfig = new CostConfig();

        List<RealtimeCandidateScoreRecord> results = scoreEngine.calculateWithEntities(
                tradeDate,
                dailyBars,
                minuteBars,
                stockInfos,
                strategyConfig,
                costConfig
        );

        log.info("日期 {} 计算完成，共生成 {} 条评分记录", tradeDate, results.size());
        return results;
    }
}
