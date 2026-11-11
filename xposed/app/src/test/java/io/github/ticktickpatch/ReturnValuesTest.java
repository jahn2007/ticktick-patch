package io.github.ticktickpatch;

import org.junit.Test;

import java.time.Instant;
import java.util.Date;

import static io.github.ticktickpatch.ReturnValues.Kind.*;
import static org.junit.Assert.*;

public class ReturnValuesTest {
    @Test
    public void primitiveAndBoxedValuesKeepTheirTypes() throws Exception {
        for (Class<?> type : new Class<?>[]{boolean.class, Boolean.class}) {
            assertEquals(Boolean.TRUE, ReturnValues.forType(TRUE, type).create());
            assertEquals(Boolean.FALSE, ReturnValues.forType(FALSE, type).create());
        }
        for (Class<?> type : new Class<?>[]{int.class, Integer.class}) {
            assertEquals(Integer.valueOf(1), ReturnValues.forType(PRO_TYPE, type).create());
        }
        for (Class<?> type : new Class<?>[]{long.class, Long.class}) {
            assertEquals(Long.valueOf(ReturnValues.PRO_END_MS), ReturnValues.forType(END_TIME, type).create());
        }
    }

    @Test
    public void mutableDatesAreNotShared() throws Exception {
        ReturnValues.Factory factory = ReturnValues.forType(END_DATE, Date.class);
        Date first = (Date) factory.create();
        first.setTime(0);
        assertEquals(ReturnValues.PRO_END_MS, ((Date) factory.create()).getTime());
        assertNotSame(first, factory.create());
    }

    @Test
    public void dateRepresentationsAgree() throws Exception {
        assertEquals(Instant.parse("9999-05-20T00:00:00Z").toEpochMilli(), ReturnValues.PRO_END_MS);
        assertEquals("9999-05-20T00:00:00.000+0000", ReturnValues.forType(END_DATE, String.class).create());
    }

    @Test
    public void customHostDateUsesLongConstructor() throws Exception {
        CustomDate result = (CustomDate) ReturnValues.forType(END_DATE, CustomDate.class).create();
        assertEquals(ReturnValues.PRO_END_MS, result.time);
    }

    @Test
    public void incompatibleReturnsAreRejectedBeforeHooking() {
        assertThrows(IllegalArgumentException.class, () -> ReturnValues.forType(TRUE, int.class));
        assertThrows(IllegalArgumentException.class, () -> ReturnValues.forType(PRO_TYPE, long.class));
        assertThrows(IllegalArgumentException.class, () -> ReturnValues.forType(END_TIME, int.class));
        assertThrows(IllegalArgumentException.class, () -> ReturnValues.forType(END_DATE, Object.class));
        assertThrows(IllegalArgumentException.class, () -> ReturnValues.forType(NULL, void.class));
        assertThrows(IllegalArgumentException.class, () -> ReturnValues.forType(NULL, boolean.class));
        assertThrows(NoSuchMethodException.class, () -> ReturnValues.forType(END_DATE, NoDate.class));
    }

    @Test
    public void referenceNullIsSupported() throws Exception {
        assertNull(ReturnValues.forType(NULL, Date.class).create());
    }

    public static final class CustomDate {
        final long time;
        private CustomDate(long time) { this.time = time; }
    }

    public static final class NoDate {}
}
