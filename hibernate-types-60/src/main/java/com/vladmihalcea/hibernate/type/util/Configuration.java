package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.util.ClassLoaderUtils;
import com.vladmihalcea.hibernate.util.ReflectionUtils;
import com.vladmihalcea.hibernate.util.StringUtils;
import org.hibernate.cfg.Environment;

import jakarta.persistence.EntityManagerFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;

import static com.vladmihalcea.hibernate.util.LogUtils.LOGGER;

/**
 * <code>Configuration</code> - It allows declarative configuration through the <code>hibernate.properties</code> file
 * or the <code>hibernate-types.properties</code> file.
 *
 * The properties from <code>hibernate-types.properties</code> can override the ones from the <code>hibernate.properties</code> file.
 *
 * It loads the {@link Properties} configuration file and makes them available to other components.
 *
 * @author Vlad Mihalcea
 * @since 2.1.0
 */
public class Configuration {

    public static final Configuration INSTANCE = new Configuration();

    public static final String PROPERTIES_FILE_PATH = "hibernate-types.properties.path";
    public static final String PROPERTIES_FILE_NAME = "hibernate-types.properties";
    public static final String APPLICATION_PROPERTIES_FILE_NAME = "application.properties";

    /**
     * Each Property has a well-defined key.
     */
    public enum PropertyKey {
        JACKSON_OBJECT_MAPPER("hibernate.types.jackson.object.mapper"),
        JSON_SERIALIZER("hibernate.types.json.serializer"),
        PRINT_BANNER("hibernate.types.print.banner");

        private final String key;

        PropertyKey(String key) {
            this.key = key;
        }

        public String getKey() {
            return key;
        }
    }

    private final Properties properties = Environment.getProperties();

    private Configuration() {
        load();
    }

    public Configuration(Map<String, Object> settings) {
        load();
        properties.putAll(settings);

        this.printBanner();
    }

    /**
     * Load {@link Properties} from the resolved {@link InputStream}
     */
    private void load() {
        String[] propertiesFilePaths = new String[] {
            APPLICATION_PROPERTIES_FILE_NAME,
            PROPERTIES_FILE_NAME,
            System.getProperty(PROPERTIES_FILE_NAME),

        };

        for (String propertiesFilePath : propertiesFilePaths) {
            if (propertiesFilePath != null) {
                InputStream propertiesInputStream = null;
                try {
                    propertiesInputStream = propertiesInputStream(propertiesFilePath);
                    if (propertiesInputStream != null) {
                        properties.load(propertiesInputStream);
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
     * Get {@link ObjectMapperWrapper} reference
     *
     * @return {@link ObjectMapperWrapper} reference
     */
    public ObjectMapperWrapper getObjectMapperWrapper() {
        Object objectMapperPropertyInstance = instantiateClass(PropertyKey.JACKSON_OBJECT_MAPPER);

        ObjectMapperWrapper objectMapperWrapper = new ObjectMapperWrapper();

        if (objectMapperPropertyInstance != null) {
            if(objectMapperPropertyInstance instanceof ObjectMapperSupplier) {
                ObjectMapper objectMapper = ((ObjectMapperSupplier) objectMapperPropertyInstance).get();
                if(objectMapper != null) {
                    objectMapperWrapper = new ObjectMapperWrapper(objectMapper);
                }
            }
            else if (objectMapperPropertyInstance instanceof Supplier) {
                Supplier<ObjectMapper> objectMapperSupplier = (Supplier<ObjectMapper>) objectMapperPropertyInstance;
                objectMapperWrapper = new ObjectMapperWrapper(objectMapperSupplier.get());
            }
            else if (objectMapperPropertyInstance instanceof ObjectMapper) {
                ObjectMapper objectMapper = (ObjectMapper) objectMapperPropertyInstance;
                objectMapperWrapper = new ObjectMapperWrapper(objectMapper);
            }
        }

        Object jsonSerializerPropertyInstance = instantiateClass(PropertyKey.JSON_SERIALIZER);

        if (jsonSerializerPropertyInstance != null) {
            JsonSerializer jsonSerializer = null;

            if(jsonSerializerPropertyInstance instanceof JsonSerializerSupplier) {
                jsonSerializer = ((JsonSerializerSupplier) jsonSerializerPropertyInstance).get();
            }
            else if (jsonSerializerPropertyInstance instanceof Supplier) {
                Supplier<JsonSerializer> jsonSerializerSupplier = (Supplier<JsonSerializer>) jsonSerializerPropertyInstance;
                jsonSerializer = jsonSerializerSupplier.get();
            }
            else if (jsonSerializerPropertyInstance instanceof JsonSerializer) {
                jsonSerializer = (JsonSerializer) jsonSerializerPropertyInstance;
            }

            if (jsonSerializer != null) {
                objectMapperWrapper.setJsonSerializer(jsonSerializer);
            }
        }

        return objectMapperWrapper;
    }

    /**
     * Get Integer property value
     *
     * @param propertyKey property key
     * @return Integer property value
     */
    public Integer integerProperty(PropertyKey propertyKey) {
        Integer value = null;
        String property = properties.getProperty(propertyKey.getKey());
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
        String property = properties.getProperty(propertyKey.getKey());
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
        String property = properties.getProperty(propertyKey.getKey());
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
        String property = properties.getProperty(propertyKey.getKey());
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
    private <T> T instantiateClass(PropertyKey propertyKey) {
        T object = null;
        String property = properties.getProperty(propertyKey.getKey());
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

    /**
     * Print the banner into the log.
     */
    private void printBanner() {
        if (this.isHypersistenceOptimizer() || bannerPrinted) {
            return;
        }
        String printBannerValue = properties.getProperty(PropertyKey.PRINT_BANNER.getKey());
        if(printBannerValue != null && !Boolean.valueOf(printBannerValue)) {
            return;
        }

        LOGGER.info(
            StringUtils.join(
                StringUtils.LINE_SEPARATOR,
                "This framework is proudly powered by:",
                "",
                ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>",
                " _    _                           _     _",
                "| |  | |                         (_)   | |",
                "| |__| |_   _ _ __   ___ _ __ ___ _ ___| |_ ___ _ __   ___ ___",
                "|  __  | | | | '_ \\ / _ \\ '__/ __| / __| __/ _ \\ '_ \\ / __/ _ \\",
                "| |  | | |_| | |_) |  __/ |  \\__ \\ \\__ \\ ||  __/ | | | (_|  __/",
                "|_|  |_|\\__, | .__/ \\___|_|  |___/_|___/\\__\\___|_| |_|\\___\\___|",
                "         __/ | |",
                "        |___/|_|",
                "",
                "At Hypersistence, we only build amazing tools, like Hibernate Types, Flexy Pool, or Hypersistence Optimizer.",
                "",
                "What if there were a tool that could automatically detect JPA and Hibernate performance issues?",
                "",
                "Hypersistence Optimizer is that tool! For more details, go to: ",
                "",
                "https://vladmihalcea.com/hypersistence-optimizer/",
                "<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<",
                ""
            )
        );
        bannerPrinted = true;
    }

    private boolean isHypersistenceOptimizer() {
        try {
            Class licenseClass = ReflectionUtils.getClassOrNull("io.hypersistence.optimizer.core.License");

            if(licenseClass != null) {
                Class jpaConfigClass = ReflectionUtils.getClassOrNull("io.hypersistence.optimizer.core.config.JpaConfig");
                Object jpaConfig = ReflectionUtils.newInstance(
                    jpaConfigClass,
                    new Object[] {
                        null
                    },
                    new Class[] {
                        EntityManagerFactory.class
                    }
                );

                Object license = ReflectionUtils.newInstance(
                    licenseClass,
                    new Object[] {
                        jpaConfig
                    },
                    new Class[] {
                        ReflectionUtils.getClassOrNull("io.hypersistence.optimizer.core.LicenseConfig")
                    }
                );

                Properties properties = ReflectionUtils.invokeGetter(license, "properties");
                Class propertyClass = ReflectionUtils.getClassOrNull("io.hypersistence.optimizer.core.License$Property");
                Object trialVersionKey = Enum.valueOf(propertyClass, "TRIAL_VERSION");
                Object validUntilMillisKey = Enum.valueOf(propertyClass, "VALID_UNTIL_MILLIS");

                boolean trialVersion = Boolean.parseBoolean(properties.getProperty(ReflectionUtils.invokeGetter(trialVersionKey, "key")));
                long validUntilMillis = Long.parseLong(properties.getProperty(ReflectionUtils.invokeGetter(validUntilMillisKey, "key")));

                if(!trialVersion && validUntilMillis > System.currentTimeMillis()) {
                    return true;
                }
            }
        } catch (Exception ignore) {
        }

        return false;
    }

    private static boolean bannerPrinted = false;
}
