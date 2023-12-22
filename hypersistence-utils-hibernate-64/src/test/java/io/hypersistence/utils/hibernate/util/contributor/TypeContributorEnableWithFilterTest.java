package io.hypersistence.utils.hibernate.util.contributor;

import io.hypersistence.utils.hibernate.type.HibernateTypesContributor;
import io.hypersistence.utils.hibernate.type.basic.Inet;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLInetType;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.usertype.UserType;
import org.junit.Test;

import java.util.Properties;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class TypeContributorEnableWithFilterTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Event.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(
            HibernateTypesContributor.TYPES_CONTRIBUTOR_FILTER,
            (Predicate<UserType>) userType -> (userType instanceof PostgreSQLInetType)
        );
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Event event = new Event();
            event.setId(1L);
            event.setIp("192.168.0.123/24");
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);
            assertEquals("192.168.0.123/24", event.getIp().getAddress());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        private Long id;

        @Column(name = "ip", columnDefinition = "inet")
        private Inet ip;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Inet getIp() {
            return ip;
        }

        public void setIp(String address) {
            this.ip = new Inet(address);
        }
    }
}
