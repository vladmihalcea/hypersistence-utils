package io.hypersistence.utils.hibernate.type.money;

import io.hypersistence.utils.hibernate.type.AbstractHibernateType;
import io.hypersistence.utils.hibernate.type.money.internal.CurrencyUnitTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;
import org.hibernate.type.spi.TypeBootstrapContext;

import javax.money.CurrencyUnit;

/**
 * Maps a Java {@link CurrencyUnit} object to a {@code VARCHAR} column type.
 *
 * @author Piotr Olaszewski
 */
public class CurrencyUnitType extends AbstractHibernateType<CurrencyUnit> {
    public static final CurrencyUnitType INSTANCE = new CurrencyUnitType();

    public CurrencyUnitType() {
        super(VarcharTypeDescriptor.INSTANCE, CurrencyUnitTypeDescriptor.INSTANCE);
    }

    public CurrencyUnitType(Configuration configuration) {
        super(
            VarcharTypeDescriptor.INSTANCE,
            CurrencyUnitTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public CurrencyUnitType(TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    public String getName() {
        return "currency";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}
