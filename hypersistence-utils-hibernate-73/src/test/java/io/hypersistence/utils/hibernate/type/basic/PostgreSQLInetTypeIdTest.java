package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.cfg.AvailableSettings;
import org.junit.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class PostgreSQLInetTypeIdTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            IpToCountry.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(AvailableSettings.ORDER_UPDATES, true);
        properties.put(AvailableSettings.STATEMENT_BATCH_SIZE, 10);
    }

    @Test
    public void testQueryFlushesOrderedUpdates() {
        List<Inet> networks = List.of(
            new Inet("192.168.10.0/24"),
            new Inet("10.0.0.0/8"),
            new Inet("2001:db8::/32")
        );

        doInJPA(entityManager -> {
            for (Inet network : networks) {
                IpToCountry ipToCountry = new IpToCountry();
                ipToCountry.network = network;
                ipToCountry.country = "RO";
                entityManager.persist(ipToCountry);
            }
        });

        doInJPA(entityManager -> {
            for (Inet network : networks) {
                entityManager.find(IpToCountry.class, network).country = "US";
            }

            List<IpToCountry> results = entityManager.createQuery(
                "select i from IpToCountry i where i.country = :country", IpToCountry.class)
                .setParameter("country", "US")
                .getResultList();

            assertEquals(networks.size(), results.size());
        });

        doInJPA(entityManager -> {
            for (Inet network : networks) {
                assertEquals("US", entityManager.find(IpToCountry.class, network).country);
            }
        });
    }

    @Entity(name = "IpToCountry")
    @Table(name = "ip_to_country")
    public static class IpToCountry {

        @Id
        @Type(PostgreSQLInetType.class)
        @Column(columnDefinition = "inet")
        private Inet network;

        private String country;
    }
}
