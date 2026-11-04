package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.junit.Test;

import java.time.ZoneId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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

    @Test
    public void testNullAndUpdates() {
        doInJPA(entityManager -> {
            UserPreferences preferences = new UserPreferences();
            preferences.setName("nullable");
            entityManager.persist(preferences);
        });

        doInJPA(entityManager -> {
            UserPreferences preferences = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(UserPreferences.class).load("nullable");
            assertNull(preferences.getZoneId());
            preferences.setZoneId(ZoneId.of("+05:30"));
        });

        doInJPA(entityManager -> {
            UserPreferences preferences = entityManager.createQuery(
                    "select p from UserPreferences p where p.zoneId = :zoneId", UserPreferences.class)
                .setParameter("zoneId", ZoneId.of("+05:30"))
                .getSingleResult();
            assertEquals(ZoneId.of("+05:30"), preferences.getZoneId());
            assertEquals("+05:30", entityManager.createNativeQuery(
                "select zone_id from user_preferences where name = 'nullable'").getSingleResult());
            preferences.setZoneId(ZoneId.of("UTC"));
        });

        doInJPA(entityManager -> {
            UserPreferences preferences = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(UserPreferences.class).load("nullable");
            assertEquals(ZoneId.of("UTC"), preferences.getZoneId());
            preferences.setZoneId(null);
        });

        doInJPA(entityManager -> {
            UserPreferences preferences = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(UserPreferences.class).load("nullable");
            assertNull(preferences.getZoneId());
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
