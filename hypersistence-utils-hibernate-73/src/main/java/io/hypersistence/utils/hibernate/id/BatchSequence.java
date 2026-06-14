package io.hypersistence.utils.hibernate.id;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.annotations.IdGeneratorType;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.id.enhanced.ImplicitDatabaseObjectNamingStrategy;

/**
 * Meta annotation to use {@link BatchSequenceGenerator} as an identifier generator.
 * 
 * @author Philippe Marschall
 * @since 3.8.1
 */
@IdGeneratorType(BatchSequenceGenerator.class)
@Retention(RUNTIME)
@Target({ FIELD, METHOD })
public @interface BatchSequence {

    /**
     * Returns the name of the sequence to use.
     * <p>
     * If omitted (empty or {@code null} then {@link ImplicitDatabaseObjectNamingStrategy}
     * is used to derive the name.
     * 
     * @return the name of the sequence to use
     * @see AvailableSettings#ID_DB_STRUCTURE_NAMING_STRATEGY
     */
    String name() default "";

    /**
     *  Returns how many sequence values to fetch at once.
     * 
     * @return how many sequence values to fetch at once, must be positive
     */
    int fetchSize() default BatchSequenceGenerator.DEFAULT_FETCH_SIZE;

    /**
     * Returns the catalog name of the sequence to use.
     * 
     * @return the catalog name of the sequence to use
     */
    String catalog() default "";

    /**
     * Returns the catalog name of the sequence to use.
     * 
     * @return the catalog name of the sequence to use
     */
    String schema() default "";

}
