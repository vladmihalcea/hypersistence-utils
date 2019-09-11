package com.vladmihalcea.hibernate.type.interval;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
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
    @TypeDef(typeClass = PostgreSQLPeriodType.class, defaultForType = Period.class)
    public static class WorkShift extends BaseEntity {

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
