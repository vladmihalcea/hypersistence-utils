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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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
    public void test() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event nullEvent = new Event();
                nullEvent.setId(0L);
                entityManager.persist(nullEvent);

                Event event = new Event();
                event.setId(1L);
                event.setSensorNames(new String[]{"Temperature", "Pressure"});
                event.setSensorValues(new int[]{12, 756});
                event.setSensorLongValues(new long[]{42L, 9223372036854775800L});
                event.setSensorStates(new SensorState[] {SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN});
                entityManager.persist(event);

                return null;
            }
        });
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames());
                assertArrayEquals(new int[]{12, 756}, event.getSensorValues());
                assertArrayEquals(new long[]{42L, 9223372036854775800L}, event.getSensorLongValues());
                assertArrayEquals(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN}, event.getSensorStates());

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

        @Type(type = "string-array")
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private String[] sensorNames;

        @Type(type = "int-array")
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private int[] sensorValues;
        
        @Type(type = "long-array")
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private long[] sensorLongValues;
        
        @Type( type = "sensor-state-array")
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private SensorState[] sensorStates;

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
