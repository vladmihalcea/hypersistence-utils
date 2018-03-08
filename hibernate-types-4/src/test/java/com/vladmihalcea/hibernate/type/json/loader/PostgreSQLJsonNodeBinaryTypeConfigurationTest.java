package com.vladmihalcea.hibernate.type.json.loader;

import com.vladmihalcea.hibernate.type.json.PostgreSQLJsonNodeBinaryTypeTest;
import com.vladmihalcea.hibernate.type.util.Configuration;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonNodeBinaryTypeConfigurationTest extends PostgreSQLJsonNodeBinaryTypeTest {

    @Override
    public void init() {
        System.setProperty(
            Configuration.PROPERTIES_FILE_PATH,
                "PostgreSQLJsonNodeBinaryType.properties"
        );
        super.init();
    }

    @Override
    public void destroy() {
        super.destroy();
        System.getProperties().remove(Configuration.PROPERTIES_FILE_PATH);
    }

    @Override
    protected String initialPrice() {
        return "44.991234567";
    }
}
