package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.Mapping;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.metamodel.model.domain.BasicDomainType;
import org.hibernate.type.ForeignKeyDirection;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.java.IncomparableComparator;
import org.hibernate.usertype.EnhancedUserType;
import org.hibernate.usertype.UserType;

import java.io.Serializable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Map;

/**
 * Very convenient base class for implementing immutable object types using Hibernate {@link UserType}.
 * <p>
 * The {@link ImmutableType} implements the {@link Type} interface too, so you can pass all
 * types extending the {@link ImmutableType} to the {@link org.hibernate.query.NativeQuery#addScalar(String, BasicDomainType)}
 * method to fix the <a href="https://vladmihalcea.com/hibernate-no-dialect-mapping-for-jdbc-type/">No Dialect mapping for JDBC type</a> issues.
 *
 * @author Vlad Mihalcea
 */
public abstract class ImmutableType<T> implements UserType<T>, Type, EnhancedUserType<T> {

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
     * @param clazz         the entity attribute {@link Class} type to be handled
     * @param configuration custom {@link Configuration} object.
     */
    protected ImmutableType(Class<T> clazz, Configuration configuration) {
        this.clazz = clazz;
        this.configuration = configuration;
    }

    /**
     * Get the current {@link Configuration} object.
     *
     * @return the current {@link Configuration} object.
     */
    protected Configuration getConfiguration() {
        return configuration;
    }

    /**
     * Get the column value from the JDBC {@link ResultSet}.
     *
     * @param rs       JDBC {@link ResultSet}
     * @param position database column position
     * @param session  current Hibernate {@link org.hibernate.Session}
     * @param owner    current Hibernate {@link SessionFactoryImplementor}
     * @return column value
     * @throws SQLException in case of failure
     */
    protected abstract T get(ResultSet rs, int position,
                             SharedSessionContractImplementor session, Object owner) throws SQLException;

    /**
     * Set the column value on the provided JDBC {@link PreparedStatement}.
     *
     * @param st      JDBC {@link PreparedStatement}
     * @param value   database column value
     * @param index   database column index
     * @param session current Hibernate {@link org.hibernate.Session}
     * @throws SQLException in case of failure
     */
    protected abstract void set(PreparedStatement st, T value, int index,
                                SharedSessionContractImplementor session) throws SQLException;

    /* Methods inherited from the {@link UserType} interface */

    @Override
    public T nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        return get(rs, position, session, owner);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index,
                            SharedSessionContractImplementor session) throws SQLException {
        set(st, clazz.cast(value), index, session);
    }

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
    public T deepCopy(Object value) {
        return (T) value;
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
    public T assemble(Serializable cached, Object owner) {
        return (T) cached;
    }

    @Override
    public T replace(Object o, Object target, Object owner) {
        return (T) o;
    }

    /* Methods inherited from the {@link Type} interface */

    @Override
    public boolean isAssociationType() {
        return false;
    }

    @Override
    public boolean isCollectionType() {
        return false;
    }

    @Override
    public boolean isEntityType() {
        return false;
    }

    @Override
    public boolean isAnyType() {
        return false;
    }

    @Override
    public boolean isComponentType() {
        return false;
    }

    @Override
    public int getColumnSpan(Mapping mapping) throws MappingException {
        return 1;
    }

    @Override
    public Class<T> getReturnedClass() {
        return returnedClass();
    }

    @Override
    public boolean isSame(Object x, Object y) throws HibernateException {
        return equals(x, y);
    }

    @Override
    public boolean isEqual(Object x, Object y) throws HibernateException {
        return equals(x, y);
    }

    @Override
    public boolean isEqual(Object x, Object y, SessionFactoryImplementor factory) throws HibernateException {
        return equals(x, y);
    }

    @Override
    public int getHashCode(Object x) throws HibernateException {
        return hashCode(x);
    }

    @Override
    public int getHashCode(Object x, SessionFactoryImplementor factory) throws HibernateException {
        return hashCode(x);
    }

    @Override
    public int compare(Object x, Object y, SessionFactoryImplementor sessionFactoryImplementor) {
        return compare(x, y);
    }

    @Override
    public int compare(Object x, Object y) {
        return IncomparableComparator.INSTANCE.compare(x, y);
    }

    @Override
    public final boolean isDirty(Object old, Object current, SharedSessionContractImplementor session) {
        return isDirty(old, current);
    }

    @Override
    public final boolean isDirty(Object old, Object current, boolean[] checkable, SharedSessionContractImplementor session) {
        return checkable[0] && isDirty(old, current);
    }

    protected final boolean isDirty(Object old, Object current) {
        return !isSame(old, current);
    }

    @Override
    public boolean isModified(Object dbState, Object currentState, boolean[] checkable, SharedSessionContractImplementor session) throws HibernateException {
        return isDirty(dbState, currentState);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, boolean[] settable, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        set(st, returnedClass().cast(value), index, session);
    }

    @Override
    public String toLoggableString(Object value, SessionFactoryImplementor factory) throws HibernateException {
        return String.valueOf(value);
    }

    @Override
    public String getName() {
        return getClass().getSimpleName();
    }

    @Override
    public Object deepCopy(Object value, SessionFactoryImplementor factory) throws HibernateException {
        return deepCopy(value);
    }

    @Override
    public Serializable disassemble(Object value, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        return disassemble(value);
    }

    @Override
    public Object assemble(Serializable cached, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        return assemble(cached, session);
    }

    @Override
    public void beforeAssemble(Serializable cached, SharedSessionContractImplementor session) {

    }

    @Override
    public Object replace(Object original, Object target, SharedSessionContractImplementor session, Object owner, Map copyCache) throws HibernateException {
        return replace(original, target, owner);
    }

    @Override
    public Object replace(Object original, Object target, SharedSessionContractImplementor session, Object owner, Map copyCache, ForeignKeyDirection foreignKeyDirection) throws HibernateException {
        return replace(original, target, owner);
    }

    @Override
    public boolean[] toColumnNullness(Object value, Mapping mapping) {
        return value == null ? ArrayHelper.FALSE : ArrayHelper.TRUE;
    }

    @Override
    public int[] getSqlTypeCodes(Mapping mapping) throws MappingException {
        return new int[]{getSqlType()};
    }

    @Override
    public String toSqlLiteral(T o) {
        return o != null ?
            String.format(Locale.ROOT, "'%s'", o) :
            null;
    }

    @Override
    public String toString(T o) throws HibernateException {
        return o != null ? o.toString() : null;
    }
}
