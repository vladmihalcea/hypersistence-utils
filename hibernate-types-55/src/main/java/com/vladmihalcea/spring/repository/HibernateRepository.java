package com.vladmihalcea.spring.repository;

import java.util.List;

/**
 * The {@code HibernateRepository} fixes the problems that the default Spring Data {@code JpaRepository}
 * suffers from.
 *
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/best-spring-data-jparepository/">this article</a>.
 *
 * @author Vlad Mihalcea
 * @version 2.17.0
 */
public interface HibernateRepository<T> {

    //The findAll method will trigger an UnsupportedOperationException

    @Deprecated
    List<T> findAll();

    //The save methods will trigger an UnsupportedOperationException

    @Deprecated
    <S extends T> S save(S entity);

    @Deprecated
    <S extends T> List<S> saveAll(Iterable<S> entities);

    @Deprecated
    <S extends T> S saveAndFlush(S entity);

    @Deprecated
    <S extends T> List<S> saveAllAndFlush(Iterable<S> entities);

    //The persist methods are meant to save newly created entities

    <S extends T> S persist(S entity);

    <S extends T> S persistAndFlush(S entity);

    <S extends T> List<S> persistAll(Iterable<S> entities);

    <S extends T> List<S> peristAllAndFlush(Iterable<S> entities);

    //The merge methods are meant to propagate detached entity state changes
    //if they are really needed

    <S extends T> S merge(S entity);

    <S extends T> S mergeAndFlush(S entity);

    <S extends T> List<S> mergeAll(Iterable<S> entities);

    <S extends T> List<S> mergeAllAndFlush(Iterable<S> entities);

    //The update methods are meant to force the detached entity state changes

    <S extends T> S update(S entity);

    <S extends T> S updateAndFlush(S entity);

    <S extends T> List<S> updateAll(Iterable<S> entities);

    <S extends T> List<S> updateAllAndFlush(Iterable<S> entities);

}
