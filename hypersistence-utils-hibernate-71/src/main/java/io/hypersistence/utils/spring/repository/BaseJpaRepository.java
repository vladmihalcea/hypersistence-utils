package io.hypersistence.utils.spring.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.repository.NoRepositoryBean;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.QueryByExampleExecutor;

import java.util.List;
import java.util.Optional;

/**
 * The {@code BaseJpaRepository} fixes many of the problems that the default Spring Data {@code JpaRepository}
 * suffers from.
 * <p>
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/basejparepository-hypersistence-utils/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 * @version 2.21.0
 */
@NoRepositoryBean
public interface BaseJpaRepository<T, ID> extends Repository<T, ID>, QueryByExampleExecutor<T> {

    Optional<T> findById(ID id);

    boolean existsById(ID id);

    T getReferenceById(ID id);

    List<T> findAllById(Iterable<ID> ids);

    long count();

    void delete(T entity);

    void deleteAllInBatch(Iterable<T> entities);

    void deleteById(ID id);

    void deleteAllByIdInBatch(Iterable<ID> ids);

    void flush();

    /**
     * The persist method allows you to pass the provided entity to the {@code persist} method of the
     * underlying JPA {@code EntityManager}.
     *
     * @param entity entity to persist
     * @param <S>    entity type
     * @return entity
     */
    <S extends T> S persist(S entity);

    /**
     * The persistAndFlush method allows you to pass the provided entity to the {@code persist} method of the
     * underlying JPA {@code EntityManager} and call {@code flush} afterwards.
     *
     * @param entity entity to persist
     * @param <S>    entity type
     * @return entity
     */
    <S extends T> S persistAndFlush(S entity);

    /**
     * The persistAll method allows you to pass the provided entities to the {@code persist} method of the
     * underlying JPA {@code EntityManager}.
     *
     * @param entities entities to persist
     * @param <S>    entity type
     * @return entities
     */
    <S extends T> List<S> persistAll(Iterable<S> entities);

    /**
     * The persistAll method allows you to pass the provided entities to the {@code persist} method of the
     * underlying JPA {@code EntityManager} and call {@code flush} afterwards.
     *
     * @param entities entities to persist
     * @param <S>    entity type
     * @return entities
     */
    <S extends T> List<S> persistAllAndFlush(Iterable<S> entities);

    /**
     * The persist method allows you to pass the provided entity to the {@code merge} method of the
     * underlying JPA {@code EntityManager}.
     *
     * @param entity entity to merge
     * @param <S>    entity type
     * @return entity
     */
    <S extends T> S merge(S entity);

    /**
     * The mergeAndFlush method allows you to pass the provided entity to the {@code merge} method of the
     * underlying JPA {@code EntityManager} and call {@code flush} afterwards.
     *
     * @param entity entity to merge
     * @param <S>    entity type
     * @return entity
     */
    <S extends T> S mergeAndFlush(S entity);

    /**
     * The mergeAll method allows you to pass the provided entities to the {@code merge} method of the
     * underlying JPA {@code EntityManager}.
     *
     * @param entities entities to merge
     * @param <S>    entity type
     * @return entities
     */
    <S extends T> List<S> mergeAll(Iterable<S> entities);

    /**
     * The mergeAllAndFlush method allows you to pass the provided entities to the {@code merge} method of the
     * underlying JPA {@code EntityManager} and call {@code flush} afterwards.
     *
     * @param entities entities to persist
     * @param <S>    entity type
     * @return entities
     */
    <S extends T> List<S> mergeAllAndFlush(Iterable<S> entities);

    /**
     * The update method allows you to pass the provided entity to the {@code update} method of the
     * underlying JPA {@code EntityManager}.
     *
     * @param entity entity to update
     * @param <S>    entity type
     * @return entity
     */
    <S extends T> S update(S entity);

    /**
     * The updateAndFlush method allows you to pass the provided entity to the {@code update} method of the
     * underlying JPA {@code EntityManager} and call {@code flush} afterwards.
     *
     * @param entity entity to update
     * @param <S>    entity type
     * @return entity
     */
    <S extends T> S updateAndFlush(S entity);

    /**
     * The updateAll method allows you to pass the provided entities to the {@code update} method of the
     * underlying JPA {@code EntityManager}.
     *
     * @param entities entities to update
     * @param <S>    entity type
     * @return entities
     */
    <S extends T> List<S> updateAll(Iterable<S> entities);

    /**
     * The updateAllAndFlush method allows you to pass the provided entities to the {@code update} method of the
     * underlying JPA {@code EntityManager} and call {@code flush} afterwards.
     *
     * @param entities entities to update
     * @param <S>    entity type
     * @return entities
     */
    <S extends T> List<S> updateAllAndFlush(Iterable<S> entities);

    /**
     * Lock the entity with the provided identifier.
     *
     * @param id entity identifier
     * @param lockMode entity lock mode
     * @return entity
     */
    T lockById(ID id, LockModeType lockMode);
}
