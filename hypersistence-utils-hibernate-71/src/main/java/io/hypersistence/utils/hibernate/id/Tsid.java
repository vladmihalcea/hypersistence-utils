package io.hypersistence.utils.hibernate.id;

import io.hypersistence.tsid.TSID;
import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.annotations.ValueGenerationType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Supplier;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;

/**
 * The {@code @Tsid} annotation can be added to entity identifiers or other fields,
 * indicating that the value will be assigned a time-sorted TSID automatically.
 *
 * <p>On identifier fields, {@link TsidGenerator} is used for value generation.
 * On non-identifier fields, {@link TsidValueGenerator} is used.</p>
 *
 * You can use the {@code @Tsid} annotation to annotate {@link Long}, {@link String},
 * or {@link TSID} fields, including entity identifiers and general columns.
 *
 * @author Vlad Mihalcea
 */
@IdGeneratorType(TsidGenerator.class)
@ValueGenerationType(generatedBy = TsidValueGenerator.class)
@Retention(RetentionPolicy.RUNTIME)
@Target({FIELD, METHOD})
public @interface Tsid {

    /**
     * Specify the class that can provide the custom {@link TSID.Factory}.
     * By default, the {@link FactorySupplier} is used.
     *
     * @return the {@link TSID.Factory} supplier.
     */
    Class<? extends Supplier<TSID.Factory>> value() default FactorySupplier.class;

    class FactorySupplier implements Supplier<TSID.Factory> {

        public static final FactorySupplier INSTANCE = new FactorySupplier();

        private TSID.Factory tsidFactory = TSID.Factory.builder()
            .withRandomFunction(TSID.Factory.THREAD_LOCAL_RANDOM_FUNCTION)
            .build();

        @Override
        public TSID.Factory get() {
            return tsidFactory;
        }
    }
}
