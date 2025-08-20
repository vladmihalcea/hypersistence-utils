package io.hypersistence.utils.spring.aop.service;

import org.springframework.stereotype.Service;

@Service
public class BaseServiceImpl {

    private volatile int calls = 0;

    protected void incrementCalls() {
        calls++;
    }

    public int getCalls() {
        return calls;
    }

    public void resetCalls() {
        this.calls = 0;
    }
}
