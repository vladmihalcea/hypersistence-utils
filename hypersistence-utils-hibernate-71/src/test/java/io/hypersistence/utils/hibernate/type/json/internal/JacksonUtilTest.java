package io.hypersistence.utils.hibernate.type.json.internal;

import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Objects;

import static org.junit.Assert.assertEquals;

public class JacksonUtilTest {

    @Test
    public void cloneDeserializeStepErrorTest() {
        MyEntity entity = new MyEntity();

        entity.setValue("some value");
        entity.setPojos(Arrays.asList(
                createMyPojo("first value", MyType.A, "1.1", createOtherPojo("USD")),
                createMyPojo("second value", MyType.B, "1.2", createOtherPojo("BRL"))
        ));

        MyEntity clone = JacksonUtil.clone(entity);
        assertEquals(clone, entity);

        List<MyPojo> clonePojos = JacksonUtil.clone(entity.getPojos());
        assertEquals(clonePojos, entity.getPojos());
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            MyEntity myEntity = (MyEntity) o;
            return Objects.equals(value, myEntity.value) &&
                    Objects.equals(pojos, myEntity.pojos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, pojos);
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            MyPojo myPojo = (MyPojo) o;
            return Objects.equals(value, myPojo.value) &&
                    type == myPojo.type &&
                    Objects.equals(number, myPojo.number) &&
                    Objects.equals(otherPojo, myPojo.otherPojo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(value, type, number, otherPojo);
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
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            OtherPojo otherPojo = (OtherPojo) o;
            return Objects.equals(currency, otherPojo.currency);
        }

        @Override
        public int hashCode() {
            return Objects.hash(currency);
        }
    }

}