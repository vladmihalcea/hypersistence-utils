package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.time.ZoneId;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@see ZoneId} Hibernate mapping.
 *
 * @author stonio
 */
public class ZoneIdTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{UserPreferences.class};
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            UserPreferences UserPreferences = new UserPreferences();
            UserPreferences.setName("vladmihalcea.com");
            UserPreferences.setZoneId(ZoneId.of("Europe/Bucharest"));

            entityManager.persist(UserPreferences);
        });

        doInJPA(entityManager -> {
            UserPreferences userPreferences = entityManager
                .unwrap(Session.class)
                .bySimpleNaturalId(UserPreferences.class)
                .load("vladmihalcea.com");

            assertEquals(ZoneId.of("Europe/Bucharest"), userPreferences.getZoneId());
        });

        doInJPA(entityManager -> {
            UserPreferences prefs = entityManager
                .createQuery(
                    "select p " +
                    "from UserPreferences p " +
                    "where " +
                    " p.zoneId = :zoneId", UserPreferences.class)
                .setParameter("zoneId", ZoneId.of("Europe/Bucharest"))
                .getSingleResult();

            assertEquals("vladmihalcea.com", prefs.getName());
        });
    }

    @Entity(name = "UserPreferences")
    @Table(name = "user_preferences")
    public static class UserPreferences {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String name;

        @Type(ZoneIdType.class)
        @Column(name = "zone_id", length= 40)
        private ZoneId zoneId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public ZoneId getZoneId() {
            return zoneId;
        }

        public void setZoneId(ZoneId zoneId) {
            this.zoneId = zoneId;
        }
    }
}
