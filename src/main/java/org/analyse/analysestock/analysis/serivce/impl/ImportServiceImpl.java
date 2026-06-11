package org.analyse.analysestock.analysis.serivce.impl;

import cn.hutool.core.text.CharSequenceUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.analyse.analysestock.analysis.entity.AbstractStockDataDaily;
import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.analysis.entity.TradingDate;
import org.analyse.analysestock.analysis.mapper.PubStockInfoMapper;
import org.analyse.analysestock.analysis.mapper.StockDataDailyAllMapper;
import org.analyse.analysestock.analysis.mapper.StockMinuteDataMapper;
import org.analyse.analysestock.analysis.mapper.TradingDateMapper;
import org.analyse.analysestock.analysis.serivce.ImportService;
import org.analyse.analysestock.analysis.vo.MissingStockDataItem;
import org.analyse.analysestock.config.util.RestTemplateUtil;
import org.analyse.analysestock.config.util.StockCodeUtil;
import org.analyse.analysestock.util.ListUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

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
    public Integer importStockMinuteData(String code, LocalDate date) {
        List<LocalDate> tradeDates = new ArrayList<>();
        if (date != null) {
            tradeDates.add(date);
        } else {
            tradeDates = tradingDateMapper.findByMinuteTradeDate();
        }

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
                .map(stockInfo -> CompletableFuture.runAsync(() -> totalImported.addAndGet(importSingleStockMinuteData(stockInfo, tradeDates)), importExecutor))
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

                LambdaQueryWrapper<StockMinuteData> queryWrapper = new LambdaQueryWrapper<>();
                queryWrapper.eq(StockMinuteData::getStockCode, Integer.valueOf(stockCode))
                        .eq(StockMinuteData::getTradeDate, tradeDate)
                        .orderByDesc(StockMinuteData::getTime)
                        .last("limit 1");
                StockMinuteData lastData = stockMinuteDataMapper.selectOne(queryWrapper);
                int lastTime = lastData == null ? 0 : lastData.getTime();

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
                if (dataObj == null) {
                    continue;
                }
                JSONArray list = dataObj.getJSONArray("list");
                if (list == null || list.isEmpty()) {
                    continue;
                }

                List<StockMinuteData> toSave = new ArrayList<>();
                for (int i = 0; i < list.size(); i++) {
                    JSONArray item = list.getJSONArray(i);
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
                    data.setPrice(item.getBigDecimal(5));
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
                .map(stockInfo -> CompletableFuture.runAsync(() -> totalImported.addAndGet(importSingleStockDailyData(stockInfo, dateStr)), importExecutor))
                .collect(Collectors.toList());

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        return totalImported.get();
    }

    private int importSingleStockDailyData(PubStockInfo stockInfo, String dateStr) {
        return addStockDataDailyAll(stockInfo.getSymbol(), dateStr);
    }

    public int addStockDataDailyAll(String stockCode, String dateStr) {
        log.info("stock_data_daily_all(全股) 导入:{},时间:{}", stockCode, dateStr);
        int count = 200;
        String dailyCode = StockCodeUtil.addMarketPrefix(stockCode);
        if (!StringUtils.isEmpty(dateStr)) {
            count = 1;
        }

        List<StockDataDailyAll> stockDataDailies = new ArrayList<>();
        LocalDate ipoDate = pubStockInfoMapper.findIpoDateByStockCode(stockCode);
        LocalDate defaultIpoDate = LocalDate.parse("20250101", DateTimeFormatter.ofPattern("yyyyMMdd"));
        if (ipoDate == null || ipoDate.compareTo(defaultIpoDate) < 0) {
            ipoDate = defaultIpoDate;
        }
        String startTime = ipoDate.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        getRdsReferenceStockCodeAll(dailyCode, startTime, dateStr, stockDataDailies, count, 0);

        List<StockDataDailyAll> afterList = new ArrayList<>();
        List<StockDataDailyAll> beforeList = new ArrayList<>();
        getRdsReferenceStockCodeAll(dailyCode, startTime, dateStr, afterList, count, 2);
        getRdsReferenceStockCodeAll(dailyCode, startTime, dateStr, beforeList, count, 1);
        if (!dailyCode.equals(stockCode)) {
            stockDataDailies.forEach(stockDataDailyAll -> stockDataDailyAll.setStockCode(stockCode));
        }
        setClosePrice(afterList, beforeList, stockDataDailies);
        ListUtils.sort(stockDataDailies, true, "tradeDate");

        LocalDate tradeDate = null;
        if (!StringUtils.isEmpty(dateStr)) {
            tradeDate = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("yyyyMMdd"));
        }
        LocalDate missingDate = tradeDate == null ? tradingDateMapper.getNewsetTradingDate(LocalDate.now().plusDays(-1)) : tradeDate;
        Integer missingResult = addDataMissing(stockCode, dateStr, stockDataDailies, missingDate, count);
        if (missingResult != null) {
            return missingResult;
        }

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
        if (CharSequenceUtil.isBlank(str)) {
            return;
        }

        JSONArray jsonArray = JSON.parseObject(str).getJSONObject("data").getJSONArray("day");
        if (jsonArray.isEmpty()) {
            log.debug("调用Rds接口获取收盘价,未取到数据 {}", url);
            return;
        }

        for (int i = 0; i < jsonArray.size(); i++) {
            T stockDataDaily;
            try {
                stockDataDaily = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.error("创建股票数据实例失败: " + clazz.getSimpleName(), e);
                continue;
            }

            JSONArray item = jsonArray.getJSONArray(i);
            LocalDate tradeDate = LocalDate.parse(item.getString(0), yyyyMMdd);
            if (count == 1 && tradeDate.compareTo(endTime) != 0) {
                continue;
            }
            if (ipoTime.compareTo(tradeDate) > 0) {
                continue;
            }
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMdd");
            try {
                Date date = simpleDateFormat.parse(item.getString(0));
                tradeDate = date.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            } catch (ParseException e) {
                log.error("解析日K交易日期失败: {}", item.getString(0), e);
                continue;
            }

            stockDataDaily.setTradeDate(tradeDate);
            stockDataDaily.setOpen(item.getBigDecimal(1));
            stockDataDaily.setHighest(item.getBigDecimal(2));
            stockDataDaily.setLowest(item.getBigDecimal(3));
            stockDataDaily.setClose(item.getBigDecimal(4));
            stockDataDaily.setAmount(item.getBigDecimal(6));
            stockDataDaily.setVolume(item.getLong(5));
            stockDataDaily.setClosePrevious(item.getBigDecimal(7));
            stockDataDaily.setStockCode(stockCode);
            stockDataDailies.add(stockDataDaily);
        }

        if (jsonArray.size() > 0 && count != 1) {
            String dateString = LocalDate.parse(String.valueOf(((JSONArray) jsonArray.get(0)).get(0)), yyyyMMdd)
                    .minusDays(1)
                    .format(yyyyMMdd);
            getRdsReferenceStockCodeAllGeneric(stockCode, start, dateString, stockDataDailies, count, power, clazz);
        }
    }

    public void getRdsReferenceStockCodeAll(String stockCode, String start, String end, List<StockDataDailyAll> stockDataDailies, int count, int power) {
        getRdsReferenceStockCodeAllGeneric(stockCode, start, end, stockDataDailies, count, power, StockDataDailyAll.class);
    }

    private Integer addDataMissing(String stockCode, String dateStr, List<StockDataDailyAll> stockDataDailies, LocalDate tradeDate, int count) {
        for (int j = 1; j < stockDataDailies.size(); j++) {
            StockDataDailyAll up = stockDataDailies.get(j - 1);
            StockDataDailyAll current = stockDataDailies.get(j);
            LocalDate nextTradeDate = tradingDateMapper.findTradingDateSqlServerStockCode(up.getTradeDate(), 0);
            if (nextTradeDate.compareTo(current.getTradeDate()) < 0) {
                StockDataDailyAll copyData = new StockDataDailyAll();
                copyData.setStockCode(stockCode);
                copyData.setTradeDate(nextTradeDate);
                copyData.setCloseForead(up.getCloseForead());
                copyData.setCloseBackad(up.getCloseBackad());
                copyData.setClose(up.getClose());
                copyData.setClosePrevious(up.getClosePrevious());
                stockDataDailies.add(copyData);
                ListUtils.sort(stockDataDailies, true, "tradeDate");
            }
        }

        if (stockDataDailies.size() > 0) {
            StockDataDailyAll lastStockDataDaily = stockDataDailies.get(stockDataDailies.size() - 1);
            LocalDate lastDate = lastStockDataDaily.getTradeDate();
            while (lastDate.compareTo(tradeDate) <= 0 && count == 200) {
                lastDate = tradingDateMapper.findTradingDateSqlServerStockCode(lastDate, 0);
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
            int tradeDateCount = tradingDateMapper.isTradeDate(tradeDate);
            if (tradeDateCount > 0) {
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
    public List<MissingStockDataItem> checkMissingData(String stockCode, LocalDate startDate, LocalDate endDate) {
        List<MissingStockDataItem> result = new ArrayList<>();

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

        for (PubStockInfo stock : allStocks) {
            String code = stock.getSymbol();
            if (stock.getDelistDate() != null && stock.getDelistDate().isBefore(startDate)) {
                continue;
            }
            if (stock.getListingDate() != null && stock.getListingDate().isAfter(endDate)) {
                continue;
            }

            LocalDate effectiveStart = startDate;
            LocalDate effectiveEnd = endDate;
            if (stock.getListingDate() != null && stock.getListingDate().isAfter(effectiveStart)) {
                effectiveStart = stock.getListingDate();
            }
            if (stock.getDelistDate() != null && stock.getDelistDate().isBefore(effectiveEnd)) {
                effectiveEnd = stock.getDelistDate();
            }

            Set<LocalDate> existingDailyDates = new HashSet<>(stockDataDailyAllMapper.findTradeDatesWithDailyData(code, effectiveStart, effectiveEnd));
            Set<LocalDate> existingMinuteDates = new HashSet<>(stockMinuteDataMapper.findTradeDatesWithMinuteData(code, effectiveStart, effectiveEnd));

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

    private void setClosePrice(List<StockDataDailyAll> afterList, List<StockDataDailyAll> beforeList, List<StockDataDailyAll> stockDataDailies) {
        Map<LocalDate, BigDecimal> afterMap = new HashMap<>();
        for (StockDataDailyAll item : afterList) {
            afterMap.put(item.getTradeDate(), item.getClose());
        }
        Map<LocalDate, BigDecimal> beforeMap = new HashMap<>();
        for (StockDataDailyAll item : beforeList) {
            beforeMap.put(item.getTradeDate(), item.getClose());
        }

        ListUtils.sort(stockDataDailies, true, "tradeDate");
        for (StockDataDailyAll stockDataDaily : stockDataDailies) {
            BigDecimal afterClose = afterMap.get(stockDataDaily.getTradeDate());
            BigDecimal beforeClose = beforeMap.get(stockDataDaily.getTradeDate());
            stockDataDaily.setCloseForead(beforeClose == null || beforeClose.compareTo(BigDecimal.ZERO) == 0 ? stockDataDaily.getClose() : beforeClose);
            stockDataDaily.setCloseBackad(afterClose);
        }
    }
}
