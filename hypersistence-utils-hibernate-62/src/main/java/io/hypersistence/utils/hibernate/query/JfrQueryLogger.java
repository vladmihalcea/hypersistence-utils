package io.hypersistence.utils.hibernate.query;

import org.hibernate.resource.jdbc.spi.StatementInspector;

/**
 * The {@link JfrQueryLogger} allows you to log a given SQL, including its stack trace,
 * using JDK Flight Recorder.
 * <p>
 * This only works with HotSpot based JVMs and requires at least Java 11.
 *
 * @author Philippe Marschall
 * @since 3.5.4
 */
public final class JfrQueryLogger implements StatementInspector {

    @Override
    public String inspect(String sql) {
        QueryEvent event = new QueryEvent();
        event.setSql(sql);
        event.commit();
        return null;
    }

}
