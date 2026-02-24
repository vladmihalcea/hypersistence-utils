package io.hypersistence.utils.hibernate.naming;

import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.boot.model.naming.Identifier;
import org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl;
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment;

/**
 * Maps the JPA camelCase properties to snake_case database identifiers.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/map-camel-case-properties-snake-case-column-names-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class CamelCaseToSnakeCaseNamingStrategy extends PhysicalNamingStrategyStandardImpl {

    public static final CamelCaseToSnakeCaseNamingStrategy INSTANCE = new CamelCaseToSnakeCaseNamingStrategy();

    public static final String CAMEL_CASE_REGEX = "([a-z]+)([A-Z]+)";

    public static final String SNAKE_CASE_PATTERN = "$1\\_$2";

    private final Configuration configuration;

    /**
     * Initialization constructor taking the default {@link Configuration} object.
     */
    public CamelCaseToSnakeCaseNamingStrategy() {
        this.configuration = Configuration.INSTANCE;
    }

    /**
     * Initialization constructor taking the {@link Class} and {@link Configuration} objects.
     *
     * @param configuration custom {@link Configuration} object.
     */
    public CamelCaseToSnakeCaseNamingStrategy(Configuration configuration) {
        this.configuration = configuration;
    }

    @Override
    public Identifier toPhysicalCatalogName(Identifier name, JdbcEnvironment context) {
        return formatIdentifier(super.toPhysicalCatalogName(name, context));
    }

    @Override
    public Identifier toPhysicalSchemaName(Identifier name, JdbcEnvironment context) {
        return formatIdentifier(super.toPhysicalSchemaName(name, context));
    }

    @Override
    public Identifier toPhysicalTableName(Identifier name, JdbcEnvironment context) {
        return formatIdentifier(super.toPhysicalTableName(name, context));
    }

    @Override
    public Identifier toPhysicalSequenceName(Identifier name, JdbcEnvironment context) {
        return formatIdentifier(super.toPhysicalSequenceName(name, context));
    }

    @Override
    public Identifier toPhysicalColumnName(Identifier name, JdbcEnvironment context) {
        return formatIdentifier(super.toPhysicalColumnName(name, context));
    }

    private Identifier formatIdentifier(Identifier identifier) {
        if (identifier != null) {
            String name = identifier.getText();

            String formattedName = name.replaceAll(CAMEL_CASE_REGEX, SNAKE_CASE_PATTERN).toLowerCase();

            return !formattedName.equals(name) ?
                    Identifier.toIdentifier(formattedName, identifier.isQuoted()) :
                    identifier;
        } else {
            return null;
        }

    }
}
