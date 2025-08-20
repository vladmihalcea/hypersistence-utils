package io.hypersistence.utils.spring.aop;

import io.hypersistence.utils.spring.aop.service.CustomerService;
import io.hypersistence.utils.spring.aop.service.ItemService;
import io.hypersistence.utils.spring.aop.service.ProductService;
import jakarta.persistence.OptimisticLockException;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import static org.junit.Assert.assertEquals;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = RetryAspectConfiguration.class)
public class RetryAspectTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private ItemService itemService;

    @Before
    public void before() {
        productService.resetCalls();
        customerService.resetCalls();
        itemService.resetCalls();
    }

    @Test
    @Ignore("https://github.com/spring-projects/spring-framework/issues/22311")
    public void testRetryOnInterface() {
        assertEquals(0, productService.getCalls());
        try {
            productService.saveProduct();
        } catch (OptimisticLockException expected) {
        }
        assertEquals(3, productService.getCalls());
    }

    @Test
    public void testRetryOnImplementation() {
        assertEquals(0, customerService.getCalls());
        try {
            customerService.saveCustomer();
        } catch (OptimisticLockException expected) {
        }
        assertEquals(3, customerService.getCalls());
    }

    @Test
    public void testRetryOnImplementationWithArgs() {
        assertEquals(0, customerService.getCalls());
        try {
            customerService.saveCustomer("User A", "client");
        } catch (OptimisticLockException expected) {
        }
        assertEquals(3, customerService.getCalls());
    }

    @Test
    public void testRetryOnImplementationWithNullArg() {
        assertEquals(0, customerService.getCalls());
        try {
            customerService.saveCustomer("Unknown user", null);
        } catch (OptimisticLockException expected) {
        }
        assertEquals(3, customerService.getCalls());
    }
}
