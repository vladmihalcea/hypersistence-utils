package io.hypersistence.utils.hibernate.id;

import org.hibernate.MappingException;
import org.hibernate.id.enhanced.SequenceStyleGenerator;
import org.hibernate.id.factory.spi.CustomIdGeneratorCreationContext;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.Type;

import java.lang.reflect.Member;
import java.util.Properties;

/**
 * Hibernate ValueGenerator for generating sequence values when using the {@link SequenceOptimizer} annotation.
 *
 * @author Vlad Mihalcea
 */
public class SequenceOptimizerGenerator extends SequenceStyleGenerator {

    private final String sequenceName;
    private final int initialValue;
    private final int incrementSize;
    private final String optimizer;

    public SequenceOptimizerGenerator(
            SequenceOptimizer annotation,
            Member annotatedMember,
            CustomIdGeneratorCreationContext context) {
        sequenceName = annotation.sequenceName();
        initialValue = annotation.initialValue();
        incrementSize = annotation.incrementSize();
        optimizer = annotation.optimizer();
    }

    @Override
    public void configure(Type type, Properties parameters, ServiceRegistry serviceRegistry) throws MappingException {
        parameters.setProperty(SEQUENCE_PARAM, sequenceName);
        parameters.setProperty(INITIAL_PARAM, String.valueOf(initialValue));
        parameters.setProperty(INCREMENT_PARAM, String.valueOf(incrementSize));
        parameters.setProperty(OPT_PARAM, optimizer);

        super.configure(type, parameters, serviceRegistry);
    }
}
