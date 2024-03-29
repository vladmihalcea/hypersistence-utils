package io.hypersistence.utils.spring.aop.service;

import io.hypersistence.utils.spring.annotation.Retry;
import org.springframework.stereotype.Service;

import javax.persistence.OptimisticLockException;

@Service
public class CustomerService extends BaseServiceImpl {

    @Retry(times = 2, on = OptimisticLockException.class)
    public void saveCustomer() {
        incrementCalls();
        throw new OptimisticLockException("Stale state!");
    }

    @Retry(times = 2, on = OptimisticLockException.class)
    public void saveCustomer(String name, String type) {
        incrementCalls();
        throw new OptimisticLockException("Stale state!");
    }
}
