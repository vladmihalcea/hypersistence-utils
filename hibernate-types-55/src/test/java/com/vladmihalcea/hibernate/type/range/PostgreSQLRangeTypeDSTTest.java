package com.vladmihalcea.hibernate.type.range;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static com.vladmihalcea.hibernate.type.range.Range.infinite;
import static com.vladmihalcea.hibernate.type.range.Range.zonedDateTimeRange;
import static org.junit.Assert.assertEquals;

/**
 * @author Edgar Asatryan
 */
public class PostgreSQLRangeTypeDSTTest extends AbstractPostgreSQLIntegrationTest {

    // updated to cross DST boundary (31/9/2018)
    private final Range<ZonedDateTime> tsTz = zonedDateTimeRange("[\"2018-05-03T10:15:30+12:00\",\"2018-12-03T10:15:30+12:00\"]");

    @Override
    protected Class<?>[] entities() {
        return new Class[]{
            Restriction.class
        };
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Restriction restriction = new Restriction();
            restriction.setId(1L);
            restriction.setRangeZonedDateTime(tsTz);
            entityManager.persist(restriction);

            return restriction;
        });

        doInJPA(entityManager -> {
            Restriction ar = entityManager.find(Restriction.class, 1L);

            ZoneId zone = ar.getRangeZonedDateTime().lower().getZone();

            ZonedDateTime lower = tsTz.lower().withZoneSameInstant(zone);

            assertEquals(lower, ar.getRangeZonedDateTime().lower());
            assertEquals(LocalDateTime.parse("2018-12-03T10:15:30").atZone(ZoneId.systemDefault()).getOffset(), ar.getRangeZonedDateTime().upper().getOffset());
        });
    }

    @Entity(name = "AgeRestriction")
    @Table(name = "age_restriction")
    @TypeDef(name = "range", typeClass = PostgreSQLRangeType.class, defaultForType = Range.class)
    public static class Restriction {

        @Id
        private Long id;

        @Column(name = "r_tstzrange", columnDefinition = "tstzrange")
        private Range<ZonedDateTime> rangeZonedDateTime;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Range<ZonedDateTime> getRangeZonedDateTime() {
            return rangeZonedDateTime;
        }

        public void setRangeZonedDateTime(Range<ZonedDateTime> rangeZonedDateTime) {
            this.rangeZonedDateTime = rangeZonedDateTime;
        }
    }
}