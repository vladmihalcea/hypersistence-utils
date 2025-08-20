package io.hypersistence.utils.spring.annotation;

import java.lang.annotation.*;

/**
 * The Retry annotation instructs Spring to retry
 * a method execution when catching a given {@link Throwable}.
 *
 * @author Vlad Mihalcea
 * @since 3.1.0
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Inherited
public @interface Retry {

    /**
     * Declare the throwable types the retry will be executed for.
     *
     * @return throwable types triggering a retry
     */
    Class<? extends Throwable>[] on();

    /**
     * The number of retry attempts
     *
     * @return retry attempts
     */
    int times() default 1;
}
