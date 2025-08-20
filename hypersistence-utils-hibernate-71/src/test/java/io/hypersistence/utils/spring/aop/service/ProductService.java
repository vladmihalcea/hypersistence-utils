package io.hypersistence.utils.spring.aop.service;

import io.hypersistence.utils.spring.annotation.Retry;
import jakarta.persistence.OptimisticLockException;

/**
 * ProductService - Product Service
 *
 * @author Vlad Mihalcea
 */
public interface ProductService extends BaseService {

    @Retry(times = 2, on = OptimisticLockException.class)
    void saveProduct();
}
