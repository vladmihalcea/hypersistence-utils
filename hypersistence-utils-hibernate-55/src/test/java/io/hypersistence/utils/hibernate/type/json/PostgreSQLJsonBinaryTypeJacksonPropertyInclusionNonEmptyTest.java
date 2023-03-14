package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.junit.Test;

import javax.persistence.*;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;


public class PostgreSQLJsonBinaryTypeJacksonPropertyInclusionNonEmptyTest extends AbstractPostgreSQLIntegrationTest {
    @Override
    protected Class<?>[] entities() {
        return new Class[]{
            MyEntity.class,
        };
    }

    @Override
    public void init() {
        System.setProperty(
                Configuration.PROPERTIES_FILE_PATH,
                "PostgreSQLJsonBinaryTypeJacksonPropertyInclusionNonEmptyTest.properties"
        );
        super.init();
    }

    private MyEntity _myEntity;

    @Override
    protected void afterInit() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            Inside inside = new Inside();
            inside.setAttribute("JPA");
            MyEntity entity = new MyEntity();
            entity.addInside(inside);

            entityManager.persist(entity);

            _myEntity = entity;
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertInsertCount(1);
    }

    @Test
    public void testLoad() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            MyEntity myEntity = entityManager.find(MyEntity.class, _myEntity.getId());
            myEntity.clearInside();
            entityManager.createQuery("select id from MyEntity where flag=false").getSingleResult();
        });

        SQLStatementCountValidator.assertTotalCount(3);
        SQLStatementCountValidator.assertSelectCount(2);
        SQLStatementCountValidator.assertUpdateCount(1);
    }

    @TypeDefs({@TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)})
    @Entity(name = "MyEntity")
    @Table(name = "my_entity")
    public static class MyEntity {
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Id
        private Long id;

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private Outside outside;

        @Column
        private boolean flag;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Outside getOutside() {
            return outside;
        }

        public void addInside(Inside inside) {
            if (outside == null) {
                outside = new Outside();
            }
            outside.addInside(inside);
        }

        public void clearInside() {
           outside.clearInside();
        }
    }

    public static class Outside {
        private Map<Long, Inside> insides;

        public Map<Long, Inside> getInsides() {
            return insides;
        }

        public void addInside(Inside inside) {
            if (insides == null) {
                insides = new LinkedHashMap<>();
            }
            insides.put(new Random().nextLong(), inside);
        }

        public void clearInside() {
            if (insides != null) {
                insides.clear();
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Outside outside = (Outside) o;
            return Objects.equals(insides, outside.insides);
        }

        @Override
        public int hashCode() {
            return Objects.hash(insides);
        }
    }

    public static class Inside {
       private String attribute;

        public String getAttribute() {
            return attribute;
        }

        public void setAttribute(String attribute) {
            this.attribute = attribute;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Inside inside = (Inside) o;
            return Objects.equals(attribute, inside.attribute);
        }

        @Override
        public int hashCode() {
            return Objects.hash(attribute);
        }
    }
}
