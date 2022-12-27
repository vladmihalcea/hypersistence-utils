package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.util.AbstractMySQLIntegrationTest;
import org.junit.Test;

import javax.persistence.*;
import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class MySQLParametrizedJsonTypeTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            PropertyHolder.class,
        };
    }

    @Override
    protected String[] packages() {
        return new String[]{
            PropertyHolder.class.getPackage().getName()
        };
    }

    @Override
    protected List<org.hibernate.type.Type> additionalTypes() {

        ParametrizedJsonStringType jsonType = new ParametrizedJsonStringType("json-hashmap", HashMap.class, new Class[]{String.class, Object.class});
        return Collections.singletonList(jsonType);
    }

    @Override
    protected boolean nativeHibernateSessionFactoryBootstrap() {
        return true;
    }

    @Test
    public void test() {
        final AtomicReference<PropertyHolder> eventHolder = new AtomicReference<>();

        doInJPA(entityManager -> {
            PropertyHolder propertyHolder = new PropertyHolder();
            propertyHolder.setId(1L);
            propertyHolder.addProperty("key1", "value");
            propertyHolder.addProperty("key2", 123456789);
            propertyHolder.addProperty("key3", new POJO("one", "two"));
            entityManager.persist(propertyHolder);

            eventHolder.set(propertyHolder);
        });

        doInJPA(entityManager -> {
            PropertyHolder propertyHolder = entityManager.find(PropertyHolder.class, eventHolder.get().getId());
            assertEquals("value", propertyHolder.getProperty("key1"));
            assertEquals(123456789, propertyHolder.getProperty("key2"));
            assertEquals(new POJO("one", "two"), propertyHolder.getProperty("key3"));

        });
    }

    public static class ParametrizedJsonStringType extends JsonStringType {

        private String name;

        public ParametrizedJsonStringType(String name, final Type rawType, final Type[] actualTypeArguments) {
            super(createObjectMapper(), createParameterizedType(rawType, actualTypeArguments, null));
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        private static ObjectMapper createObjectMapper() {
            ObjectMapper objectMapper = new ObjectMapper();
            objectMapper.enableDefaultTyping();
            return objectMapper;
        }

        private static ParameterizedType createParameterizedType(final Type rawType, final Type[] actualTypeArguments, final Type ownerType) {
            return new ParameterizedType() {

                @Override
                public Type[] getActualTypeArguments() {
                    return actualTypeArguments;
                }

                @Override
                public Type getRawType() {
                    return rawType;
                }

                @Override
                public Type getOwnerType() {
                    return ownerType;
                }

                @Override
                public boolean equals(Object obj) {
                    if (!(obj instanceof ParameterizedType)) {
                        return false;
                    }
                    ParameterizedType other = (ParameterizedType) obj;
                    return Arrays.equals(getActualTypeArguments(), other.getActualTypeArguments()) && safeEquals(getRawType(), other.getRawType()) && safeEquals(getOwnerType(), other.getOwnerType());
                }

                @Override
                public int hashCode() {
                    return safeHashCode(getActualTypeArguments()) ^ safeHashCode(getRawType()) ^ safeHashCode(getOwnerType());
                }
            };
        }

        private static boolean safeEquals(Type t1, Type t2) {
            if (t1 == null) {
                return t2 == null;
            }
            return t1.equals(t2);
        }

        private static int safeHashCode(Object o) {
            if (o == null) {
                return 1;
            }
            return o.hashCode();
        }

    }

    @Entity(name = "PropertyHolder")
    @Table(name = "propertyholder")
    public static class PropertyHolder {

        @Id
        private Long id;

        @Version
        private Integer version;

        @org.hibernate.annotations.Type(type = "json-hashmap")
        @Column(columnDefinition = "json")
        private Map<String, Object> props = new HashMap<>();


        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public Integer getVersion() {
            return version;
        }

        public Map<String, Object> getProps() {
            return props;
        }

        public void setProps(Map<String, Object> props) {
            this.props = props;
        }

        public void addProperty(String key, Object value) {
            props.put(key, value);
        }

        public Object getProperty(String key) {
            return props.get(key);
        }
    }

    public static class POJO implements Serializable {

        private static final long serialVersionUID = -5009179810689351758L;

        private String first;
        private String second;

        public POJO() {
        }

        public POJO(String first, String second) {
            this.first = first;
            this.second = second;
        }

        public String getFirst() {
            return first;
        }

        public void setFirst(String first) {
            this.first = first;
        }

        public String getSecond() {
            return second;
        }

        public void setSecond(String second) {
            this.second = second;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((first == null) ? 0 : first.hashCode());
            result = prime * result + ((second == null) ? 0 : second.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            POJO other = (POJO) obj;
            if (first == null) {
                if (other.first != null)
                    return false;
            } else if (!first.equals(other.first))
                return false;
            if (second == null) {
                if (other.second != null)
                    return false;
            } else if (!second.equals(other.second))
                return false;
            return true;
        }
    }

}
