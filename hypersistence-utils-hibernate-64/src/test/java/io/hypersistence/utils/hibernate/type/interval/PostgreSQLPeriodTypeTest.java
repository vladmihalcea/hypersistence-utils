package io.hypersistence.utils.hibernate.type.interval;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.time.Period;

import static org.junit.Assert.assertEquals;

/**
 * Tests for {@see PostgreSQLIntervalType} Hibernate type.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class PostgreSQLPeriodTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class[]{WorkShift.class};
    }

    @Test
    public void test() {
        Period duration = Period.of(1, 2, 3);

        doInJPA(entityManager -> {
            WorkShift intervalEntity = new WorkShift();
            intervalEntity.setId(1L);
            intervalEntity.setDuration(duration);

            entityManager.persist(intervalEntity);
        });

        doInJPA(entityManager -> {
            WorkShift result = entityManager.find(WorkShift.class, 1L);
            assertEquals(duration, result.getDuration());
        });
    }

    @Entity(name = "WorkShift")
    public static class WorkShift extends BaseEntity {

        @Type(PostgreSQLPeriodType.class)
        @Column(columnDefinition = "interval")
        private Period duration;

        public Period getDuration() {
            return duration;
        }

        public void setDuration(Period duration) {
            this.duration = duration;
        }
    }
}
