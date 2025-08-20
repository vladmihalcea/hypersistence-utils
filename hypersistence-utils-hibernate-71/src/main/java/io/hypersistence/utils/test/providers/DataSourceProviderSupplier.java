package io.hypersistence.utils.test.providers;

import org.hibernate.dialect.Database;

import java.util.Map;
import java.util.function.Supplier;

/**
 * @author Vlad Mihalcea
 */
public interface DataSourceProviderSupplier extends Supplier<Map<Database, DataSourceProvider>> {

}