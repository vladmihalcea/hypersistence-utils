package com.vladmihalcea.hibernate.type.binary;

import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.BinaryType;
import org.hibernate.type.descriptor.sql.BinaryTypeDescriptor;

/**
 * Maps a Java {@link byte[]} object to a BINARY MySQL column type.
 *
 * @author Vlad Mihalcea
 * @since 2.10.1
 */
public class MySQLBinaryType extends BinaryType {

    public static final MySQLBinaryType INSTANCE = new MySQLBinaryType();

    private final Configuration configuration;

    /**
     * Initialization constructor taking the default {@link Configuration} object.
     */
    public MySQLBinaryType() {
        this(Configuration.INSTANCE);
    }

    /**
     * Initialization constructor taking a custom {@link Configuration} object.
     *
     * @param configuration custom {@link Configuration} object.
     */
    public MySQLBinaryType(Configuration configuration) {
        this.configuration = configuration;
        setSqlTypeDescriptor(BinaryTypeDescriptor.INSTANCE);
    }
}