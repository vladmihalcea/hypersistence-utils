package com.vladmihalcea.hibernate.type.array;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.ParameterExpression;
import javax.persistence.criteria.Root;
import javax.sql.DataSource;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

/**
 * @author Stanislav Gubanov
 */
public class ArrayTypeSearchTest extends AbstractPostgreSQLIntegrationTest {

  @Override
  protected Class<?>[] entities() {
    return new Class<?>[]{
        Event.class,
    };
  }

  @Override
  public void init() {
    DataSource dataSource = newDataSource();
    try (Connection connection = dataSource.getConnection();
        Statement statement = connection.createStatement()) {
      statement.executeUpdate(
          "CREATE OR REPLACE FUNCTION fn_helper_array_contains(a integer[],b integer[])\n"
              + "  RETURNS boolean AS\n"
              + "$$\n"
              + "BEGIN\n"
              + "  return a @> b;\n"
              + "END;\n"
              + "$$ LANGUAGE 'plpgsql';"
      );
    } catch (SQLException e) {
      fail(e.getMessage());
    }
    super.init();
  }

  @Override
  protected DataSourceProvider dataSourceProvider() {
    return new PostgreSQLDataSourceProvider() {
      @Override
      public String hibernateDialect() {
        return PostgreSQL95ArrayDialect.class.getName();
      }
    };
  }

  @Test
  public void test() {
    doInJPA(entityManager -> {
      Event event = new Event();
      event.setId(1L);
      event.setValues(new int[]{12, 14, 16});
      entityManager.persist(event);
    });

    doInJPA(entityManager -> {
      CriteriaBuilder cb = entityManager.getCriteriaBuilder();
      CriteriaQuery<Event> cq = cb.createQuery(Event.class);
      Root<Event> root = cq.from(Event.class);
      cq.select(root);

      ParameterExpression containValues = cb.parameter(int[].class);
      cq.where(cb.equal(cb.function("fn_helper_array_contains", Boolean.class, root.get("values"), containValues), Boolean.TRUE));
      TypedQuery<Event> query = entityManager.createQuery(cq);
      int[] s = {12,16};
      query.setParameter(containValues, s);
      List<Event> events = query.getResultList();
      assertArrayEquals(new int[]{12, 14, 16}, events.get(0).getValues());
    });
  }

  @Entity(name = "Event")
  @Table(name = "event")
  public static class Event extends BaseEntity {

    @Type(type = "int-array")
    @Column(name = "values", columnDefinition = "integer[]")
    private int[] values;

    public int[] getValues() {
      return values;
    }

    public void setValues(int[] values) {
      this.values = values;
    }
  }
}
