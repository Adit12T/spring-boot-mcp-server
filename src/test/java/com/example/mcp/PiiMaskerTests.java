package com.example.mcp;

import com.example.mcp.security.PiiMasker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PiiMaskerTests {

    @Test
    void masksEmailAddresses() {
        assertEquals("Lookup failed for j***@example.com",
            PiiMasker.mask("Lookup failed for jane.doe@example.com"));
    }

    @Test
    void masksLongNumbersButKeepsTheLastFourDigits() {
        assertEquals("Card ****1111 declined", PiiMasker.mask("Card 4111111111111111 declined"));
    }

    @Test
    void masksCardNumbersWrittenInGroupsOfFour() {
        assertEquals("Card ****1111 declined", PiiMasker.mask("Card 4111 1111 1111 1111 declined"));
        assertEquals("Card ****1111 declined", PiiMasker.mask("Card 4111-1111-1111-1111 declined"));
        String dates = "Window 2026-01-05 to 2026-01-06, batch 1234 5678";
        assertEquals(dates, PiiMasker.mask(dates));
    }

    @Test
    void leavesOrdinaryTechnicalTextAlone() {
        String text = "Cannot invoke \"demo.orders.DiscountService$Coupon.percentOff()\" at line 17, order 10482";
        assertEquals(text, PiiMasker.mask(text));
        assertNull(PiiMasker.mask(null));
    }
}
