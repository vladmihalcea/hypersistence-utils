package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.time.MonthDay;

import static org.junit.Assert.assertEquals;

/**
 * @author Mladen Savic (mladensavic94@gmail.com)
 */
public class PostgreSQLMonthDayDateTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{Season.class};
    }

    @Test
    public void testCreationAndFetchById(){
        Season season = createEntity("Summer", MonthDay.of(6,21), MonthDay.of(9,22));

        doInJPA(entityManager -> {
            Season summer = entityManager.unwrap(Session.class).find(Season.class, season.getId());

            assertEquals(summer.getBeginningOfSeason(), MonthDay.of(6,21));
            assertEquals(summer.getEndOfSeason(), MonthDay.of(9,22));
        });

    }

    @Test
    public void testFetchWithQuery(){
        createEntity("Winter", MonthDay.of(12,21), MonthDay.of(3,20));

        doInJPA(entityManager -> {
            Season seasonQ = entityManager
                    .createQuery(
                            "select s " +
                                    "from Season s " +
                                    "where " +
                                    "s.beginningOfSeason = :beginningOfSeason", Season.class)
                    .setParameter("beginningOfSeason", MonthDay.of(12,21))
                    .getSingleResult();

            assertEquals("Winter", seasonQ.getName());
        });
    }

    public Season createEntity(String name, MonthDay beginning, MonthDay end){
        Season season = new Season();
        season.setName(name);
        season.setBeginningOfSeason(beginning);
        season.setEndOfSeason(end);

        doInJPA(entityManager -> {
            entityManager.persist(season);
        });

        return season;
    }


    @Entity(name = "Season")
    @Table(name = "season")
    @TypeDef(typeClass = MonthDayDateType.class, defaultForType = MonthDay.class)
    public static class Season {

        @Id
        @GeneratedValue
        private Long id;
        private String name;
        @Column(name = "beginning_of_season", columnDefinition = "date")
        private MonthDay beginningOfSeason;
        @Column(name = "end_of_season", columnDefinition = "date")
        private MonthDay endOfSeason;

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

        public MonthDay getBeginningOfSeason() {
            return beginningOfSeason;
        }

        public void setBeginningOfSeason(MonthDay beginningOfSeason) {
            this.beginningOfSeason = beginningOfSeason;
        }

        public MonthDay getEndOfSeason() {
            return endOfSeason;
        }

        public void setEndOfSeason(MonthDay endOfSeason) {
            this.endOfSeason = endOfSeason;
        }
    }
}
