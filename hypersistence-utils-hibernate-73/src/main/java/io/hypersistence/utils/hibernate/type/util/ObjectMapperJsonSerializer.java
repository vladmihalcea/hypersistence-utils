package io.hypersistence.utils.hibernate.type.util;

import tools.jackson.databind.JavaType;
import tools.jackson.databind.JsonNode;
import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.type.SerializationException;

import java.io.Serializable;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.Map;

/**
 * @author Vlad Mihalcea
 */
public class ObjectMapperJsonSerializer implements JsonSerializer {

    private final ObjectMapperWrapper objectMapperWrapper;

    public ObjectMapperJsonSerializer(ObjectMapperWrapper objectMapperWrapper) {
        this.objectMapperWrapper = objectMapperWrapper;
    }

    @Override
    public <T> T clone(T object) {
        if (object instanceof String) {
            return object;
        } else if (object instanceof Collection) {
            Class commonElementType = findCommonElementType((Collection) object);
            if (commonElementType != null && !(Serializable.class.isAssignableFrom(commonElementType))) {
                JavaType type = objectMapperWrapper.getTypeFactory()
                    .constructParametricType(
                        object.getClass(),
                        commonElementType
                    );
                return objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), type);
            }
        } else if (object instanceof Map) {
            Map.Entry<Class, Class> commonElementType = findCommonElementType((Map) object);
            if (commonElementType != null) {
                Class commonKeyClass = commonElementType.getKey();
                Class commonValueClass = commonElementType.getValue();

                boolean isCommonKeyClassCoreOrNotSerializable = isCoreJavaType(commonKeyClass) || !Serializable.class.isAssignableFrom(commonKeyClass);
                boolean isCommonValueClassCoreOrNotSerializable = isCoreJavaType(commonValueClass) || !Serializable.class.isAssignableFrom(commonValueClass);
                if (
                        (isCommonKeyClassCoreOrNotSerializable || isCommonValueClassCoreOrNotSerializable)
                                && (object.getClass().getTypeParameters().length == 2)
                ) {
                    JavaType type = objectMapperWrapper.getTypeFactory().constructParametricType(
                        object.getClass(),
                        commonKeyClass,
                        commonValueClass
                    );
                    return objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), type);
                }
            }
        } else if (object instanceof JsonNode) {
            return (T) ((JsonNode) object).deepCopy();
        }

        if (object instanceof Serializable) {
            try {
                return (T) SerializationHelper.clone((Serializable) object);
            } catch (SerializationException e) {
                //it is possible that object itself implements java.io.Serializable, but underlying structure does not
                //in this case we switch to the other JSON marshaling strategy which doesn't use the Java serialization
            }
        }

        return jsonClone(object);
    }

    private boolean isCoreJavaType(Class<?> type) {
        Package typePackage = type.getPackage();
        return typePackage != null && typePackage.getName().startsWith("java");
    }

    private Class findCommonElementType(Collection collection) {
        Class commonElementType = null;
        for (Object element : collection) {
            if (element != null) {
                if(commonElementType == null) {
                    commonElementType = element.getClass();
                } else {
                    commonElementType = resolveCommonElementType(commonElementType, element.getClass());
                    if(commonElementType == null) {
                        return null;
                    }
                }
            }
        }
        return commonElementType;
    }

    private Class resolveCommonElementType(Class commonElementType, Class elementClass) {
        if(commonElementType.isAssignableFrom(elementClass) &&
           !Modifier.isAbstract(commonElementType.getModifiers())) {
            return commonElementType;
        } else {
            Class<?> superclass = commonElementType.getSuperclass();
            if(!superclass.equals(Object.class)) {
                return resolveCommonElementType(superclass, elementClass);
            } else {
                return null;
            }
        }
    }

    private Map.Entry<Class, Class> findCommonElementType(Map<?, ?> map) {
        Map.Entry<Class, Class> commonElementType = null;
        for (Map.Entry elementEntry : map.entrySet()) {
            if (elementEntry.getKey() != null && elementEntry.getValue() != null) {
                Map.Entry<Class, Class> elementClass = new AbstractMap.SimpleEntry<>(
                    elementEntry.getKey().getClass(),
                    elementEntry.getValue().getClass()
                );
                if(commonElementType == null) {
                    commonElementType = elementClass;
                } else {
                    commonElementType = resolveCommonElementType(commonElementType, elementClass);
                    if(commonElementType == null) {
                        return null;
                    }
                }
            }
        }
        return commonElementType;
    }

    private Map.Entry<Class, Class> resolveCommonElementType(Map.Entry<Class, Class> commonElementType, Map.Entry<Class, Class> elementClass) {
        Class commonKeyClass = commonElementType.getKey();
        Class commonValueClass = commonElementType.getValue();
        if(commonKeyClass.isAssignableFrom(elementClass.getKey()) &&
            !isAbstractType(commonKeyClass) &&
            commonValueClass.isAssignableFrom(elementClass.getValue()) &&
            !isAbstractType(commonValueClass)) {
            return commonElementType;
        } else {
            Class<?> keySuperclass = commonKeyClass.equals(elementClass.getKey()) ?
                commonKeyClass :
                commonKeyClass.getSuperclass();
            Class<?> valueSuperclass = commonValueClass.equals(elementClass.getValue()) ?
                commonValueClass :
                commonValueClass.getSuperclass();
            if(!keySuperclass.equals(Object.class) && !keySuperclass.equals(commonKeyClass) &&
               !valueSuperclass.equals(Object.class) && !valueSuperclass.equals(commonValueClass)) {
                return resolveCommonElementType(
                    new AbstractMap.SimpleEntry<>(
                        keySuperclass,
                        valueSuperclass
                    ),
                    elementClass
                );
            } else {
                return null;
            }
        }
    }

    private boolean isAbstractType(Class type) {
        return Modifier.isAbstract(type.getModifiers()) && !type.isArray();
    }

    private <T> T jsonClone(T object) {
        return objectMapperWrapper.fromBytes(
            objectMapperWrapper.toBytes(object),
            (Class<T>) object.getClass()
        );
    }
}