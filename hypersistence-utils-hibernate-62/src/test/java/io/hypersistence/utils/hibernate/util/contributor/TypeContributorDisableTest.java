package io.hypersistence.utils.hibernate.util.contributor;

import io.hypersistence.utils.hibernate.type.HibernateTypesContributor;
import io.hypersistence.utils.hibernate.type.basic.Inet;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.common.ExceptionUtil;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class TypeContributorDisableTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Event.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.setProperty(
            HibernateTypesContributor.ENABLE_TYPES_CONTRIBUTOR,
            Boolean.FALSE.toString()
        );
    }

    @Test
    public void test() {
        try {
            doInJPA(entityManager -> {
                Event event = new Event();
                event.setId(1L);
                event.setIp("192.168.0.123/24");
                entityManager.persist(event);
            });

            fail("Should throw Exception");
        } catch (Exception e) {
            assertTrue(ExceptionUtil.rootCause(e).getMessage().contains("column \"ip\" is of type inet but expression is of type bytea"));
        }
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
