package io.hypersistence.utils.hibernate.type.range;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static io.hypersistence.utils.hibernate.type.range.Range.zonedDateTimeRange;
import static org.junit.Assert.assertEquals;

/**
 * @author Arun Mohandas
 */
public class ZonedDateTimeMilliSecondTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class[]{
                Restriction.class
        };
    }

    @Test
    public void test() {
        validateTest(
                zonedDateTimeRange(
                        "[" +
                                "\"2018-05-03T10:15:30.127110+12:00\"," +
                                "\"2018-12-03T10:15:30.127111+12:00\"" +
                        "]"
                )
        );

        validateTest(
                zonedDateTimeRange(
                        "[" +
                                "\"2018-05-03T10:15:30.127100+12:00\"," +
                                "\"2018-12-03T10:15:30.127111+12:00\"" +
                        "]"
                )
        );

        validateTest(
                zonedDateTimeRange(
                        "[" +
                                "\"2018-05-03T10:15:30.127000+12:00\"," +
                                "\"2018-12-03T10:15:30.127111+12:00\"" +
                        "]"
                )
        );
    }

    private void validateTest(Range<ZonedDateTime> tsTz) {
        Restriction _restriction = doInJPA(entityManager -> {
            Restriction restriction = new Restriction();
            restriction.setRangeZonedDateTime(tsTz);
            entityManager.persist(restriction);

            return restriction;
        });

        doInJPA(entityManager -> {
            Restriction restriction = entityManager.find(Restriction.class, _restriction.getId());

            ZoneId zone = restriction.getRangeZonedDateTime().lower().getZone();

            ZonedDateTime lower = tsTz.lower().withZoneSameInstant(zone);

            assertEquals(lower, restriction.getRangeZonedDateTime().lower());
            assertEquals(LocalDateTime.parse("2018-12-03T10:15:30").atZone(ZoneId.systemDefault()).getOffset(),
                    restriction.getRangeZonedDateTime().upper().getOffset());
        });
    }

    @Entity(name = "AgeRestriction")
    @Table(name = "age_restriction")
    public static class Restriction {

        @Id
        @GeneratedValue
        private Long id;

        @Type(PostgreSQLRangeType.class)
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