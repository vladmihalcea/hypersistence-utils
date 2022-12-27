package com.vladmihalcea.hibernate.type.util;

/**
 * Maps the JPA camelCase properties to snake_case database identifiers.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/map-camel-case-properties-snake-case-column-names-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @deprecated use {@link com.vladmihalcea.hibernate.naming.CamelCaseToSnakeCaseNamingStrategy} instead
 *
 * @author Vlad Mihalcea
 */
@Deprecated
public class CamelCaseToSnakeCaseNamingStrategy extends com.vladmihalcea.hibernate.naming.CamelCaseToSnakeCaseNamingStrategy {

    public static final CamelCaseToSnakeCaseNamingStrategy INSTANCE = new CamelCaseToSnakeCaseNamingStrategy();

    public static final String CAMEL_CASE_REGEX = "([a-z]+)([A-Z]+)";

    public static final String SNAKE_CASE_PATTERN = "$1\\_$2";

}
