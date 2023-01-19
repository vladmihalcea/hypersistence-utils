package io.hypersistence.utils.spring.aop.service;

import jakarta.persistence.OptimisticLockException;
import org.springframework.stereotype.Service;

@Service
public class ProductServiceImpl extends BaseServiceImpl implements ProductService {

    @Override
    public void saveProduct() {
        incrementCalls();
        throw new OptimisticLockException("Save Product!");
    }
}
