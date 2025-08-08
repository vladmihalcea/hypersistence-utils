package io.hypersistence.utils.spring.aop;

import io.hypersistence.utils.spring.annotation.Retry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Aspect to retry method execution.
 *
 * @author Vlad Mihalcea
 */
@Aspect
@Component
public class RetryAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetryAspect.class);

    @Around("@annotation(io.hypersistence.utils.spring.annotation.Retry)")
    public Object retry(ProceedingJoinPoint pjp) throws Throwable {
        Retry retryAnnotation = getAnnotation(pjp, Retry.class);
        return (retryAnnotation != null) ? proceed(pjp, retryAnnotation) : proceed(pjp);
    }

    private Object proceed(ProceedingJoinPoint pjp) throws Throwable {
        return pjp.proceed();
    }

    private Object proceed(ProceedingJoinPoint pjp, Retry retryAnnotation) throws Throwable {
        int times = retryAnnotation.times();
        Class<? extends Throwable>[] retryOn = retryAnnotation.on();
        if (times <= 0) {
            throw new IllegalArgumentException(
                "@Retry{times} should be greater than 0!"
            );
        }
        if (retryOn.length <= 0) {
            throw new IllegalArgumentException(
                "@Retry{on} should have at least one Throwable!"
            );
        }

        LOGGER.trace("Proceed with {} retries on {}", times, Arrays.toString(retryOn));

        return tryProceeding(pjp, times, retryOn);
    }

    private Object tryProceeding(ProceedingJoinPoint pjp, int times, Class<? extends Throwable>[] retryOn)
        throws Throwable {
        try {
            return proceed(pjp);
        } catch (Throwable throwable) {
            if (isRetryThrowable(throwable, retryOn) && times-- > 0) {
                LOGGER.info(
                    "Retryable failure was caught, {} remaining retr{} on {}",
                    times,
                    (times == 1 ? "y" : "ies"),
                    Arrays.toString(retryOn)
                );
                return tryProceeding(pjp, times, retryOn);
            }
            throw throwable;
        }
    }

    private boolean isRetryThrowable(Throwable throwable, Class<? extends Throwable>[] retryOn) {
        Throwable cause = throwable;
        do {
            for (Class<? extends Throwable> retryThrowable : retryOn) {
                if (retryThrowable.isAssignableFrom(cause.getClass())) {
                    return true;
                }
            }

            if (cause.getCause() == null || cause.getCause() == cause) {
                break;
            } else {
                cause = cause.getCause();
            }
        }
        while (true);
        return false;
    }

    private <T extends Annotation> T getAnnotation(ProceedingJoinPoint pjp, Class<T> annotationClass)
        throws NoSuchMethodException {
        MethodSignature signature = (MethodSignature) pjp.getSignature();
        Method method = signature.getMethod();
        T annotation = AnnotationUtils.findAnnotation(method, annotationClass);

        if (annotation != null) {
            return annotation;
        }

        method = pjp.getTarget().getClass().getMethod(
            pjp.getSignature().getName(),
            signature.getParameterTypes()
        );
        return AnnotationUtils.findAnnotation(method, annotationClass);
    }
}
