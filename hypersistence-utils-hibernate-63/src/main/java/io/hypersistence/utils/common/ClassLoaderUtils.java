package io.hypersistence.utils.common;

import java.io.InputStream;
import java.net.URL;

/**
 * <code>ClassLoaderUtils</code> - Class loading related utilities holder.
 *
 * @author Vlad Mihalcea
 * @since 2.1.0
 */
public final class ClassLoaderUtils {

    private ClassLoaderUtils() {
        throw new UnsupportedOperationException("ClassLoaderUtils is not instantiable!");
    }

    /**
     * Load the available ClassLoader
     *
     * @return ClassLoader
     */
    public static ClassLoader getClassLoader() {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        return (classLoader != null) ? classLoader : ClassLoaderUtils.class.getClassLoader();
    }

    /**
     * Load the Class denoted by the given string representation
     *
     * @param className class string representation
     * @param <T> class generic type
     * @return Class
     * @throws ClassNotFoundException if the class cannot be resolved
     */
    @SuppressWarnings("unchecked")
    public static <T> Class<T> loadClass(String className) throws ClassNotFoundException {
        return (Class<T>) getClassLoader().loadClass(className);
    }

    /**
     * Find if Class denoted by the given string representation is loadable
     *
     * @param className class string representation
     * @return Class
     */
    @SuppressWarnings("unchecked")
    public static boolean findClass(String className) {
        try {
            return getClassLoader().loadClass(className) != null;
        } catch (ClassNotFoundException e) {
            return false;
        } catch (NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Get the resource URL
     *
     * @param resourceName resource name
     * @return resource URL
     */
    public static URL getResource(String resourceName) {
        return getClassLoader().getResource(resourceName);
    }

    /**
     * Get the resource InputStream
     *
     * @param resourceName resource name
     * @return resource InputStream
     */
    public static InputStream getResourceAsStream(String resourceName) {
        return getClassLoader().getResourceAsStream(resourceName);
    }
}
