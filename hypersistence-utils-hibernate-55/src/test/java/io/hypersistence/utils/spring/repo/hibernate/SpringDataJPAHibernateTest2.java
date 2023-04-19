package io.hypersistence.utils.spring.repo.hibernate;

import io.hypersistence.utils.spring.commons.Codes.BookTypes;
import io.hypersistence.utils.spring.commons.Codes.Publisher;
import io.hypersistence.utils.spring.domain.Books;
import io.hypersistence.utils.spring.domain.Books.BookInventory;
import io.hypersistence.utils.spring.domain.SharedDomainSharedDataFixture;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringDataJPAHibernateConfiguration.class)
public class SpringDataJPAHibernateTest2 {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private BooksRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    public void testSave() {
        Map<Publisher, Map<BookTypes, BookInventory>> bookDetails = SharedDomainSharedDataFixture.standardBook();
        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            bookRepository.persist(new Books(702L, bookDetails));
            return null;
        });

        List<Books> books = transactionTemplate.execute(transactionStatus ->
                entityManager.createQuery(
                                "select b from Books b " , Books.class)
                        .getResultList()
        );

        for (Books book : books) {
            LOGGER.debug("[TESTING] Book ID:" + book.getId());
            LOGGER.debug("[TESTING] Book version: " + book.getVersion());
            assertEquals(Optional.of(0L), book.getVersion());
        }
    }

}
