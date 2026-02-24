package io.hypersistence.utils.hibernate.id;

import org.hibernate.MappingException;
import org.hibernate.generator.GeneratorCreationContext;
import org.hibernate.id.enhanced.SequenceStyleGenerator;

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
            GeneratorCreationContext context) {
        sequenceName = annotation.sequenceName();
        initialValue = annotation.initialValue();
        incrementSize = annotation.incrementSize();
        optimizer = annotation.optimizer();
    }

    @Override
    public void configure(GeneratorCreationContext creationContext, Properties parameters) throws MappingException {
        parameters.setProperty(SEQUENCE_PARAM, sequenceName);
        parameters.setProperty(INITIAL_PARAM, String.valueOf(initialValue));
        parameters.setProperty(INCREMENT_PARAM, String.valueOf(incrementSize));
        parameters.setProperty(OPT_PARAM, optimizer);

        super.configure(creationContext, parameters);
    }
}
