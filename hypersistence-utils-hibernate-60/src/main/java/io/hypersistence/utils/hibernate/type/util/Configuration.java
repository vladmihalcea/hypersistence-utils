package io.hypersistence.utils.hibernate.type.util;

import io.hypersistence.utils.common.ClassLoaderUtils;
import org.hibernate.cfg.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;

import static io.hypersistence.utils.common.LogUtils.LOGGER;

/**
 * <code>Configuration</code> - It allows you to configure various Hibernate Types.
 *
 * @author Vlad Mihalcea
 * @since 2.1.0
 */
public class Configuration implements Serializable {

    public static final Configuration INSTANCE = new Configuration();

    @Deprecated
    public static final String DEPRECATED_PROPERTIES_FILE_PATH = "hibernate-types.properties.path";
    @Deprecated
    public static final String DEPRECATED_PROPERTIES_FILE_NAME = "hibernate-types.properties";

    public static final String PROPERTIES_FILE_PATH = "hypersistence-utils.properties.path";
    public static final String PROPERTIES_FILE_NAME = "hypersistence-utils.properties";
    public static final String APPLICATION_PROPERTIES_FILE_NAME = "application.properties";

    /**
     * Each Property has a well-defined key.
     */
    public enum PropertyKey {
        JACKSON_OBJECT_MAPPER(
            "hypersistence.utils.jackson.object.mapper",
            "hibernate.types.jackson.object.mapper"
        ),
        JSON_SERIALIZER(
            "hypersistence.utils.json.serializer",
            "hibernate.types.json.serializer"
        ),
        PRINT_BANNER(
            "hypersistence.utils.print.banner",
            "hibernate.types.print.banner"
        );

        private final String key;
        @Deprecated
        private final String deprecatedKey;

        PropertyKey(String key, String deprecatedKey) {
            this.key = key;
            this.deprecatedKey = deprecatedKey;
        }

        public String resolve(Properties properties) {
            String value = properties.getProperty(key);
            if(value == null) {
                value = properties.getProperty(deprecatedKey);
                if(value != null) {
                    LOGGER.warn(
                        "The [{}] configuration property is deprecated. Use [{}] instead.",
                        deprecatedKey,
                        key
                    );
                }
            }
            return value;
        }
    }

    private final Properties properties = Environment.getProperties();

    private Configuration() {
        this(null);
    }

    public Configuration(Map<String, Object> settings) {
        load();
        if (settings != null) {
            properties.putAll(settings);
        }
    }

    /**
     * Load {@link Properties} from the resolved {@link InputStream}
     */
    private void load() {
        String customPropertiesFilePath = System.getProperty(DEPRECATED_PROPERTIES_FILE_PATH);
        if(customPropertiesFilePath != null) {
            LOGGER.warn(
                "The [{}] System property is deprecated. Use [{}] instead.",
                DEPRECATED_PROPERTIES_FILE_PATH,
                PROPERTIES_FILE_PATH
            );
        } else {
            customPropertiesFilePath = System.getProperty(PROPERTIES_FILE_PATH);
        }

        String[] propertiesFilePaths = new String[] {
            APPLICATION_PROPERTIES_FILE_NAME,
            DEPRECATED_PROPERTIES_FILE_NAME,
            PROPERTIES_FILE_NAME,
            customPropertiesFilePath
        };

        for (String propertiesFilePath : propertiesFilePaths) {
            if (propertiesFilePath != null) {
                InputStream propertiesInputStream = null;
                try {
                    propertiesInputStream = propertiesInputStream(propertiesFilePath);
                    if (propertiesInputStream != null) {
                        properties.load(propertiesInputStream);

                        if(DEPRECATED_PROPERTIES_FILE_NAME.equals(propertiesFilePath)) {
                            LOGGER.warn(
                                "The [{}] property file is deprecated. Use [{}] instead.",
                                DEPRECATED_PROPERTIES_FILE_NAME,
                                PROPERTIES_FILE_NAME
                            );
                        }
                    }
                } catch (IOException e) {
                    LOGGER.error("Can't load properties", e);
                } finally {
                    try {
                        if (propertiesInputStream != null) {
                            propertiesInputStream.close();
                        }
                    } catch (IOException e) {
                        LOGGER.error("Can't close the properties InputStream", e);
                    }
                }
            }
        }
    }

    /**
     * Get {@link Properties} file {@link InputStream}
     *
     * @param propertiesFilePath properties file path
     * @return {@link Properties} file {@link InputStream}
     * @throws IOException the file couldn't be loaded properly
     */
    private InputStream propertiesInputStream(String propertiesFilePath) throws IOException {
        if (propertiesFilePath != null) {
            URL propertiesFileUrl;
            try {
                propertiesFileUrl = new URL(propertiesFilePath);
            } catch (MalformedURLException ignore) {
                propertiesFileUrl = ClassLoaderUtils.getResource(propertiesFilePath);
                if (propertiesFileUrl == null) {
                    File f = new File(propertiesFilePath);
                    if (f.exists() && f.isFile()) {
                        try {
                            propertiesFileUrl = f.toURI().toURL();
                        } catch (MalformedURLException e) {
                            LOGGER.error(
                                "The property " + propertiesFilePath + " can't be resolved to either a URL, " +
                                "a classpath resource or a File"
                            );
                        }
                    }
                }
            }
            if (propertiesFileUrl != null) {
                return propertiesFileUrl.openStream();
            }
        }
        return ClassLoaderUtils.getResourceAsStream(propertiesFilePath);
    }

    /**
     * Get all properties.
     *
     * @return properties.
     */
    public Properties getProperties() {
        return properties;
    }

    /**
     * Get Integer property value
     *
     * @param propertyKey property key
     * @return Integer property value
     */
    public Integer integerProperty(PropertyKey propertyKey) {
        Integer value = null;
        String property = propertyKey.resolve(properties);
        if (property != null) {
            value = Integer.valueOf(property);
        }
        return value;
    }

    /**
     * Get Long property value
     *
     * @param propertyKey property key
     * @return Long property value
     */
    public Long longProperty(PropertyKey propertyKey) {
        Long value = null;
        String property = propertyKey.resolve(properties);
        if (property != null) {
            value = Long.valueOf(property);
        }
        return value;
    }

    /**
     * Get Boolean property value
     *
     * @param propertyKey property key
     * @return Boolean property value
     */
    public Boolean booleanProperty(PropertyKey propertyKey) {
        Boolean value = null;
        String property = propertyKey.resolve(properties);
        if (property != null) {
            value = Boolean.valueOf(property);
        }
        return value;
    }

    /**
     * Get Class property value
     *
     * @param propertyKey property key
     * @param <T> class generic type
     * @return Class property value
     */
    public <T> Class<T> classProperty(PropertyKey propertyKey) {
        Class<T> clazz = null;
        String property = propertyKey.resolve(properties);
        if (property != null) {
            try {
                return ClassLoaderUtils.loadClass(property);
            } catch (ClassNotFoundException e) {
                LOGGER.error("Couldn't load the " + property + " class given by the " + propertyKey + " property", e);
            }
        }
        return clazz;
    }

    /**
     * Instantiate class associated to the given property key
     *
     * @param propertyKey property key
     * @param <T>         class parameter type
     * @return class instance
     */
    protected  <T> T instantiateClass(PropertyKey propertyKey) {
        T object = null;
        String property = propertyKey.resolve(properties);
        if (property != null) {
            try {
                Class<T> clazz = ClassLoaderUtils.loadClass(property);
                LOGGER.debug("Instantiate {}", clazz);
                object = clazz.newInstance();
            } catch (ClassNotFoundException e) {
                LOGGER.error("Couldn't load the " + property + " class given by the " + propertyKey + " property", e);
            } catch (InstantiationException e) {
                LOGGER.error("Couldn't instantiate the " + property + " class given by the " + propertyKey + " property", e);
            } catch (IllegalAccessException e) {
                LOGGER.error("Couldn't access the " + property + " class given by the " + propertyKey + " property", e);
            }
        }
        return object;
    }
}
