package com.vladmihalcea.hibernate.id;

import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.H2DataSourceProvider;

/**
 * @author Philippe Marschall
 */
public class H2BatchSequenceGeneratorTest extends AbstractBatchSequenceGeneratorTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new H2DataSourceProvider();
    }
}
