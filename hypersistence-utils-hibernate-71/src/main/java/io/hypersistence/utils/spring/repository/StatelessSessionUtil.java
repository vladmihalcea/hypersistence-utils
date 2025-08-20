package io.hypersistence.utils.spring.repository;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.StatelessSession;
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
            TransactionSynchronizationManager.bindResource(statelessSessionKey, statelessSession);
            return statelessSession;
        });
    }
}
