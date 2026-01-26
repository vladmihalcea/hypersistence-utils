package io.hypersistence.utils.spring.repo.querymethod;

import io.hypersistence.utils.spring.repo.querymethod.domain.Post;
import io.hypersistence.utils.spring.repo.querymethod.domain.PostComment;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = SpringDataJPAQueryMethodConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class SpringDataJPAQueryMethodTest {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private PostCommentRepository postCommentRepository;

    @Test
    public void testDeleteMethod() {
        transactionTemplate.execute((TransactionCallback<Void>) transactionStatus -> {
            Post post1 = new Post()
                .addPostComment(new PostComment());
            Post post2 = new Post();

            assertEquals(0, postRepository.count());
            assertEquals(0, postCommentRepository.count());

            postRepository.save(post1);
            postRepository.save(post2);

            assertEquals(2, postRepository.count());
            assertEquals(1, postCommentRepository.count());

            postRepository.deleteByCommentsIsNull();

            assertEquals(1, postRepository.count());
            assertEquals(1, postCommentRepository.count());

            postRepository.delete(post1);

            assertEquals(0, postRepository.count());
            assertEquals(0, postCommentRepository.count());

            return null;
        });
    }
}

