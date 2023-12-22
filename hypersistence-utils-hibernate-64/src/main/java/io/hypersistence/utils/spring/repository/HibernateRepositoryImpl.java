package io.hypersistence.utils.spring.repository;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.internal.AbstractSharedSessionContract;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Vlad Mihalcea
 */
public class HibernateRepositoryImpl<T> implements HibernateRepository<T> {

    private final EntityManager entityManager;

    public HibernateRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<T> findAll() {
        throw new UnsupportedOperationException("Fetching all records from a given database table is a terrible idea!");
    }

    @Override
    public <S extends T> S save(S entity) {
        return unsupportedSave();
    }

    @Override
    public <S extends T> List<S> saveAll(Iterable<S> entities) {
        return unsupportedSave();
    }

    @Override
    public <S extends T> S saveAndFlush(S entity) {
        return unsupportedSave();
    }

    @Override
    public <S extends T> List<S> saveAllAndFlush(Iterable<S> entities) {
        return unsupportedSave();
    }

    public <S extends T> S persist(S entity) {
        entityManager.persist(entity);
        return entity;
    }

    public <S extends T> S persistAndFlush(S entity) {
        persist(entity);
        entityManager.flush();
        return entity;
    }

    public <S extends T> List<S> persistAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for(S entity : entities) {
            result.add(persist(entity));
        }
        return result;
    }

    public <S extends T> List<S> persistAllAndFlush(Iterable<S> entities) {
        return executeBatch(() -> {
            List<S> result = new ArrayList<>();
            for(S entity : entities) {
                result.add(persist(entity));
            }
            entityManager.flush();
            return result;
        });
    }

    public <S extends T> S merge(S entity) {
        return entityManager.merge(entity);
    }

    @Override
    public <S extends T> S mergeAndFlush(S entity) {
        S result = merge(entity);
        entityManager.flush();
        return result;
    }

    @Override
    public <S extends T> List<S> mergeAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for(S entity : entities) {
            result.add(merge(entity));
        }
        return result;
    }

    @Override
    public <S extends T> List<S> mergeAllAndFlush(Iterable<S> entities) {
        return executeBatch(() -> {
            List<S> result = new ArrayList<>();
            for(S entity : entities) {
                result.add(merge(entity));
            }
            entityManager.flush();
            return result;
        });
    }

    public <S extends T> S update(S entity) {
        session().update(entity);
        return entity;
    }

    @Override
    public <S extends T> S updateAndFlush(S entity) {
        update(entity);
        entityManager.flush();
        return entity;
    }

    @Override
    public <S extends T> List<S> updateAll(Iterable<S> entities) {
        List<S> result = new ArrayList<>();
        for(S entity : entities) {
            result.add(update(entity));
        }
        return result;
    }

    @Override
    public <S extends T> List<S> updateAllAndFlush(Iterable<S> entities) {
        return executeBatch(() -> {
            List<S> result = new ArrayList<>();
            for(S entity : entities) {
                result.add(update(entity));
            }
            entityManager.flush();
            return result;
        });
    }

    protected Integer getBatchSize(Session session) {
        SessionFactoryImplementor sessionFactory = session.getSessionFactory().unwrap(SessionFactoryImplementor.class);
        final JdbcServices jdbcServices = sessionFactory.getServiceRegistry().getService(JdbcServices.class);
        if(!jdbcServices.getExtractedMetaDataSupport().supportsBatchUpdates()) {
            return Integer.MIN_VALUE;
        }
        return session.unwrap(AbstractSharedSessionContract.class).getConfiguredJdbcBatchSize();
    }

    protected <R> R executeBatch(Supplier<R> callback) {
        Session session = session();
        Integer jdbcBatchSize = getBatchSize(session);
        Integer originalSessionBatchSize = session.getJdbcBatchSize();
        try {
            if (jdbcBatchSize == null) {
                session.setJdbcBatchSize(10);
            }
            return callback.get();
        } finally {
            session.setJdbcBatchSize(originalSessionBatchSize);
        }
    }

    protected Session session() {
        return entityManager.unwrap(Session.class);
    }

    protected <S extends T> S unsupportedSave() {
        throw new UnsupportedOperationException("There's no such thing as a save method in JPA, so don't use this hack!");
    }
}
