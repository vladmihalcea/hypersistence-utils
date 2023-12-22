package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.hibernate.type.json.internal.JdbcTypeSetter;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.common.ReflectionUtils;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.internal.util.IndexedConsumer;
import org.hibernate.metamodel.mapping.BasicValuedMapping;
import org.hibernate.metamodel.mapping.JdbcMapping;
import org.hibernate.metamodel.mapping.MappingType;
import org.hibernate.metamodel.model.domain.DomainType;
import org.hibernate.query.BindableType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.internal.BasicTypeImpl;
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
public abstract class MutableType<T, JDBC extends JdbcType, JAVA extends JavaType<T>> implements UserType<T>, BindableType<T>, DomainType<T>, BasicValuedMapping {

    private final Class<T> clazz;

    private final JDBC jdbcTypeDescriptor;
    private final JAVA javaTypeDescriptor;
    private final JdbcMapping jdbcMapping;

    private final Configuration configuration;

    /**
     * Initialization constructor taking the {@link Class}
     * and using the default {@link Configuration} object.
     *
     * @param clazz The class returned by {@link UserType#nullSafeGet(ResultSet, int, SharedSessionContractImplementor, Object)}.
     * @param jdbcTypeDescriptor the JDBC type descriptor
     * @param javaTypeDescriptor the Java type descriptor
     */
    public MutableType(Class<T> clazz, JDBC jdbcTypeDescriptor, JAVA javaTypeDescriptor) {
        this(clazz, jdbcTypeDescriptor, javaTypeDescriptor, Configuration.INSTANCE);
    }

    /**
     * Initialization constructor taking the {@link Class}
     * and using the provided {@link Configuration} object.
     *
     * @param clazz The class returned by {@link UserType#nullSafeGet(ResultSet, int, SharedSessionContractImplementor, Object)}.
     * @param jdbcTypeDescriptor the JDBC type descriptor
     * @param javaTypeDescriptor the Java type descriptor
     * @param configuration the configuration
     */
    public MutableType(Class<T> clazz, JDBC jdbcTypeDescriptor, JAVA javaTypeDescriptor, Configuration configuration) {
        this.clazz = clazz;
        this.jdbcTypeDescriptor = jdbcTypeDescriptor;
        this.javaTypeDescriptor = javaTypeDescriptor;
        this.configuration = configuration;
        this.jdbcMapping = new BasicTypeImpl<T>(javaTypeDescriptor, jdbcTypeDescriptor);

        ReflectionUtils.getMethodOrNull(javaTypeDescriptor, "setJdbcType");
        if(javaTypeDescriptor instanceof JdbcTypeSetter) {
            JdbcTypeSetter jdbcTypeSetter = (JdbcTypeSetter) javaTypeDescriptor;
            jdbcTypeSetter.setJdbcType(jdbcTypeDescriptor);
        }
    }

    public JDBC getJdbcTypeDescriptor() {
        return jdbcTypeDescriptor;
    }

    public JAVA getJavaTypeDescriptor() {
        return javaTypeDescriptor;
    }

    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean equals(T x, T y) {
        return javaTypeDescriptor.areEqual(x, y);
    }

    @Override
    public int hashCode(T x) {
        return javaTypeDescriptor.extractHashCode(x);
    }

    @Override
    public T nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws
        SQLException {
        return jdbcTypeDescriptor.getExtractor(javaTypeDescriptor).extract(rs, position, session);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        jdbcTypeDescriptor.getBinder(javaTypeDescriptor).bind(st, (T) value, index, session);
    }

    @Override
    public T deepCopy(T value) {
        return javaTypeDescriptor.getMutabilityPlan().deepCopy(value);
    }

    @Override
    public boolean isMutable() {
        return javaTypeDescriptor.getMutabilityPlan().isMutable();
    }

    @Override
    public Serializable disassemble(T value) {
        return javaTypeDescriptor.getMutabilityPlan().disassemble(value, null);
    }

    @Override
    public T assemble(Serializable cached, Object owner) {
        return javaTypeDescriptor.getMutabilityPlan().assemble(cached, null);
    }

    @Override
    public T replace(T detached, T managed, Object owner) {
		if (!isMutable() || (managed != null && equals(detached, managed))) {
			return detached;
		}

        return deepCopy(detached);
    }

    @Override
    public int getSqlType() {
        return jdbcTypeDescriptor.getJdbcTypeCode();
    }

    @Override
    public Class<T> returnedClass() {
        return clazz;
    }

    @Override
    public Class<T> getBindableJavaType() {
        return clazz;
    }

    @Override
    public JavaType<T> getExpressibleJavaType() {
        return javaTypeDescriptor;
    }

    @Override
    public JdbcMapping getJdbcMapping() {
        return jdbcMapping;
    }

    @Override
    public MappingType getMappedType() {
        return jdbcMapping;
    }

    @Override
    public Object disassemble(Object value, SharedSessionContractImplementor session) {
        return disassemble((T) value);
    }

    @Override
    public <X, Y> int forEachDisassembledJdbcValue(Object value, int offset, X x, Y y, JdbcValuesBiConsumer<X, Y> valuesConsumer, SharedSessionContractImplementor session) {
        valuesConsumer.consume(offset, x, y, value, jdbcMapping);
        return getJdbcTypeCount();
    }

    @Override
    public int forEachJdbcType(int offset, IndexedConsumer<JdbcMapping> action) {
        action.accept(offset, jdbcMapping);
        return getJdbcTypeCount();
    }
}
