package io.hypersistence.utils.hibernate.type.basic;

import java.io.Serializable;
import java.util.Objects;

/**
 * The {@link MacAddress} object type is used to represent a PostgreSQL {@code macaddr} (MAC address) column.
 * <p>
 * The value is stored using the canonical representation returned by PostgreSQL (e.g. {@code 08:00:2b:01:02:03}).
 */
public class MacAddress implements Serializable {

    private final String address;

    public MacAddress(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(address, MacAddress.class.cast(o).address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    @Override
    public String toString() {
        return address;
    }
}
