package com.vladmihalcea.hibernate.type.binary;

import org.hibernate.type.BinaryType;
import org.hibernate.type.descriptor.sql.BinaryTypeDescriptor;

/**
 * Maps a Java {@link byte[]} object to a BINARY MySQL column type.
 *
 * @author Vlad Mihalcea
 * @since 2.10.1
 */
public class MySQLBinaryType extends BinaryType {

    public MySQLBinaryType() {
        setSqlTypeDescriptor(BinaryTypeDescriptor.INSTANCE);
    }
}