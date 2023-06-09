package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Type;
import org.hibernate.cfg.AvailableSettings;
import org.junit.Test;

import java.time.YearMonth;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLYearMonthIdTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            YearMonthEntity.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(AvailableSettings.STATEMENT_BATCH_SIZE, 50);
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            YearMonthEntity entity = new YearMonthEntity();
            entity.setId(YearMonth.of(2016, 10));
            entity.setNotes("High-Performance Java Persistence");

            entityManager.persist(entity);
        });

        doInJPA(entityManager -> {
            YearMonthEntity entity = entityManager
                .find(YearMonthEntity.class, YearMonth.of(2016, 10));

            assertEquals("High-Performance Java Persistence", entity.getNotes());
        });
    }

    @Entity(name = "YearMonthEntity")
    public static class YearMonthEntity {

        @Id
        @Type(YearMonthIntegerType.class)
        private YearMonth id;

        private String notes;

        public YearMonth getId() {
            return id;
        }

        public void setId(YearMonth id) {
            this.id = id;
        }

        public String getNotes() {
            return notes;
        }

        public void setNotes(String notes) {
            this.notes = notes;
        }
    }
}
