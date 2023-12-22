package io.hypersistence.utils.hibernate.query;

import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The {@link QueryStackTraceLogger} allows you to log the stack trace that
 * executed a given SQL query.
 *
 * @author Vlad Mihalcea
 * @since 3.5.3
 */
public class QueryStackTraceLogger implements StatementInspector {

    public static final String ORG_HIBERNATE = "org.hibernate";

    public static String TAB = "\t";
    public static String NEW_LINE = System.getProperty("line.separator");

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryStackTraceLogger.class);

    private final String packageNamePrefix;

    public QueryStackTraceLogger(String packageNamePrefix) {
        this.packageNamePrefix = packageNamePrefix;
    }

    @Override
    public String inspect(String sql) {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(stackTraceStringUpTo(packageNamePrefix, sql));
        }
        return null;
    }

    /**
     * Generate a formatted stack trace string based up to the provided package name prefix.
     *
     * @param endPackageNamePrefix package name to match the {@link StackTraceElement}
     * @param query                the query string to embed in the log message
     * @return the stack trace {@link String} up to the matching the provided package name
     */
    private static String stackTraceStringUpTo(String endPackageNamePrefix, String query) {
        return StackWalker.getInstance().walk(stream -> {
            StringBuilder buffer = new StringBuilder();
            buffer.append("This SQL query: [\n\t");
            buffer.append(query);
            buffer.append("\n]\n");
            buffer.append("was generated by Hibernate like this: [\n");
            AtomicBoolean firstMatch = new AtomicBoolean(false);
            stream
                .skip(2L) // skip this and the calling method
                .dropWhile(stackFrame -> // skip anything else until we end up in Hibernate
                    !stackFrame.getClassName().startsWith(ORG_HIBERNATE))
                .takeWhile(stackFrame -> {
                    // take anything up to and including the first method in a class in endPackageNamePrefix
                    // would #startsWith be more appropriate?
                    if (stackFrame.getClassName().contains(endPackageNamePrefix)) {
                        if (firstMatch.get()) {
                            return false;
                        } else {
                            firstMatch.set(true);
                            return true;
                        }
                    } else {
                        return !firstMatch.get();
                    }
                })
                .forEach(stackFrame -> {
                    buffer.append(TAB).append(stackFrame.toStackTraceElement()).append(NEW_LINE);
                });
            buffer.append(']');
            return buffer.toString();
        });
    }
}
