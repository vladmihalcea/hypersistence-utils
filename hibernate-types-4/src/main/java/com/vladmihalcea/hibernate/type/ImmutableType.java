package com.vladmihalcea.hibernate.type;

import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Very convenient base class for implementing immutable object types using Hibernate {@link UserType}.
 *
 * @author Vlad Mihalcea
 */
public abstract class ImmutableType<T> implements UserType {

    private final Configuration configuration;

    private final Class<T> clazz;

    /**
     * Initialization constructor taking the {@link Class}
     * and using the default {@link Configuration} object.
     *
     * @param clazz the entity attribute {@link Class} type to be handled
     */
    protected ImmutableType(Class<T> clazz) {
        this.clazz = clazz;
        this.configuration = Configuration.INSTANCE;
    }

    /**
     * Initialization constructor taking the {@link Class} and {@link Configuration} objects.
     *
     * @param clazz the entity attribute {@link Class} type to be handled
     * @param configuration custom {@link Configuration} object.
     */
    protected ImmutableType(Class<T> clazz, Configuration configuration) {
        this.clazz = clazz;
        this.configuration = configuration;
    }

    /**
     * Get the current {@link Configuration} object.
     * @return the current {@link Configuration} object.
     */
    protected Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names,
                              SessionImplementor session, Object owner) throws SQLException {
        return get(rs, names, session, owner);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index,
                            SessionImplementor session) throws SQLException {
        set(st, clazz.cast(value), index, session);
    }

    protected abstract T get(ResultSet rs, String[] names,
                             SessionImplementor session, Object owner) throws SQLException;

    protected abstract void set(PreparedStatement st, T value, int index,
                                SessionImplementor session) throws SQLException;


    @Override
    public Class<T> returnedClass() {
        return clazz;
    }

    @Override
    public boolean equals(Object x, Object y) {
        return (x == y) || (x != null && x.equals(y));
    }

    @Override
    public int hashCode(Object x) {
        return x.hashCode();
    }

    @Override
    public Object deepCopy(Object value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Object o) {
        return (Serializable) o;
    }

    @Override
    public Object assemble(Serializable cached, Object owner) {
        return cached;
    }

    @Override
    public Object replace(Object o, Object target, Object owner) {
        return o;
    }
}