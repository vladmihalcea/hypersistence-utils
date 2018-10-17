package com.vladmihalcea.hibernate.type.basic;

import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;

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

        Inet inet = (Inet) o;

        return address != null ? address.equals(inet.address) : inet.address == null;
    }

    @Override
    public int hashCode() {
        return address != null ? address.hashCode() : 0;
    }

    public InetAddress toInetAddress() {
        try {
            String host = address.replaceAll("\\/.*$", "");
            return Inet4Address.getByName(host);
        } catch (UnknownHostException e) {
            throw new IllegalStateException(e);
        }
    }
}
