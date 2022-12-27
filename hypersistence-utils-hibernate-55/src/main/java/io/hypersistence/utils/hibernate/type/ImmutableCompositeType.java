package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.EntityMode;
import org.hibernate.FetchMode;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.jdbc.Size;
import org.hibernate.engine.spi.*;
import org.hibernate.internal.util.collections.ArrayHelper;
import org.hibernate.type.BasicType;
import org.hibernate.type.CompositeType;
import org.hibernate.type.ForeignKeyDirection;
import org.hibernate.type.Type;
import org.hibernate.type.descriptor.java.IncomparableComparator;
import org.hibernate.usertype.CompositeUserType;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Very convenient base class for implementing immutable object types using Hibernate {@link CompositeUserType}.
 * <p>
 * The {@link ImmutableCompositeType} implements the {@link Type} interface too, so you can pass all
 * types extending the {@link ImmutableCompositeType} to the {@link org.hibernate.query.NativeQuery#addScalar(String, Type)}
 * method to fix the <a href="https://vladmihalcea.com/hibernate-no-dialect-mapping-for-jdbc-type/">No Dialect mapping for JDBC type</a> issues.
 *
 * @author Vlad Mihalcea
 */
public abstract class ImmutableCompositeType<T> implements CompositeUserType, CompositeType, BasicType {

    private final Configuration configuration;

    private final Class<T> clazz;

    private final List<Method> clazzMethods;

    /**
     * Initialization constructor taking the {@link Class}
     * and using the default {@link Configuration} object.
     *
     * @param clazz the entity attribute {@link Class} type to be handled
     */
    protected ImmutableCompositeType(Class<T> clazz) {
        this(clazz, Configuration.INSTANCE);
    }

    /**
     * Initialization constructor taking the {@link Class} and {@link Configuration} objects.
     *
     * @param clazz         the entity attribute {@link Class} type to be handled
     * @param configuration custom {@link Configuration} object.
     */
    protected ImmutableCompositeType(Class<T> clazz, Configuration configuration) {
        this.clazz = clazz;
        this.configuration = configuration;
        this.clazzMethods = Collections.unmodifiableList(Arrays.asList(clazz.getMethods()));
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
     * @param rs      JDBC {@link ResultSet}
     * @param names   database column name
     * @param session current Hibernate {@link org.hibernate.Session}
     * @param owner   current Hibernate {@link SessionFactoryImplementor}
     * @return column value
     * @throws SQLException in case of failure
     */
    protected abstract T get(ResultSet rs, String[] names,
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
    public Object nullSafeGet(ResultSet rs, String[] names,
                              SharedSessionContractImplementor session, Object owner) throws SQLException {
        return get(rs, names, session, owner);
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
    public Object deepCopy(Object value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Object o, SharedSessionContractImplementor session) {
        return (Serializable) o;
    }

    @Override
    public Object assemble(Serializable cached, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        return cached;
    }

    @Override
    public Object replace(Object o, Object target, SharedSessionContractImplementor session, Object owner) {
        return o;
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
        return true;
    }

    @Override
    public int getColumnSpan(Mapping mapping) throws MappingException {
        return getPropertyTypes().length;
    }

    @Override
    public int[] sqlTypes(Mapping mapping) throws MappingException {
        List<Integer> sqlTypes= new ArrayList<>();
        Type[] types = getPropertyTypes();
        for (int i = 0; i < types.length; i++) {
            sqlTypes.addAll(
                Arrays.stream(types[i].sqlTypes(mapping)).boxed().collect(Collectors.toList())
            );
        }
        return sqlTypes.stream().mapToInt(i->i).toArray();
    }

    @Override
    public Size[] dictatedSizes(Mapping mapping) throws MappingException {
        return new Size[]{new Size()};
    }

    @Override
    public Size[] defaultSizes(Mapping mapping) throws MappingException {
        return dictatedSizes(mapping);
    }

    @Override
    public Class getReturnedClass() {
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
    public Object nullSafeGet(ResultSet rs, String name, SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
        return get(rs, new String[]{name}, session, owner);
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
        return disassemble(value, session);
    }

    @Override
    public void beforeAssemble(Serializable cached, SharedSessionContractImplementor session) {

    }

    @Override
    public Object hydrate(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
        return nullSafeGet(rs, names, session, owner);
    }

    @Override
    public Object resolve(Object value, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        return value;
    }

    @Override
    public Object semiResolve(Object value, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        return value;
    }

    @Override
    public Type getSemiResolvedType(SessionFactoryImplementor factory) {
        return this;
    }

    @Override
    public Object replace(Object original, Object target, SharedSessionContractImplementor session, Object owner, Map copyCache) throws HibernateException {
        return replace(original, target, session, owner);
    }

    @Override
    public Object replace(Object original, Object target, SharedSessionContractImplementor session, Object owner, Map copyCache, ForeignKeyDirection foreignKeyDirection) throws HibernateException {
        return replace(original, target, session, owner);
    }

    @Override
    public boolean[] toColumnNullness(Object value, Mapping mapping) {
        return value == null ? ArrayHelper.FALSE : ArrayHelper.TRUE;
    }

    @Override
    public String[] getRegistrationKeys() {
        return new String[]{
            getName()
        };
    }

    @Override
    public Type[] getSubtypes() {
        return getPropertyTypes();
    }

    @Override
    public boolean[] getPropertyNullability() {
        return new boolean[]{false, false};
    }

    @Override
    public Object[] getPropertyValues(Object component, SharedSessionContractImplementor session) throws HibernateException {
        return new Object[]{getPropertyValue(component, 0), getPropertyValue(component, 1)};
    }

    @Override
    public Object[] getPropertyValues(Object component, EntityMode entityMode) throws HibernateException {
        return new Object[]{getPropertyValue(component, 0), getPropertyValue(component, 1)};
    }

    @Override
    public Object getPropertyValue(Object component, int index, SharedSessionContractImplementor session) throws HibernateException {
        return getPropertyValue(component, index);
    }

    @Override
    public void setPropertyValues(Object component, Object[] values, EntityMode entityMode) throws HibernateException {
        throw new HibernateException("Calling setPropertyValues is illegal on on " + clazz.getName() + " because it's an immutable object!");
    }

    @Override
    public CascadeStyle getCascadeStyle(int index) {
        return CascadeStyles.NONE;
    }

    @Override
    public FetchMode getFetchMode(int index) {
        return FetchMode.DEFAULT;
    }

    @Override
    public boolean isMethodOf(Method method) {
        return clazzMethods.contains(method);
    }

    @Override
    public boolean isEmbedded() {
        return false;
    }

    @Override
    public boolean hasNotNullProperty() {
        return true;
    }
}