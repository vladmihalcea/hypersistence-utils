package io.hypersistence.utils.spring.repo.hibernate;

import com.google.common.collect.HashMultimap;
import io.hypersistence.utils.spring.domain.Movie;
import io.hypersistence.utils.spring.repository.HibernateRepository;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovieRepository extends HibernateRepository<Movie>, JpaRepository<Movie, String> {

    @Modifying
    @Query("UPDATE Movie m SET m.cast = :cast WHERE m.name = :name")
    void updateActors(@Param("name") String name, @Param("cast") HashMultimap<String, String> cast);
}
