package io.hypersistence.utils.spring.repository;

import java.util.List;

/**
 * The {@code HibernateRepository} fixes the problems that the default Spring Data {@code JpaRepository}
 * suffers from.
 * <p>
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/best-spring-data-jparepository/">this article</a>.
 *
 * @author Vlad Mihalcea
 * @version 2.17.0
 */
public interface HibernateRepository<T> {

    /**
     * The findAll method is a terrible Anti-Pattern.
     * <p>
     * For more details about why you should not use the {@code findAll} method by default,
     * check out <a href="https://vladmihalcea.com/spring-data-findall-anti-pattern/">this article</a>.
     *
     * @return all the records from the database table this entity is mapped to
     */
    @Deprecated
    List<T> findAll();

    //The save methods will trigger an UnsupportedOperationException

    /**
     * The save method should be avoided.
     * <p>
     * For more details about how to use it, check out <a href="https://vladmihalcea.com/best-spring-data-jparepository/">this article</a>.
     */
    @Deprecated
    <S extends T> S save(S entity);

    /**
     * The save method should be avoided.
     * <p>
     * For more details about how to use it, check out <a href="https://vladmihalcea.com/best-spring-data-jparepository/">this article</a>.
     */
    @Deprecated
    <S extends T> List<S> saveAll(Iterable<S> entities);

    /**
     * The saveAndFlush method should be avoided.
     * <p>
     * For more details about how to use it, check out <a href="https://vladmihalcea.com/best-spring-data-jparepository/">this article</a>.
     */
    @Deprecated
    <S extends T> S saveAndFlush(S entity);

    /**
     * The saveAllAndFlush method should be avoided.
     * <p>
     * For more details about how to use it, check out <a href="https://vladmihalcea.com/best-spring-data-jparepository/">this article</a>.
     */
    @Deprecated
    <S extends T> List<S> saveAllAndFlush(Iterable<S> entities);

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
}
