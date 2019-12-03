package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Table;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class ArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class,
        };
    }

    @Override
    public void init() {
      DataSource dataSource = newDataSource();
      Connection connection = null;
      try {
          connection = dataSource.getConnection();
          Statement statement = null;
          try {
              statement = connection.createStatement();
              try {
                  statement.executeUpdate(
                          "DROP TYPE sensor_state CASCADE"
                  );
              } catch (SQLException ignore) {
              }
              statement.executeUpdate(
                      "CREATE TYPE sensor_state AS ENUM ('ONLINE', 'OFFLINE', 'UNKNOWN')"
              );
              statement.executeUpdate(
                  "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\""
              );
          }
          finally {
              if (statement != null) {
                  try {
                      statement.close();
                  } catch (SQLException e) {
                      fail(e.getMessage());
                  }
              }
          }
      } catch (SQLException e) {
          fail(e.getMessage());
      } finally {
          if (connection != null) {
              try {
                  connection.close();
              } catch (SQLException e) {
                  fail(e.getMessage());
              }
          }
      }
      super.init();
    }

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider() {
            @Override
            public String hibernateDialect() {
                return PostgreSQL9ArrayDialect.class.getName();
            }
        };
    }

    @Test
    public void test() throws ParseException {

        SimpleDateFormat formater = new SimpleDateFormat("yyyy-MM-dd");

        String date1String = formater.format(new Date());
        String date2String = formater.format(new Date(1));

        final Date date1 = formater.parse(date1String);
        final Date date2 = formater.parse(date2String);

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event nullEvent = new Event();
                nullEvent.setId(0L);
                entityManager.persist(nullEvent);

                Event event = new Event();
                event.setId(1L);
                event.setSensorIds(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")});
                event.setSensorNames(new String[]{"Temperature", "Pressure"});
                event.setSensorValues(new int[]{12, 756});
                event.setSensorLongValues(new long[]{42L, 9223372036854775800L});
                event.setSensorStates(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN});
                event.setDateValues(new Date[]{date1, date2});
                entityManager.persist(event);

                return null;
            }
        });
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertArrayEquals(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds());
                assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames());
                assertArrayEquals(new int[]{12, 756}, event.getSensorValues());
                assertArrayEquals(new long[]{42L, 9223372036854775800L}, event.getSensorLongValues());
                assertArrayEquals(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN}, event.getSensorStates());
                assertArrayEquals(new Date[]{date1, date2}, event.getDateValues());

                return null;
            }
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    @TypeDef(name = "sensor-state-array", typeClass = EnumArrayType.class, parameters = {
        @Parameter(name = EnumArrayType.SQL_ARRAY_TYPE, value = "sensor_state")}
    )
    public static class Event extends BaseEntity {
        @Type(type = "uuid-array")
        @Column(name = "sensor_ids", columnDefinition = "uuid[]")
        private UUID[] sensorIds;

        @Type(type = "string-array")
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private String[] sensorNames;

        @Type(type = "int-array")
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private int[] sensorValues;

        @Type(type = "long-array")
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private long[] sensorLongValues;

        @Type(type = "date-array")
        @Column(name = "date_values", columnDefinition = "date[]")
        private Date[] dateValues;

        @Type(type = "sensor-state-array")
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private SensorState[] sensorStates;

        public UUID[] getSensorIds() {
            return sensorIds;
        }

        public void setSensorIds(UUID[] sensorIds) {
            this.sensorIds = sensorIds;
        }

        public String[] getSensorNames() {
            return sensorNames;
        }

        public void setSensorNames(String[] sensorNames) {
            this.sensorNames = sensorNames;
        }

        public int[] getSensorValues() {
            return sensorValues;
        }

        public void setSensorValues(int[] sensorValues) {
            this.sensorValues = sensorValues;
        }

        public long[] getSensorLongValues() {
            return sensorLongValues;
        }

        public void setSensorLongValues(long[] sensorLongValues) {
            this.sensorLongValues = sensorLongValues;
        }

        public Date[] getDateValues() {
            return dateValues;
        }

        public void setDateValues(Date[] dateValues) {
            this.dateValues = dateValues;
        }

        public SensorState[] getSensorStates() {
            return sensorStates;
        }

        public void setSensorStates(SensorState[] sensorStates) {
            this.sensorStates = sensorStates;
        }

    }
    
    public enum SensorState {
      ONLINE, OFFLINE, UNKNOWN;
    }

}
