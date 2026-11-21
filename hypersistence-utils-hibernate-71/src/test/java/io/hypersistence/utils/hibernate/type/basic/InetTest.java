package io.hypersistence.utils.hibernate.type.basic;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class InetTest {

    private final List<Inet> addresses = List.of(
        new Inet(null),
        new Inet("10.0.0.0/16"),
        new Inet("10.0.0.0/24"),
        new Inet("192.168.0.10"),
        new Inet("192.168.0.2"),
        new Inet("2001:0db8::1"),
        new Inet("2001:db8::1"),
        new Inet("2001:db8::2")
    );

    @Test
    public void testNaturalOrder() {
        List<Inet> sorted = new ArrayList<>(addresses);
        Collections.reverse(sorted);
        Collections.sort(sorted);

        assertEquals(addresses, sorted);
    }

    @Test
    public void testComparisonIsConsistentWithEquals() {
        for (Inet left : addresses) {
            assertEquals(0, left.compareTo(new Inet(left.getAddress())));
            for (Inet right : addresses) {
                assertEquals(left.equals(right), left.compareTo(right) == 0);
                assertEquals(-Integer.signum(left.compareTo(right)), Integer.signum(right.compareTo(left)));
            }
        }
    }

    @Test
    public void testNullInetIsRejected() {
        assertThrows(NullPointerException.class, () -> new Inet("192.168.0.1").compareTo(null));
        assertThrows(NullPointerException.class, () -> new Inet(null).compareTo(null));
    }
}
