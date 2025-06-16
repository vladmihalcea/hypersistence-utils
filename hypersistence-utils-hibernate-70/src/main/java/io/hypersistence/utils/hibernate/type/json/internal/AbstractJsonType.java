package io.hypersistence.utils.hibernate.type.json.internal;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.type.descriptor.converter.spi.BasicValueConverter;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.java.JavaType;

import io.hypersistence.utils.hibernate.type.MutableDynamicParameterizedType;
import io.hypersistence.utils.hibernate.type.util.Configuration;

public abstract class AbstractJsonType<T> extends MutableDynamicParameterizedType<T, AbstractJsonJdbcTypeDescriptor, AbstractClassJavaType<T>> {
	@SuppressWarnings("unchecked")
  private final class BasicValueConverterImplementation implements BasicValueConverter<T, Object> {
		@Override
		public @Nullable T toDomainValue(@Nullable Object relationalForm) {
			return (T)relationalForm;
		}

		@Override
		public @Nullable Object toRelationalValue(@Nullable T domainForm) {
			return domainForm;
		}

		@Override
		public JavaType<T> getDomainJavaType() {
			return getJavaTypeDescriptor();
		}

		@Override
		public JavaType<Object> getRelationalJavaType() {
			return (JavaType<Object>) getJavaTypeDescriptor();
		}
	}

	protected AbstractJsonType(Class<T> returnedClass, AbstractJsonJdbcTypeDescriptor jdbcTypeDescriptor,
  		AbstractClassJavaType<T> javaTypeDescriptor, Configuration configuration) {
      super(returnedClass, jdbcTypeDescriptor, javaTypeDescriptor, configuration);
  }

  protected AbstractJsonType(Class<T> returnedClass, AbstractJsonJdbcTypeDescriptor jdbcTypeDescriptor,
  		AbstractClassJavaType<T> javaTypeDescriptor) {
      super(returnedClass, jdbcTypeDescriptor, javaTypeDescriptor);
  }
  
	@Override
  public BasicValueConverter<T, Object> getValueConverter(){
      return new BasicValueConverterImplementation();
  }
}
