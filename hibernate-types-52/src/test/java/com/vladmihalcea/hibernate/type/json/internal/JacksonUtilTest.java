package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.internal.util.SerializationHelper;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;

public class JacksonUtilTest {

    @Test(expected = IllegalArgumentException.class)
    public void cloneDeserializeStepErrorTest() throws Exception {
        MyEntity entity = new MyEntity();

        entity.setValue("some value");
        entity.setPojos(Arrays.asList(
                createMyPojo("first value", MyType.A, "1.1", createOtherPojo("USD")),
                createMyPojo("second value", MyType.B, "1.2", createOtherPojo("BRL"))
        ));

        MyEntity clone = JacksonUtil.clone(entity);

        System.out.println("entity = " + entity);
        System.out.println("clone = " + clone);

        List<MyPojo> pojos = JacksonUtil.clone(entity.getPojos());
        System.out.println("pojos = " + pojos);
    }

    @Test
    public void cloneWithHibernateHelperTest() throws Exception {
        MyEntity entity = new MyEntity();

        entity.setValue("some value");
        entity.setPojos(Arrays.asList(
                createMyPojo("first value", MyType.A, "1.1", createOtherPojo("USD")),
                createMyPojo("second value", MyType.B, "1.2", createOtherPojo("BRL"))
        ));

        Object clone = SerializationHelper.clone(new ArrayList<>(entity.getPojos()));
        System.out.println("clone = " + clone);
    }

    private MyPojo createMyPojo(String value, MyType myType, String number, OtherPojo otherPojo) {
        MyPojo myPojo = new MyPojo();
        myPojo.setValue(value);
        myPojo.setType(myType);
        myPojo.setNumber(new BigDecimal(number));
        myPojo.setOtherPojo(otherPojo);
        return myPojo;
    }

    private OtherPojo createOtherPojo(String currency) {
        OtherPojo otherPojo = new OtherPojo();
        otherPojo.setCurrency(Currency.getInstance(currency));
        return otherPojo;
    }

    public static class MyEntity {
        private String value;
        private List<MyPojo> pojos;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public List<MyPojo> getPojos() {
            return pojos;
        }

        public void setPojos(List<MyPojo> pojos) {
            this.pojos = pojos;
        }

        @Override
        public String toString() {
            return "MyEntity{" +
                    "value='" + value + '\'' +
                    ", pojos=" + pojos +
                    '}';
        }
    }

    public static class MyPojo implements Serializable {
        private String value;
        private MyType type;
        private BigDecimal number;
        private OtherPojo otherPojo;

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }

        public MyType getType() {
            return type;
        }

        public void setType(MyType type) {
            this.type = type;
        }

        public BigDecimal getNumber() {
            return number;
        }

        public void setNumber(BigDecimal number) {
            this.number = number;
        }

        public OtherPojo getOtherPojo() {
            return otherPojo;
        }

        public void setOtherPojo(OtherPojo otherPojo) {
            this.otherPojo = otherPojo;
        }

        @Override
        public String toString() {
            return "MyPojo{" +
                    "value='" + value + '\'' +
                    ", type=" + type +
                    ", number=" + number +
                    ", otherPojo=" + otherPojo +
                    '}';
        }
    }

    public enum MyType {
        A, B, C
    }

    public static class OtherPojo implements Serializable {
        private Currency currency;

        public Currency getCurrency() {
            return currency;
        }

        public void setCurrency(Currency currency) {
            this.currency = currency;
        }

        @Override
        public String toString() {
            return "OtherPojo{" +
                    "currency=" + currency +
                    '}';
        }
    }

}