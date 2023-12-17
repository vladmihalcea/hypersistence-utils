package io.hypersistence.utils.hibernate.type.json.polymorphic;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonPolymorphicListJacksonTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }

    @Test
    public void test() {

        doInJPA(entityManager -> {
            entityManager.persist(
                new Book()
                    .setIsbn("978-9730228236")
                    .addCoupon(new AmountDiscountCoupon("PPP")
                        .setAmount(new BigDecimal("4.99"))
                    )
                    .addCoupon(new PercentageDiscountCoupon("Black Friday")
                        .setPercentage(BigDecimal.valueOf(0.02))
                    )
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            Map<String, DiscountCoupon> topics = book.getCoupons()
                .stream()
                .collect(
                    Collectors.toMap(
                        DiscountCoupon::getName,
                        Function.identity()
                    )
                );

            assertEquals(2, topics.size());
            AmountDiscountCoupon amountDiscountCoupon = (AmountDiscountCoupon) topics.get("PPP");
            assertEquals(
                new BigDecimal("4.99"),
                amountDiscountCoupon.getAmount()
            );

            PercentageDiscountCoupon percentageDiscountCoupon = (PercentageDiscountCoupon) topics.get("Black Friday");
            assertEquals(
                BigDecimal.valueOf(0.02),
                percentageDiscountCoupon.getPercentage()
            );
        });
    }

    @Entity(name = "Book")
    @Table(name = "book")
    
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        @Column(length = 15)
        private String isbn;

        @Type(JsonType.class)
        @Column(columnDefinition = "jsonb")
        private List<DiscountCoupon> coupons = new ArrayList<>();

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public List<DiscountCoupon> getCoupons() {
            return coupons;
        }

        public Book setCoupons(List<DiscountCoupon> coupons) {
            this.coupons = coupons;
            return this;
        }

        public Book addCoupon(DiscountCoupon topic) {
            coupons.add(topic);
            return this;
        }
    }

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(
            name = "discount.coupon.amount",
            value = AmountDiscountCoupon.class
        ),
        @JsonSubTypes.Type(
            name = "discount.coupon.percentage",
            value = PercentageDiscountCoupon.class
        ),
    })
    public abstract static class DiscountCoupon implements Serializable {

        private String name;

        public DiscountCoupon() {
        }

        public DiscountCoupon(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
        public abstract String getType();

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof DiscountCoupon)) return false;
            DiscountCoupon that = (DiscountCoupon) o;
            return Objects.equals(getName(), that.getName());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getName());
        }
    }

    public static class AmountDiscountCoupon extends DiscountCoupon {

        public static final String DISCRIMINATOR = "discount.coupon.amount";

        private BigDecimal amount;

        public AmountDiscountCoupon() {
        }

        public AmountDiscountCoupon(String name) {
            super(name);
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public AmountDiscountCoupon setAmount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        @Override
        public String getType() {
            return DISCRIMINATOR;
        }
    }

    public static class PercentageDiscountCoupon extends DiscountCoupon {

        public static final String DISCRIMINATOR = "discount.coupon.percentage";

        private BigDecimal percentage;

        public PercentageDiscountCoupon() {
        }

        public PercentageDiscountCoupon(String name) {
            super(name);
        }

        public BigDecimal getPercentage() {
            return percentage;
        }

        public PercentageDiscountCoupon setPercentage(BigDecimal amount) {
            this.percentage = amount;
            return this;
        }

        @Override
        public String getType() {
            return DISCRIMINATOR;
        }
    }
}