package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.AbstractMySQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.time.MonthDay;
import java.util.ArrayList;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;

/**
 * @author Mladen Savic (mladensavic94@gmail.com)
 */
public class MySQLMonthDayDateTest extends AbstractMySQLIntegrationTest {

    public static final String COLUMN_TYPE = "date";

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{Season.class};
    }

    private TimeZone defaultTimeZone;

    @Override
    protected void afterInit() {
        defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"));
    }

    @Override
    public void destroy() {
        super.destroy();
        TimeZone.setDefault(defaultTimeZone);
    }

    @Test
    public void testCreationAndFetchById(){
        Season season = createEntity("Summer", MonthDay.of(6,21), MonthDay.of(9,22));

        doInJPA(entityManager -> {
            Season summer = entityManager.unwrap(Session.class).find(Season.class, season.getId());

            assertEquals(summer.getBeginningOfSeason(), MonthDay.of(6,21));
            assertEquals(summer.getEndOfSeason(), MonthDay.of(9,22));
        });

        assertEquals(COLUMN_TYPE, getColumnType("end_of_season") );
        assertEquals(COLUMN_TYPE, getColumnType("beginning_of_season") );

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

        assertEquals(COLUMN_TYPE, getColumnType("end_of_season") );
        assertEquals(COLUMN_TYPE, getColumnType("beginning_of_season") );
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

    public String getColumnType(String column){
        ArrayList<String> results = new ArrayList<>(1);
        doInJPA(entityManager -> {
            Object result = entityManager.createNativeQuery("SELECT data_type FROM information_schema.columns WHERE \n" +
                    "table_name = 'season' AND column_name = :column_name")
                    .setParameter("column_name", column)
                    .getSingleResult();
            results.add((String) result);
        });
        return results.get(0);
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
