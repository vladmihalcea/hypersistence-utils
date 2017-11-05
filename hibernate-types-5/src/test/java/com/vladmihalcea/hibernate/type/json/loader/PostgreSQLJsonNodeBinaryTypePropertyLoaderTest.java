package com.vladmihalcea.hibernate.type.json.loader;

import com.vladmihalcea.hibernate.type.json.PostgreSQLJsonNodeBinaryTypeTest;
import com.vladmihalcea.hibernate.type.util.PropertyLoader;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonNodeBinaryTypePropertyLoaderTest extends PostgreSQLJsonNodeBinaryTypeTest {

    @Override
    public void init() {
        System.setProperty(
            PropertyLoader.PROPERTIES_FILE_PATH,
                "PostgreSQLJsonNodeBinaryType.properties"
        );
        super.init();
    }

    @Override
    public void destroy() {
        super.destroy();
        System.getProperties().remove(PropertyLoader.PROPERTIES_FILE_PATH);
    }
}
