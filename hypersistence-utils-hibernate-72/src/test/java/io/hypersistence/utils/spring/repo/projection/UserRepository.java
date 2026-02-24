package io.hypersistence.utils.spring.repo.projection;

import io.hypersistence.utils.spring.domain.User;
import io.hypersistence.utils.spring.repository.BaseJpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * @author Vlad Mihalcea
 */
@Repository
public interface UserRepository extends BaseJpaRepository<User, Long> {

    @Query("select u from User u where :role member of u.roles")
    List<User> findByRole(@Param("role") User.Role role);
}
