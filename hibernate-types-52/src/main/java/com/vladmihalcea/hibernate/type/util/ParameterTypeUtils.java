package com.vladmihalcea.hibernate.type.util;

import com.vladmihalcea.hibernate.util.StringUtils;
import org.hibernate.usertype.DynamicParameterizedType;

import javax.persistence.Column;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <code>ParameterizedTypeUtils</code> - {@link DynamicParameterizedType.ParameterType} utilities holder.
 *
 * @author Vlad Mihalcea
 * @since 2.16.0
 */
public class ParameterTypeUtils {

    private static final Pattern COLUMN_TYPE_PATTERN = Pattern.compile("([a-zA-Z0-9]+).*?");

    private ParameterTypeUtils() {
        throw new UnsupportedOperationException("StringUtils is not instantiable!");
    }

    /**
     * Resolve the {@link DynamicParameterizedType.ParameterType} instance
     * from the provided {@link Properties} object.
     *
     * @param properties configuration properties
     * @return {@link DynamicParameterizedType.ParameterType} instance
     */
    public static DynamicParameterizedType.ParameterType resolve(Properties properties) {
        Object parameterTypeObject = properties.get(DynamicParameterizedType.PARAMETER_TYPE);
        if (parameterTypeObject instanceof DynamicParameterizedType.ParameterType) {
            return (DynamicParameterizedType.ParameterType) parameterTypeObject;
        }
        return null;
    }

    /**
     * Get the required annotation from the {@link DynamicParameterizedType.ParameterType} instance.
     *
     * @param parameterType {@link DynamicParameterizedType.ParameterType} instance
     * @param annotationClass annotation class
     * @return annotation
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> A getAnnotationOrNull(DynamicParameterizedType.ParameterType parameterType, Class<A> annotationClass) {
        List<A> annotations = getAnnotations(parameterType, annotationClass);
        int annotationCount = annotations.size();
        if(annotationCount > 1) {
            throw new IllegalArgumentException(
                String.format(
                    "The provided ParameterType associated with the [%s] property contains more than one annotation of the [%s] type!",
                    parameterType.getReturnedClass(),
                    annotationClass.getName()
                )
            );
        }
        return annotationCount == 1 ? annotations.get(0) : null;
    }

    /**
     * Get the required annotations from the {@link DynamicParameterizedType.ParameterType} instance.
     *
     * @param parameterType {@link DynamicParameterizedType.ParameterType} instance
     * @param annotationClass annotation class
     * @return annotations
     */
    @SuppressWarnings("unchecked")
    public static <A extends Annotation> List<A> getAnnotations(DynamicParameterizedType.ParameterType parameterType, Class<A> annotationClass) {
        return Arrays.stream(parameterType.getAnnotationsMethod())
            .filter(a -> annotationClass.isAssignableFrom(a.annotationType()))
            .map(a -> (A) a)
            .collect(Collectors.toList());
    }

    /**
     * Get the column type association from the {@link DynamicParameterizedType.ParameterType} instance.
     *
     * @param parameterType {@link DynamicParameterizedType.ParameterType} instance
     * @return column type
     */
    public static String getColumnType(DynamicParameterizedType.ParameterType parameterType) {
        if (parameterType != null) {
            Column columnAnnotation = ParameterTypeUtils.getAnnotationOrNull(parameterType, Column.class);
            if(columnAnnotation != null) {
                String columnDefinition = columnAnnotation.columnDefinition();
                if(!StringUtils.isBlank(columnDefinition)) {
                    Matcher matcher = COLUMN_TYPE_PATTERN.matcher(columnDefinition);
                    if (matcher.matches()) {
                        return StringUtils.toLowercase(matcher.group(1));
                    }
                }
            }
        }
        return null;
    }
}
