package com.vladmihalcea.spring.repository;

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
 * "https://vladmihalcea.com/spring-data-base-repository/">this article</a>.
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
}
