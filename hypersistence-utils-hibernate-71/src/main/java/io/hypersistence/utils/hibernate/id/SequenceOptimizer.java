package io.hypersistence.utils.hibernate.id;

import org.hibernate.annotations.IdGeneratorType;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Meta annotation to use a custom {@link org.hibernate.id.enhanced.Optimizer} for a sequence-based identifier generator.
 * 
 * @author Vlad Mihalcea
 * @since 3.13.0
 */
@IdGeneratorType(SequenceOptimizerGenerator.class)
@Retention(RUNTIME)
@Target({ FIELD, METHOD })
public @interface SequenceOptimizer {

    /**
     * Returns the name of the sequence to use.
     * 
     * @return the name of the sequence to use
     */
    String sequenceName();

    /**
     *  Returns the initial sequence value.
     *
     * @return initial sequence value
     */
    int initialValue() default 1;

    /**
     *  Returns the increment sequence value.
     * 
     * @return increment sequence value.
     */
    int incrementSize() default 50;

    /**
     * Returns the optimizer name. It can be either the fully-qualified Class name or the short name given by the {@link org.hibernate.id.enhanced.StandardOptimizerDescriptor}
     * 
     * @return the optimizer name
     */
    String optimizer() default "";
}
