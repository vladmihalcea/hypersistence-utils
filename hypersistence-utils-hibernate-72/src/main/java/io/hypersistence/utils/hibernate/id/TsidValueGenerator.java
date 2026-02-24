package io.hypersistence.utils.hibernate.id;

import io.hypersistence.tsid.TSID;
import io.hypersistence.utils.common.ReflectionUtils;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.generator.*;

import java.lang.annotation.Annotation;
import java.lang.reflect.Member;
import java.util.EnumSet;
import java.util.function.Supplier;

/**
 * Hibernate ValueGenerator for generating TSID values on non-ID entity fields.
 *
 * <p>Supports automatic TSID generation for regular columns annotated with {@link Tsid},
 * unlike {@link TsidGenerator} which is for entity IDs only.</p>
 *
 * @author Donghun Kim
 */
public class TsidValueGenerator implements AnnotationBasedGenerator<Tsid>, BeforeExecutionGenerator {

    private TSID.Factory factory;

    private TsidGenerator.AttributeType idTyattributeTypee;

    public TsidValueGenerator() {
    }

    @Override
    public void initialize(Tsid tsidAnnotation, Member member, GeneratorCreationContext generatorCreationContext) {
        idTyattributeTypee = TsidGenerator.AttributeType.valueOf(ReflectionUtils.getMemberType(member));
        if (tsidAnnotation != null) {
            Class<? extends Supplier<TSID.Factory>> supplierClass = tsidAnnotation.value();

            if (supplierClass.equals(Tsid.FactorySupplier.class)) {
                this.factory = Tsid.FactorySupplier.INSTANCE.get();
            } else {
                Supplier<TSID.Factory> supplier = ReflectionUtils.newInstance(supplierClass);
                this.factory = supplier.get();
            }
        }
    }

    @Override
    public Object generate(SharedSessionContractImplementor sharedSessionContractImplementor, Object o, Object o1, EventType eventType) {
        return idTyattributeTypee.cast(factory.generate());
    }

    @Override
    public EnumSet<EventType> getEventTypes() {
        return EventTypeSets.INSERT_ONLY;
    }
}
