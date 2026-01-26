package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.type.SerializationException;

import java.io.Serializable;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.AbstractMap;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
            Collection<?> collection = (Collection<?>) object;
            ElementTypeInfo elementTypeInfo = resolveCommonCollectionElementType(collection);
            if (elementTypeInfo != null) {
                boolean requiresJsonClone = !Serializable.class.isAssignableFrom(elementTypeInfo.commonElementClass) ||
                    containsNonSerializableLeaf(elementTypeInfo.commonElementType);
                if (requiresJsonClone) {
                    JavaType type = typeFactory()
                        .constructCollectionType(
                            resolveCollectionClass(collection.getClass()),
                            elementTypeInfo.commonElementType
                        );
                    return objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), type);
                }
            }
        } else if (object instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) object;
            MapElementTypeInfo elementTypeInfo = resolveCommonMapElementType(map);
            if (elementTypeInfo != null) {
                boolean isCommonKeyClassCoreOrNotSerializable = isCoreJavaType(elementTypeInfo.commonKeyClass) ||
                    !Serializable.class.isAssignableFrom(elementTypeInfo.commonKeyClass);
                boolean isCommonValueClassCoreOrNotSerializable = isCoreJavaType(elementTypeInfo.commonValueClass) ||
                    !Serializable.class.isAssignableFrom(elementTypeInfo.commonValueClass);
                boolean requiresJsonClone = isCommonKeyClassCoreOrNotSerializable ||
                    isCommonValueClassCoreOrNotSerializable ||
                    containsNonSerializableLeaf(elementTypeInfo.commonKeyType) ||
                    containsNonSerializableLeaf(elementTypeInfo.commonValueType);
                if (requiresJsonClone && (object.getClass().getTypeParameters().length == 2)) {
                    JavaType type = typeFactory().constructMapType(
                        resolveMapClass(map.getClass()),
                        elementTypeInfo.commonKeyType,
                        elementTypeInfo.commonValueType
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

    private TypeFactory typeFactory() {
        return objectMapperWrapper.getObjectMapper().getTypeFactory();
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

    private boolean hasDefaultConstructor(Class<?> type) {
        if (Modifier.isAbstract(type.getModifiers())) {
            return false;
        }
        try {
            Constructor<?> constructor = type.getDeclaredConstructor();
            return constructor != null;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    private Class<? extends Collection> resolveCollectionClass(Class<?> collectionClass) {
        if (Collection.class.isAssignableFrom(collectionClass) && hasDefaultConstructor(collectionClass)) {
            return (Class<? extends Collection>) collectionClass;
        }
        if (List.class.isAssignableFrom(collectionClass)) {
            return List.class;
        }
        if (Set.class.isAssignableFrom(collectionClass)) {
            return Set.class;
        }
        return Collection.class;
    }

    private Class<? extends Map> resolveMapClass(Class<?> mapClass) {
        if (Map.class.isAssignableFrom(mapClass) && hasDefaultConstructor(mapClass)) {
            return (Class<? extends Map>) mapClass;
        }
        return Map.class;
    }

    private boolean containsNonSerializableLeaf(JavaType type) {
        if (type == null) {
            return false;
        }
        if (type.isContainerType()) {
            JavaType keyType = type.getKeyType();
            if (keyType != null && containsNonSerializableLeaf(keyType)) {
                return true;
            }
            JavaType contentType = type.getContentType();
            return contentType != null && containsNonSerializableLeaf(contentType);
        }
        Class<?> rawClass = type.getRawClass();
        return rawClass != null && !Serializable.class.isAssignableFrom(rawClass);
    }

    private ElementTypeInfo resolveCommonCollectionElementType(Collection<?> collection) {
        Class<?> commonElementClass = null;
        JavaType commonElementType = null;
        for (Object element : collection) {
            if (element != null) {
                Class<?> elementClass = element.getClass();
                if (commonElementClass == null) {
                    commonElementClass = elementClass;
                    commonElementType = resolveJavaType(element);
                } else {
                    Class<?> previousCommonElementClass = commonElementClass;
                    commonElementClass = resolveCommonElementType(commonElementClass, elementClass);
                    if (commonElementClass == null) {
                        return null;
                    }
                    if (!commonElementClass.equals(previousCommonElementClass)) {
                        commonElementType = typeFactory().constructType(commonElementClass);
                    }
                }
            }
        }
        if (commonElementClass == null) {
            return null;
        }
        if (commonElementType == null) {
            commonElementType = typeFactory().constructType(commonElementClass);
        }
        return new ElementTypeInfo(commonElementClass, commonElementType);
    }

    private MapElementTypeInfo resolveCommonMapElementType(Map<?, ?> map) {
        Map.Entry<Class, Class> commonElementType = null;
        JavaType commonKeyType = null;
        JavaType commonValueType = null;
        for (Map.Entry elementEntry : map.entrySet()) {
            if (elementEntry.getKey() != null && elementEntry.getValue() != null) {
                Map.Entry<Class, Class> elementClass = new AbstractMap.SimpleEntry<>(
                    elementEntry.getKey().getClass(),
                    elementEntry.getValue().getClass()
                );
                if (commonElementType == null) {
                    commonElementType = elementClass;
                    commonKeyType = resolveJavaType(elementEntry.getKey());
                    commonValueType = resolveJavaType(elementEntry.getValue());
                } else {
                    Class<?> previousKeyClass = commonElementType.getKey();
                    Class<?> previousValueClass = commonElementType.getValue();
                    commonElementType = resolveCommonElementType(commonElementType, elementClass);
                    if (commonElementType == null) {
                        return null;
                    }
                    Class<?> commonKeyClass = commonElementType.getKey();
                    Class<?> commonValueClass = commonElementType.getValue();
                    if (!commonKeyClass.equals(previousKeyClass)) {
                        commonKeyType = typeFactory().constructType(commonKeyClass);
                    }
                    if (!commonValueClass.equals(previousValueClass)) {
                        commonValueType = typeFactory().constructType(commonValueClass);
                    }
                }
            }
        }
        if (commonElementType == null) {
            return null;
        }
        if (commonKeyType == null) {
            commonKeyType = typeFactory().constructType(commonElementType.getKey());
        }
        if (commonValueType == null) {
            commonValueType = typeFactory().constructType(commonElementType.getValue());
        }
        return new MapElementTypeInfo(
            commonElementType.getKey(),
            commonElementType.getValue(),
            commonKeyType,
            commonValueType
        );
    }

    private JavaType resolveJavaType(Object value) {
        if (value instanceof Collection) {
            ElementTypeInfo elementTypeInfo = resolveCommonCollectionElementType((Collection<?>) value);
            if (elementTypeInfo == null) {
                return null;
            }
            return typeFactory().constructCollectionType(
                resolveCollectionClass(value.getClass()),
                elementTypeInfo.commonElementType
            );
        }
        if (value instanceof Map) {
            MapElementTypeInfo elementTypeInfo = resolveCommonMapElementType((Map<?, ?>) value);
            if (elementTypeInfo == null) {
                return null;
            }
            return typeFactory().constructMapType(
                resolveMapClass(value.getClass()),
                elementTypeInfo.commonKeyType,
                elementTypeInfo.commonValueType
            );
        }
        return typeFactory().constructType(value.getClass());
    }

    private <T> T jsonClone(T object) {
        return objectMapperWrapper.fromBytes(
            objectMapperWrapper.toBytes(object),
            (Class<T>) object.getClass()
        );
    }

    private static final class ElementTypeInfo {
        private final Class<?> commonElementClass;
        private final JavaType commonElementType;

        private ElementTypeInfo(Class<?> commonElementClass, JavaType commonElementType) {
            this.commonElementClass = commonElementClass;
            this.commonElementType = commonElementType;
        }
    }

    private static final class MapElementTypeInfo {
        private final Class<?> commonKeyClass;
        private final Class<?> commonValueClass;
        private final JavaType commonKeyType;
        private final JavaType commonValueType;

        private MapElementTypeInfo(
            Class<?> commonKeyClass,
            Class<?> commonValueClass,
            JavaType commonKeyType,
            JavaType commonValueType
        ) {
            this.commonKeyClass = commonKeyClass;
            this.commonValueClass = commonValueClass;
            this.commonKeyType = commonKeyType;
            this.commonValueType = commonValueType;
        }
    }
}
