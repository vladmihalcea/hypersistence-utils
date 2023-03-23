package io.hypersistence.utils.hibernate.type.basic;

import org.hibernate.HibernateException;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

/**
 * The {@link Inet} object type is used to represent an IP address.
 * <p>
 * For more details about how to use it,
 * check out <a href="https://vladmihalcea.com/postgresql-inet-type-hibernate/">this article</a>
 * on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class Inet implements Serializable {

    private final String address;

    public Inet(String address) {
        this.address = address;
    }

    public String getAddress() {
        return address;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        return Objects.equals(address, Inet.class.cast(o).address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address);
    }

    /**
     * Get the associated {@link InetAddress} for the current {@link #address}.
     *
     * @return the associated {@link InetAddress}
     */
    public InetAddress toInetAddress() {
        try {
            String host = address.replaceAll("\\/.*$", "");
            return Inet4Address.getByName(host);
        } catch (UnknownHostException e) {
            throw new HibernateException(
                new IllegalArgumentException(e)
            );
        }
    }
}
