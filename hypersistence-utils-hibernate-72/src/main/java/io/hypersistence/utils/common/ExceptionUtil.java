package io.hypersistence.utils.common;

import jakarta.persistence.LockTimeoutException;
import org.hibernate.PessimisticLockException;
import org.hibernate.exception.LockAcquisitionException;

import java.sql.SQLTimeoutException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Vlad Mihalcea
 */
public interface ExceptionUtil {

    List<Class<? extends Exception>> LOCK_TIMEOUT_EXCEPTIONS = Arrays.asList(
            LockAcquisitionException.class,
            LockTimeoutException.class,
            PessimisticLockException.class,
            jakarta.persistence.PessimisticLockException.class,
            SQLTimeoutException.class
    );

    /**
     * Get the root cause of a particular {@code Throwable}
     *
     * @param t exception
     * @return exception root cause
     */
    static <T extends Throwable> T rootCause(Throwable t) {
        Throwable cause = t.getCause();
        if (cause != null && cause != t) {
            return rootCause(cause);
        }
        return (T) t;
    }

    /**
     * Is the given throwable caused by a database lock timeout?
     *
     * @param e exception
     * @return is caused by a database lock timeout
     */
    static boolean isLockTimeout(Throwable e) {
        AtomicReference<Throwable> causeHolder = new AtomicReference<>(e);
        do {
            final Throwable cause = causeHolder.get();
            final String failureMessage = cause.getMessage().toLowerCase();
            if (LOCK_TIMEOUT_EXCEPTIONS.stream().anyMatch(c -> c.isInstance(cause)) ||
                    failureMessage.contains("timeout") ||
                    failureMessage.contains("timed out") ||
                    failureMessage.contains("time out") ||
                    failureMessage.contains("closed connection") ||
                    failureMessage.contains("link failure") ||
                    failureMessage.contains("expired or aborted by a conflict")
                    ) {
                return true;
            } else {
                if (cause.getCause() == null || cause.getCause() == cause) {
                    break;
                } else {
                    causeHolder.set(cause.getCause());
                }
            }
        }
        while (true);
        return false;
    }

    /**
     * Is the given throwable caused by the following exception type?
     *
     * @param e exception
     * @param exceptionType exception type
     * @return is caused by the given exception type
     */
    static boolean isCausedBy(Throwable e, Class<? extends Throwable> exceptionType) {
        AtomicReference<Throwable> causeHolder = new AtomicReference<>(e);
        do {
            final Throwable cause = causeHolder.get();
            if (exceptionType.isInstance(cause)) {
                return true;
            } else {
                if (cause.getCause() == null || cause.getCause() == cause) {
                    break;
                } else {
                    causeHolder.set(cause.getCause());
                }
            }
        }
        while (true);
        return false;
    }

    /**
     * Is the given throwable caused by a database MVCC anomaly detection?
     *
     * @param e exception
     * @return is caused by a database lock MVCC anomaly detection
     */
    static boolean isMVCCAnomalyDetection(Throwable e) {
        AtomicReference<Throwable> causeHolder = new AtomicReference<>(e);
        do {
            final Throwable cause = causeHolder.get();
            String lowerCaseMessage = cause.getMessage().toLowerCase();
            if (
                    cause.getMessage().contains("ORA-08177: can't serialize access for this transaction") //Oracle
                            || lowerCaseMessage.contains("could not serialize access due to concurrent update") //PSQLException
                            || lowerCaseMessage.contains("ould not serialize access due to read/write dependencies among transactions") //PSQLException
                            || lowerCaseMessage.contains("snapshot isolation transaction aborted due to update conflict") //SQLServerException
                            || lowerCaseMessage.contains("kconflict") //YugabyteDB
                            || lowerCaseMessage.contains("unknown transaction, could be recently aborted") //YugabyteDB
                            || lowerCaseMessage.contains("conflicts with higher priority transaction") //YugabyteDB
                    ) {
                return true;
            } else {
                if (cause.getCause() == null || cause.getCause() == cause) {
                    break;
                } else {
                    causeHolder.set(cause.getCause());
                }
            }
        }
        while (true);
        return false;
    }

    /**
     * Was the given exception caused by a SQL connection close
     *
     * @param e exception
     * @return is caused by a SQL connection close
     */
    static boolean isConnectionClose(Exception e) {
        Throwable cause = e;
        do {
            if (cause.getMessage().toLowerCase().contains("connection is close")
                    || cause.getMessage().toLowerCase().contains("closed connection")
                    || cause.getMessage().toLowerCase().contains("link failure")
                    || cause.getMessage().toLowerCase().contains("closed")
                    ) {
                return true;
            } else {
                if (cause.getCause() == null || cause.getCause() == cause) {
                    break;
                } else {
                    cause = cause.getCause();
                }
            }
        }
        while (true);
        return false;
    }
}
