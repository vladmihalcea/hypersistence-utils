package com.vladmihalcea.hibernate.type.json;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.model.Location;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Inheritance;
import javax.persistence.InheritanceType;

public class PostgreSQLJsonBinaryTypeUnionTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class[] {
                Base.class,
                Sub1.class,
                Sub2.class
        };
    }

    @Test
    public void testUnionFetch() {
        doInJPA(em -> {
            em.createQuery("SELECT a FROM Base a", Base.class).getResultList();
        });
    }

    @Entity(name = "Base")
    @Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
    public static abstract class Base extends BaseEntity {

    }

    @Entity
    public static abstract class Sub1 extends Base {

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private Location location;

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }

    }

    @Entity
    public static abstract class Sub2 extends Base {}

}
