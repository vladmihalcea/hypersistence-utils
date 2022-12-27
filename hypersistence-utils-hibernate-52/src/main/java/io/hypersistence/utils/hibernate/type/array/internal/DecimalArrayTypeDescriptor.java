package io.hypersistence.utils.hibernate.type.array.internal;

import java.math.BigDecimal;

/**
 * @author Moritz Kobel
 */
public class DecimalArrayTypeDescriptor extends AbstractArrayTypeDescriptor<BigDecimal[]> {

    public DecimalArrayTypeDescriptor() {
        super(BigDecimal[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "decimal";
    }
}
