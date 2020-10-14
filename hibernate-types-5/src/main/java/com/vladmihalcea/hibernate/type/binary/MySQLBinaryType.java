package com.vladmihalcea.hibernate.type.binary;

import org.hibernate.type.BinaryType;
import org.hibernate.type.descriptor.sql.BinaryTypeDescriptor;

public class MySQLBinaryType extends BinaryType {

    public MySQLBinaryType() {
        setSqlTypeDescriptor(BinaryTypeDescriptor.INSTANCE);
    }
}