package com.vladmihalcea.hibernate.type.util;

import org.junit.Assert;
import org.junit.Test;

public class StringUtilsTest {

    @Test
    public void should_join_string_with_delimiter(){

        // GIVEN
        String delimiter = ",";

        // WHEN
        final String joinedString = StringUtils.join(delimiter, "Oracle", "PostgreSQL", "MySQL", "SQL Server");

        // THEN
        Assert.assertEquals("Oracle,PostgreSQL,MySQL,SQL Server", joinedString);
    }
}
