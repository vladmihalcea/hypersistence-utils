package com.vladmihalcea.hibernate.type.util;

import org.hibernate.cfg.DefaultNamingStrategy;

/**
 * @author Vlad Mihalcea
 */
public class CamelCaseToSnakeCaseNamingStrategy extends DefaultNamingStrategy {

    public static final CamelCaseToSnakeCaseNamingStrategy INSTANCE = new CamelCaseToSnakeCaseNamingStrategy();

    public static final String CAMEL_CASE_REGEX = "([a-z]+)([A-Z]+)";

    public static final String SNAKE_CASE_PATTERN = "$1\\_$2";

    @Override
    public String classToTableName(String className) {
        return formatIdentifier(super.classToTableName(className));
    }

    @Override
    public String propertyToColumnName(String propertyName) {
        return formatIdentifier(super.propertyToColumnName(propertyName));
    }

    @Override
    public String collectionTableName(String ownerEntity, String ownerEntityTable, String associatedEntity, String associatedEntityTable, String propertyName) {
        return formatIdentifier(super.collectionTableName(ownerEntity, ownerEntityTable, associatedEntity, associatedEntityTable, propertyName));
    }

    @Override
    public String joinKeyColumnName(String joinedColumn, String joinedTable) {
        return formatIdentifier(super.joinKeyColumnName(joinedColumn, joinedTable));
    }

    @Override
    public String foreignKeyColumnName(String propertyName, String propertyEntityName, String propertyTableName, String referencedColumnName) {
        return formatIdentifier(super.foreignKeyColumnName(propertyName, propertyEntityName, propertyTableName, referencedColumnName));
    }

    private String formatIdentifier(String identifier) {

        String formattedName = identifier.replaceAll(CAMEL_CASE_REGEX, SNAKE_CASE_PATTERN).toLowerCase();

        return !formattedName.equals(identifier) ? formattedName : identifier;
    }
}
