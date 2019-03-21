package com.vladmihalcea.hibernate.type.tsvector.nondynamicparameters;

import org.postgresql.util.PGobject;

import java.sql.SQLException;

/**
 * @author Lukman Adekunle
 */
public class PGTSVector extends PGobject {

    public PGTSVector() {
        setType("tsvector");
    }

    @Override
    public void setValue(String value) {
        try {
            super.setValue(value);
        } catch (SQLException e) {
            throw new IllegalArgumentException("value : " + value + "cannot be set");
        }
    }

}
