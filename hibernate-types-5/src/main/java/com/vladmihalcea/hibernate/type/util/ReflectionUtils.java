package com.vladmihalcea.hibernate.type.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * <code>ReflectionUtils</code> - Reflection utilities holder.
 *
 * @author Vlad Mihalcea
 * @since 1.0
 */
public final class ReflectionUtils {

    public static final String GETTER_PREFIX = "get";
    public static final String SETTER_PREFIX = "set";
    private static final Logger LOGGER = LoggerFactory.getLogger(ReflectionUtils.class);

    private ReflectionUtils() {
        throw new UnsupportedOperationException("ReflectionUtils is not instantiable!");
    }

    /**
     * Instantiate object
     *
     * @param className Class for object to instantiate
     * @param <T>       field type
     * @return field value
     */
    public static <T> T newInstance(String className) {
        try {
            Class clazz = Class.forName(className);
            return (T) clazz.newInstance();
        } catch (ClassNotFoundException e) {
            throw handleException(className, e);
        } catch (InstantiationException e) {
            throw handleException(className, e);
        } catch (IllegalAccessException e) {
            throw handleException(className, e);
        }
    }

    /**
     * Get target object field value
     *
     * @param target    target object
     * @param fieldName field name
     * @param <T>       field type
     * @return field value
     */
    public static <T> T getFieldValue(Object target, String fieldName) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            T returnValue = (T) field.get(target);
            return returnValue;
        } catch (NoSuchFieldException e) {
            throw handleException(fieldName, e);
        } catch (IllegalAccessException e) {
            throw handleException(fieldName, e);
        }
    }

    /**
     * Set target object field value
     *
     * @param target    target object
     * @param fieldName field name
     * @param value     field value
     */
    public static void setFieldValue(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException e) {
            throw handleException(fieldName, e);
        } catch (IllegalAccessException e) {
            throw handleException(fieldName, e);
        }
    }

    /**
     * Get target method
     *
     * @param target         target object
     * @param methodName     method name
     * @param parameterTypes method parameter types
     * @return return value
     */
    public static Method getMethod(Object target, String methodName, Class... parameterTypes) {
        return getMethod(target.getClass(), methodName, parameterTypes);
    }

    public static Method getMethod(Class targetClass, String methodName, Class... parameterTypes) {
        try {
            return targetClass.getDeclaredMethod(methodName, parameterTypes);
        } catch (NoSuchMethodException e) {
            try {
                return targetClass.getMethod(methodName, parameterTypes);
            } catch (NoSuchMethodException ignore) {
            }

            if(!targetClass.getSuperclass().equals(Object.class)) {
                return getMethod(targetClass.getSuperclass(), methodName, parameterTypes);
            }
            else {
                throw handleException(methodName, e);
            }
        }
    }

    /**
     * Check if target class has the given method
     *
     * @param targetClass    target class
     * @param methodName     method name
     * @param parameterTypes method parameter types
     * @return method availability
     */
    public static boolean hasMethod(Class<?> targetClass, String methodName, Class... parameterTypes) {
        try {
            targetClass.getMethod(methodName, parameterTypes);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /**
     * Get setter method
     *
     * @param target        target object
     * @param property      property
     * @param parameterType setter parameter type
     * @return setter method
     */
    public static Method getSetter(Object target, String property, Class<?> parameterType) {
        String setterMethodName = SETTER_PREFIX + property.substring(0, 1).toUpperCase() + property.substring(1);
        Method setter = getMethod(target, setterMethodName, parameterType);
        setter.setAccessible(true);
        return setter;
    }

    /**
     * Get getter method
     *
     * @param target   target object
     * @param property property
     * @return setter method
     */
    public static Method getGetter(Object target, String property) {
        String getterMethodName = GETTER_PREFIX + property.substring(0, 1).toUpperCase() + property.substring(1);
        Method getter = getMethod(target, getterMethodName);
        getter.setAccessible(true);
        return getter;
    }

    /**
     * Invoke target method
     *
     * @param method     method to invoke
     * @param parameters method parameters
     * @return return value
     */
    public static <T> T invoke(Object target, Method method, Object... parameters) {
        try {
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            T returnValue = (T) method.invoke(target, parameters);
            return returnValue;
        } catch (InvocationTargetException e) {
            throw handleException(method.getName(), e);
        } catch (IllegalAccessException e) {
            throw handleException(method.getName(), e);
        }
    }

    /**
     * Invoke getter method with the given parameter
     *
     * @param target   target object
     * @param property property
     */
    public static <T> T invokeGetter(Object target, String property) {
        Method setter = getGetter(target, property);
        try {
            return (T) setter.invoke(target);
        } catch (IllegalAccessException e) {
            throw handleException(setter.getName(), e);
        } catch (InvocationTargetException e) {
            throw handleException(setter.getName(), e);
        }
    }

    /**
     * Invoke setter method with the given parameter
     *
     * @param target    target object
     * @param property  property
     * @param parameter setter parameter
     */
    public static void invokeSetter(Object target, String property, Object parameter) {
        Method setter = getSetter(target, property, parameter.getClass());
        try {
            setter.invoke(target, parameter);
        } catch (IllegalAccessException e) {
            throw handleException(setter.getName(), e);
        } catch (InvocationTargetException e) {
            throw handleException(setter.getName(), e);
        }
    }

    /**
     * Invoke setter method with the given parameter
     *
     * @param target    target object
     * @param property  property
     * @param parameter setter parameter
     */
    public static void invokeSetter(Object target, String property, boolean parameter) {
        Method setter = getSetter(target, property, boolean.class);
        try {
            setter.invoke(target, parameter);
        } catch (IllegalAccessException e) {
            throw handleException(setter.getName(), e);
        } catch (InvocationTargetException e) {
            throw handleException(setter.getName(), e);
        }
    }

    /**
     * Invoke setter method with the given parameter
     *
     * @param target    target object
     * @param property  property
     * @param parameter setter parameter
     */
    public static void invokeSetter(Object target, String property, int parameter) {
        Method setter = getSetter(target, property, int.class);
        try {
            setter.invoke(target, parameter);
        } catch (IllegalAccessException e) {
            throw handleException(setter.getName(), e);
        } catch (InvocationTargetException e) {
            throw handleException(setter.getName(), e);
        }
    }

    /**
     * Invoke static Class method
     *
     * @param method        method to invoke
     * @param parameters    method parameters
     * @return              return value
     */
    public static <T> T invokeStatic(Method method, Object... parameters) {
        try {
            method.setAccessible(true);
            @SuppressWarnings("unchecked")
            T returnValue = (T) method.invoke(null, parameters);
            return returnValue;
        } catch (InvocationTargetException e) {
            throw handleException(method.getName(), e);
        } catch (IllegalAccessException e) {
            throw handleException(method.getName(), e);
        }
    }

    /**
     * Invoke setter method with the given parameter
     *
     * @param className class name to be retrieved
     * @return Java {@link Class} object instance
     */
    public static <T> Class<T> getClass(String className) {
        try {
            return (Class<T>) Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw handleException(className, e);
        }
    }

    /**
     * Handle {@link NoSuchFieldException} by logging it and rethrown it as a {@link IllegalArgumentException}
     *
     * @param fieldName field name
     * @param e         exception
     * @return wrapped exception
     */
    private static IllegalArgumentException handleException(String fieldName, NoSuchFieldException e) {
        LOGGER.error("Couldn't find field " + fieldName, e);
        return new IllegalArgumentException(e);
    }

    /**
     * Handle {@link NoSuchMethodException} by logging it and rethrown it as a {@link IllegalArgumentException}
     *
     * @param methodName method name
     * @param e          exception
     * @return wrapped exception
     */
    private static IllegalArgumentException handleException(String methodName, NoSuchMethodException e) {
        LOGGER.error("Couldn't find method " + methodName, e);
        return new IllegalArgumentException(e);
    }

    /**
     * Handle {@link IllegalAccessException} by logging it and rethrown it as a {@link IllegalArgumentException}
     *
     * @param memberName member name
     * @param e          exception
     * @return wrapped exception
     */
    private static IllegalArgumentException handleException(String memberName, IllegalAccessException e) {
        LOGGER.error("Couldn't access member " + memberName, e);
        return new IllegalArgumentException(e);
    }

    /**
     * Handle {@link InvocationTargetException} by logging it and rethrown it as a {@link IllegalArgumentException}
     *
     * @param methodName method name
     * @param e          exception
     * @return wrapped exception
     */
    private static IllegalArgumentException handleException(String methodName, InvocationTargetException e) {
        LOGGER.error("Couldn't invoke method " + methodName, e);
        return new IllegalArgumentException(e);
    }

    /**
     * Handle {@link ClassNotFoundException} by logging it and rethrown it as a {@link IllegalArgumentException}
     *
     * @param className class name
     * @param e         exception
     * @return wrapped exception
     */
    private static IllegalArgumentException handleException(String className, ClassNotFoundException e) {
        LOGGER.error("Couldn't find class " + className, e);
        return new IllegalArgumentException(e);
    }

    /**
     * Handle {@link InstantiationException} by logging it and rethrown it as a {@link IllegalArgumentException}
     *
     * @param className class name
     * @param e         exception
     * @return wrapped exception
     */
    private static IllegalArgumentException handleException(String className, InstantiationException e) {
        LOGGER.error("Couldn't instantiate class " + className, e);
        return new IllegalArgumentException(e);
    }
}
