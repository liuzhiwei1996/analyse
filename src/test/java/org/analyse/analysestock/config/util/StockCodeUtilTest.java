package org.analyse.analysestock.config.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StockCodeUtilTest {

    @Test
    void shouldAddShenzhenPrefix() {
        assertEquals("A000001", StockCodeUtil.addMarketPrefix("000001"));
        assertEquals("A301611", StockCodeUtil.addMarketPrefix("301611"));
    }

    @Test
    void shouldAddShanghaiPrefix() {
        assertEquals("B600522", StockCodeUtil.addMarketPrefix("600522"));
        assertEquals("B688981", StockCodeUtil.addMarketPrefix("688981"));
    }

    @Test
    void shouldKeepExistingPrefix() {
        assertEquals("A301611", StockCodeUtil.addMarketPrefix("a301611"));
        assertEquals("B600522", StockCodeUtil.addMarketPrefix("B600522"));
    }

    @Test
    void shouldRejectInvalidOrUnsupportedCode() {
        assertThrows(IllegalArgumentException.class, () -> StockCodeUtil.addMarketPrefix(null));
        assertThrows(IllegalArgumentException.class, () -> StockCodeUtil.addMarketPrefix("60052"));
        assertThrows(IllegalArgumentException.class, () -> StockCodeUtil.addMarketPrefix("920001"));
        assertThrows(IllegalArgumentException.class, () -> StockCodeUtil.addMarketPrefix("A600522"));
    }
}
