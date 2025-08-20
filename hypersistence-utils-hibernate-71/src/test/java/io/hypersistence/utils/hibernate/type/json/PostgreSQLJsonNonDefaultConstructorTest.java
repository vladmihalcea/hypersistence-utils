package io.hypersistence.utils.hibernate.type.json;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class PostgreSQLJsonNonDefaultConstructorTest extends AbstractPostgreSQLIntegrationTest {

  @Override
  protected Class<?>[] entities() {
    return new Class<?>[] {
        Container.class
    };
  }

  @Test
  public void testJsonBinaryTypeEquality() {
    doInJPA(entityManager -> {
      entityManager.persist(
          new Container()
              .setName("Non-Default Constructor")
              .setSettings(new Settings(true, List.of(new Label("Non-Default"), new Label("Constructor"))))
      );
    });
    doInJPA(entityManager -> {
      List<Container> containers = entityManager.createQuery("select c from Container c", Container.class)
          .getResultList();
      Container matched = containers.stream()
          .filter(container -> container.getName().equals("Non-Default Constructor"))
          .findFirst()
          .orElseThrow();
      assertEquals("Non-Default Constructor", matched.getName());
    });
  }

  @Entity(name = "Container")
  @Table(name = "container")
  public static class Container {

    @Id
    @GeneratedValue
    private Long id;
    private String name;
    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    private Settings settings;

    public String getName() {
      return name;
    }

    public Container setName(String name) {
      this.name = name;
      return this;
    }

    public Settings getSettings() {
      return settings;
    }

    public Container setSettings(Settings settings) {
      this.settings = settings;
      return this;
    }
  }

  public static class Settings implements Serializable {

    private boolean enabled;
    private List<Label> labels;

    public Settings() {
    }

    public Settings(boolean enabled, List<Label> labels) {
      this.enabled = enabled;
      this.labels = new ArrayList<>(labels);
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public List<Label> getLabels() {
      return labels;
    }

    public void setLabels(List<Label> labels) {
      this.labels = labels;
    }
  }

  public static class Label implements Serializable {

    private String code;

    @JsonCreator
    public Label(@JsonProperty("code") String code) {
      this.code = code;
    }

    public String getCode() {
      return code;
    }

    public void setCode(String code) {
      this.code = code;
    }
  }
}
