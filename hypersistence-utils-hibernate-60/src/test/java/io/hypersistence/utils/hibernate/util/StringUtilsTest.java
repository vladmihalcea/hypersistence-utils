package io.hypersistence.utils.hibernate.util;

import io.hypersistence.utils.common.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class StringUtilsTest {

    @Test
    public void testJoin(){
        assertEquals(
            "Oracle,PostgreSQL,MySQL,SQL Server",
            StringUtils.join(",", "Oracle", "PostgreSQL", "MySQL", "SQL Server")
        );
    }
}
