package com.vladmihalcea.hibernate.type.tsvector;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author bitluke
 */
public class TSVector implements Serializable {

    private final String tokens;

    public TSVector(String tokens) {
        this.tokens = tokens;
    }

    public String getTokens() {
        return tokens;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TSVector)) return false;
        TSVector tsVector = (TSVector) o;
        return getTokens().equals(tsVector.getTokens());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getTokens());
    }
}
