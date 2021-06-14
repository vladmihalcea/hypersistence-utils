package com.vladmihalcea.hibernate.type.interval;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.TypeDef;
import org.junit.Assert;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.time.Duration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

/**
 * Tests for {@see PostgreSQLIntervalType} Hibernate type.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class PostgreSQLIntervalTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class[]{WorkShift.class};
    }

    @Test
    public void test() {
        Duration duration = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4).plusNanos(5000);

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
    @TypeDef(typeClass = PostgreSQLIntervalType.class, defaultForType = Duration.class)
    public static class WorkShift extends BaseEntity {

        @Column(columnDefinition = "interval")
        private Duration duration;

        public Duration getDuration() {
            return duration;
        }

        public void setDuration(Duration duration) {
            this.duration = duration;
        }
    }
}
