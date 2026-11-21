package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.HibernateException;
import org.hibernate.annotations.Type;
import org.junit.Test;
import tools.jackson.databind.exc.InvalidDefinitionException;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class PostgreSQLJsonImmutableTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            CustomerOrder.class
        };
    }

    @Test
    public void testMissingCreatorReportsOriginalFailure() {
        executeStatement(
            "insert into customer_order (id, payment_details) values " +
            "(1, '[{\"provider\":\"card\",\"transactionId\":\"TX-1\",\"amount\":12.5}]')"
        );

        assertMissingCreator();
    }

    @Test
    public void testIntegerMapKeysReportOriginalFailure() {
        executeStatement(
            "insert into customer_order (id, payment_map) values " +
            "(1, '{\"1\":{\"provider\":\"card\",\"transactionId\":\"TX-1\",\"amount\":12.5}}')"
        );

        assertMissingCreator();
    }

    private void assertMissingCreator() {
        HibernateException exception = assertThrows(HibernateException.class, () -> doInJPA(entityManager -> {
            entityManager.createQuery("select o from CustomerOrder o", CustomerOrder.class).getResultList();
        }));

        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause.toString(), cause instanceof InvalidDefinitionException);
        assertTrue(cause.getMessage(), cause.getMessage().contains(PaymentDetails.class.getName()));
    }

    @Test
    public void testImmutableValueWithCreatorNeedsNoSetters() {
        executeStatement(
            "insert into customer_order (id, immutable_payment_details) values " +
            "(1, '[{\"provider\":\"card\",\"transactionId\":\"TX-1\",\"amount\":12.5}]')"
        );

        doInJPA(entityManager -> {
            CustomerOrder order = entityManager.createQuery(
                "select o from CustomerOrder o", CustomerOrder.class
            ).getSingleResult();
            ImmutablePaymentDetails payment = order.immutablePaymentDetails.get(0);
            assertEquals("card", payment.getProvider());
            assertEquals("TX-1", payment.getTransactionId());
            assertEquals(12.5, payment.getAmount(), 0);
        });
    }

    @Entity(name = "CustomerOrder")
    @Table(name = "customer_order")
    public static class CustomerOrder {

        @Id
        private Long id;

        @Type(JsonBinaryType.class)
        @Column(name = "payment_details", columnDefinition = "jsonb")
        private List<PaymentDetails> paymentDetails;

        @Type(JsonType.class)
        @Column(name = "payment_map", columnDefinition = "jsonb")
        private Map<Integer, PaymentDetails> paymentMap;

        @Type(JsonType.class)
        @Column(name = "immutable_payment_details", columnDefinition = "jsonb")
        private List<ImmutablePaymentDetails> immutablePaymentDetails;
    }

    public static final class PaymentDetails implements Serializable {

        private final String provider;
        private final String transactionId;
        private final double amount;

        public PaymentDetails(String provider, String transactionId, double amount) {
            this.provider = provider;
            this.transactionId = transactionId;
            this.amount = amount;
        }

        public String getProvider() {
            return provider;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public double getAmount() {
            return amount;
        }
    }

    public static final class ImmutablePaymentDetails implements Serializable {

        private final String provider;
        private final String transactionId;
        private final double amount;

        @JsonCreator
        public ImmutablePaymentDetails(
            @JsonProperty("provider") String provider,
            @JsonProperty("transactionId") String transactionId,
            @JsonProperty("amount") double amount) {
            this.provider = provider;
            this.transactionId = transactionId;
            this.amount = amount;
        }

        public String getProvider() {
            return provider;
        }

        public String getTransactionId() {
            return transactionId;
        }

        public double getAmount() {
            return amount;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof ImmutablePaymentDetails)) return false;
            ImmutablePaymentDetails that = (ImmutablePaymentDetails) other;
            return Double.compare(amount, that.amount) == 0 &&
                Objects.equals(provider, that.provider) &&
                Objects.equals(transactionId, that.transactionId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(provider, transactionId, amount);
        }
    }
}
