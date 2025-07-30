package io.hypersistence.utils.hibernate.type.json;

import java.io.Serializable;
import java.util.List;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class Issue799 extends AbstractPostgreSQLIntegrationTest {

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
              .setName("name-1")
              .setSettings(
                  new Settings(
                      true,
                      List.of("node1", "node2"),
                      List.of(new Label("HIBERNATE"), new Label("TYPE")))
              )
      );
    });
    doInJPA(entityManager -> {
      List<Container> containers = entityManager.createQuery("select c from Container c", Container.class)
          .getResultList();
      Container matched = containers.stream()
          .filter(container -> container.getName().equals("name-1"))
          .findFirst()
          .orElseThrow();
      assertEquals("name-1", matched.getName());
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
    private List<String> trustedNodes;
    private List<Label> availableAccounts;

    public Settings() {
    }

    public Settings(boolean enabled, List<String> trustedNodes, List<Label> availableAccounts) {
      this.enabled = enabled;
      this.trustedNodes = trustedNodes;
      this.availableAccounts = availableAccounts;
    }

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public List<String> getTrustedNodes() {
      return trustedNodes;
    }

    public void setTrustedNodes(List<String> trustedNodes) {
      this.trustedNodes = trustedNodes;
    }

    public List<Label> getAvailableAccounts() {
      return availableAccounts;
    }

    public void setAvailableAccounts(List<Label> availableAccounts) {
      this.availableAccounts = availableAccounts;
    }
  }

  public static class Label implements Serializable {

    private String code;

    // If add default constr test passes

    public Label(String code) {
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
