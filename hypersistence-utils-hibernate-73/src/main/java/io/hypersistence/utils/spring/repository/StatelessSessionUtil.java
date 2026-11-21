package io.hypersistence.utils.spring.repository;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.Serializable;
import java.sql.Connection;
import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
class StatelessSessionUtil implements Serializable {
    private final Connection connection;

    public StatelessSessionUtil(Connection connection) {
        this.connection = connection;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StatelessSessionUtil)) return false;
        StatelessSessionUtil that = (StatelessSessionUtil) o;
        return Objects.equals(connection, that.connection);
    }

    @Override
    public int hashCode() {
        return Objects.hash(connection);
    }

    public static StatelessSession statelessSession(EntityManager entityManager) {
        Session session = entityManager.unwrap(Session.class);
        return session.doReturningWork(connection -> {
            StatelessSessionUtil statelessSessionKey = new StatelessSessionUtil(connection);
            StatelessSession statelessSession = (StatelessSession) TransactionSynchronizationManager.getResource(statelessSessionKey);
            if (statelessSession != null) {
                return statelessSession;
            }
            statelessSession = session.getSessionFactory().openStatelessSession(connection);
            // The StatelessSession wraps the same physical JDBC Connection that Spring
            // already enrolled in the current transaction (autoCommit is off). Beginning a
            // resource-local transaction here does NOT start a second DB transaction; it only
            // flips the StatelessSession's own coordinator status to ACTIVE, which is what makes
            // TransactionCoordinator.isTransactionActive() return true. Without it,
            // AbstractMutationCoordinator.resolveBatchKeyAccess() falls back to
            // NoBatchKeyAccess and JDBC batching is disabled.
            //
            // The check must target the StatelessSession, not the primary Session: inside a
            // Spring transaction the primary Session is already joined, but the StatelessSession
            // has its own, still-inactive coordinator. joinTransaction()/auto-join do not help
            // here either, as explicitJoin() is a no-op for a non-JTA (resource-local) session.
            if (!statelessSession.getTransaction().isActive()) {
                statelessSession.getTransaction().begin();
            }

            final StatelessSession statelessSessionResource = statelessSession;
            final StatelessSessionUtil resourceKey = statelessSessionKey;
            // Never commit/rollback this StatelessSession's transaction: it shares Spring's
            // Connection, so that would steal the physical commit from Spring. We only close
            // the session (which does not commit/rollback the provided Connection) and unbind
            // the resource once the Spring transaction completes.
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (TransactionSynchronizationManager.hasResource(resourceKey)) {
                            TransactionSynchronizationManager.unbindResource(resourceKey);
                        }
                        statelessSessionResource.close();
                    }
                });
            }

            TransactionSynchronizationManager.bindResource(statelessSessionKey, statelessSession);
            return statelessSession;
        });
    }
}
