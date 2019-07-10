package com.vladmihalcea.hibernate.type.interval;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Assert;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import java.time.Duration;

public class PostgreSQLIntervalTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class[] { IntervalEntity.class };
    }

    @Test
    public void test() {
        doInJPA(em -> {
            IntervalEntity intervalEntity = new IntervalEntity();
            intervalEntity.setId(1L);
            Duration duration = Duration.ofDays(1).plusHours(2).plusMinutes(3).plusSeconds(4);
            intervalEntity.setInterval(duration);
            em.persist(intervalEntity);
            em.flush();
            em.clear();

            IntervalEntity result = em.createQuery("SELECT a from IntervalEntity a", IntervalEntity.class).getSingleResult();
            Assert.assertEquals(duration, result.getInterval());
        });
    }

    @Entity(name = "IntervalEntity")
    @TypeDef(name = "interval", typeClass = PostgreSQLIntervalType.class, defaultForType = Duration.class)
    public static class IntervalEntity extends BaseEntity {

        @Type(type = "interval")
        @Column(columnDefinition = "interval")
        private Duration interval;

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }
    }
}
