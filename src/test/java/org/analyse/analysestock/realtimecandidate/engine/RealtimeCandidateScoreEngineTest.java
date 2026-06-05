package org.analyse.analysestock.realtimecandidate.engine;

import org.analyse.analysestock.analysis.entity.PubStockInfo;
import org.analyse.analysestock.analysis.entity.StockDataDailyAll;
import org.analyse.analysestock.analysis.entity.StockMinuteData;
import org.analyse.analysestock.realtimecandidate.config.CostConfig;
import org.analyse.analysestock.realtimecandidate.config.RealtimeStrategyConfig;
import org.analyse.analysestock.realtimecandidate.dto.RealtimeCandidateScoreRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class RealtimeCandidateScoreEngineTest {

    @Test
    public void testCalculate() {
        RealtimeCandidateScoreEngine engine = new RealtimeCandidateScoreEngine();
        LocalDate tradeDate = LocalDate.of(2026, 6, 5);
        
        List<PubStockInfo> securityInfos = new ArrayList<>();
        PubStockInfo stock1 = new PubStockInfo();
        stock1.setSymbol("000001");
        stock1.setShortName("平安银行");
        stock1.setSector("银行");
        stock1.setListingDate(LocalDate.of(2000, 1, 1));
        stock1.setStStatus("0");
        stock1.setListedStatus("1");
        securityInfos.add(stock1);

        List<StockDataDailyAll> dailyBars = new ArrayList<>();
        // T-1 日
        StockDataDailyAll d1 = new StockDataDailyAll();
        d1.setStockCode("000001");
        d1.setTradeDate(tradeDate.minusDays(1));
        d1.setClose(new BigDecimal("10.00"));
        d1.setAmount(new BigDecimal("200000000"));
        dailyBars.add(d1);
        // 补足 20 日以便计算均值和波动率 (简化)
        for (int i = 2; i <= 25; i++) {
            StockDataDailyAll d = new StockDataDailyAll();
            d.setStockCode("000001");
            d.setTradeDate(tradeDate.minusDays(i));
            d.setClose(new BigDecimal("10.00"));
            d.setAmount(new BigDecimal("200000000"));
            dailyBars.add(d);
        }

        List<StockMinuteData> minuteBars = new ArrayList<>();
        // D 日分钟线
        minuteBars.add(createMin("000001", tradeDate, 1400, "10.00", 1000L, "10000"));
        minuteBars.add(createMin("000001", tradeDate, 1415, "10.10", 1000L, "10100"));
        minuteBars.add(createMin("000001", tradeDate, 1430, "10.20", 1000L, "10200"));
        // 补一些 D-1 日分钟线用于短样本
        minuteBars.add(createMin("000001", tradeDate.minusDays(1), 1430, "10.00", 1000L, "10000"));
        // 补 D 日早盘用于短样本卖出 (T+1)
        minuteBars.add(createMin("000001", tradeDate, 930, "10.10", 1000L, "10100"));
        minuteBars.add(createMin("000001", tradeDate, 945, "10.10", 1000L, "10100"));

        RealtimeStrategyConfig strategyConfig = new RealtimeStrategyConfig();
        CostConfig costConfig = new CostConfig();

        List<RealtimeCandidateScoreRecord> result = engine.calculateWithEntities(
                tradeDate, dailyBars, minuteBars, securityInfos, strategyConfig, costConfig);

        assertNotNull(result);
        assertFalse(result.isEmpty());
        RealtimeCandidateScoreRecord record = result.get(0);
        assertEquals("000001", record.getStockCode());
        // tailMomentum = 10.20 / 10.00 - 1 = 0.02
        assertEquals(0, new BigDecimal("0.02").compareTo(record.getTailMomentum()));
        assertTrue(record.getFinalScore().compareTo(BigDecimal.ZERO) > 0);
    }

    private StockMinuteData createMin(String code, LocalDate date, int time, String price, Long vol, String amt) {
        StockMinuteData m = new StockMinuteData();
        m.setStockCode(Integer.valueOf(code));
        m.setTradeDate(date);
        m.setTime(time);
        m.setPrice(new BigDecimal(price));
        m.setMinuteVolume(vol);
        m.setMinuteAmount(new BigDecimal(amt));
        m.setHighPrice(new BigDecimal(price));
        m.setLowPrice(new BigDecimal(price));
        return m;
    }
}
