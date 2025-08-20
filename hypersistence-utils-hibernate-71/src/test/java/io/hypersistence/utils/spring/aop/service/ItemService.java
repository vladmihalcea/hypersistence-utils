package io.hypersistence.utils.spring.aop.service;

import io.hypersistence.utils.spring.annotation.Retry;
import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ItemServiceImpl - ItemService Impl
 *
 * @author Vlad Mihalcea
 */
@Service
public class ItemService extends BaseServiceImpl {

    @Retry(times = 2, on = OptimisticLockException.class)
    @Transactional
    public void saveItem() {
        incrementCalls();
        throw new OptimisticLockException("Save Item!");
    }
}
