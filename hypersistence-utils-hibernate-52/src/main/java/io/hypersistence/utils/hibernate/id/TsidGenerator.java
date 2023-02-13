package io.hypersistence.utils.hibernate.id;

import io.hypersistence.tsid.TSID;
import io.hypersistence.utils.hibernate.util.ReflectionUtils;
import org.hibernate.HibernateException;
import org.hibernate.MappingException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.Configurable;
import org.hibernate.id.IdentifierGenerator;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.io.Serializable;
import java.util.Properties;
import java.util.function.Supplier;

/**
 * @author Vlad Mihalcea
 */
public class TsidGenerator implements IdentifierGenerator, Configurable {

    private TSID.Factory DEFAULT_TSID_FACTORY = TSID.Factory.builder()
        .withRandomFunction(TSID.Factory.THREAD_LOCAL_RANDOM_FUNCTION)
        .build();

    /**
     * Indicates the name of the TSID.Factory Supplier.
     */
    public static final String TSID_FACTORY_SUPPLIER_PARAM = "tsid_factory_supplier";

    private TSID.Factory tsidFactory = DEFAULT_TSID_FACTORY;

    private AttributeType idType;

    @Override
    public void configure(Type type, Properties params, ServiceRegistry serviceRegistry)
        throws MappingException {
        idType = AttributeType.valueOf(type.getReturnedClass());

        String tsidSupplierClass = ConfigurationHelper.getString(TSID_FACTORY_SUPPLIER_PARAM, params);
        if (tsidSupplierClass != null) {
            Supplier<TSID.Factory> factorySupplier = ReflectionUtils.newInstance(tsidSupplierClass);
            tsidFactory = factorySupplier.get();
        }
    }

    @Override
    public Serializable generate(SharedSessionContractImplementor session, Object object) throws HibernateException {
        return idType.cast(tsidFactory.generate());
    }

    enum AttributeType {
        LONG {
            @Override
            public Serializable cast(TSID tsid) {
                return tsid.toLong();
            }
        },
        STRING {
            @Override
            public Serializable cast(TSID tsid) {
                return tsid.toString();
            }
        },
        TSID {
            @Override
            public Serializable cast(TSID tsid) {
                return tsid;
            }
        };

        public abstract Serializable cast(TSID tsid);

        static AttributeType valueOf(Class clazz) {
            if(Long.class.isAssignableFrom(clazz)) {
                return LONG;
            } else if (String.class.isAssignableFrom(clazz)) {
                return STRING;
            } else if (TSID.class.isAssignableFrom(clazz)) {
                return TSID;
            } else {
                throw new HibernateException(
                    String.format(
                        "The @Tsid annotation on [%s] can only be placed on a Long or String entity attribute!",
                        clazz
                    )
                );
            }
        }
    }
}
