package io.hypersistence.utils.spring.repo.hibernate;

import io.hypersistence.utils.spring.domain.Books;
import io.hypersistence.utils.spring.repository.HibernateRepository;
import org.springframework.data.jpa.repository.JpaRepository;

@org.springframework.stereotype.Repository
public interface BooksRepository extends HibernateRepository<Books>, JpaRepository<Books, Long> {
}
