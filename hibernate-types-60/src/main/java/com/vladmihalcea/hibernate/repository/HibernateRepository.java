package com.vladmihalcea.hibernate.repository;

import java.util.List;

public interface HibernateRepository <T> {

    // save method will trigger an UnsupportedOperationException
    @Deprecated
    <S extends T> S save(S entity);

    @Deprecated
    <S extends T> List<S> saveAll(Iterable<S> entities);

    @Deprecated
    <S extends T> S saveandFlush(S entity);

    @Deprecated
    <S extends T> List<S> saveAllandFlush(Iterable<S> entities);

    // persist methods are meant to save newly created entities

    <S extends T> S persist(S entity);

    <S extends T> S persistAndFlush(S entity);

    <S extends T> List<S> persistAll(Iterable<S> entities);

    <S extends T> List<S> persistAllAndFlush(Iterable<S> entities);

    //Merge methods are meant to propagate detached entity state changes
    //if they are really needed

    <S extends T> S merge(S entity);

    <S extends T> S mergeAndFlush(S entity);

    <S extends T> List<S> mergeAll(Iterable<S> entities);

    <S extends T> List<S> mergeAllAndFlush(Iterable<S> entities);

    //Update methods are meant to force the detached entity state changes

    <S extends T> S update(S entity);

    <S extends T> S updateAndFlush(S entity);

    <S extends T> List<S> updateAll(Iterable<S> entities);

    <S extends T> List<S> updateAllAndFlush(Iterable<S> entities);






}
