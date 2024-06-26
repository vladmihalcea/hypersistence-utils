package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.hibernate.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class PostgreSQLLTreeTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Tree.class
        };
    }

    @Override
    protected void beforeInit() {
        executeStatement("CREATE EXTENSION IF NOT EXISTS ltree");
    }

    @Test
    public void test() {
        Tree treeWithNullPath = new Tree();
        treeWithNullPath.setId(1L);
        persist(treeWithNullPath);
        testFindById(treeWithNullPath.getId(), treeWithNullPath.getPath());

        Tree treeWithPath = new Tree();
        treeWithPath.setId(2L);
        treeWithPath.setPath("Top.Collections.Pictures.Astronomy.Stars");
        persist(treeWithPath);
        testFindById(treeWithPath.getId(), treeWithPath.getPath());

        testFindTreeByPath(treeWithPath.getPath(), treeWithPath);
    }

    private void persist(final Tree tree) {
        doInJPA(new JPATransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                entityManager.persist(tree);

                return null;
            }
        });
    }

    private void testFindById(final Long treeId, final String expectedPath) {
        doInJPA(new JPATransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Tree tree = entityManager.find(Tree.class, treeId);

                assertEquals(expectedPath, tree.getPath());

                return null;
            }
        });
    }

    private void testFindTreeByPath(final String searchablePath, final Tree expectedTree) {
        doInJPA(new JPATransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                CriteriaBuilder builder = entityManager.getCriteriaBuilder();

                CriteriaQuery<Tree> criteria = builder.createQuery(Tree.class);

                Root<Tree> root = criteria.from(Tree.class);

                criteria.where(
                    builder.equal(root.get("path"), searchablePath)
                );

                List<Tree> trees = entityManager
                    .createQuery(criteria).getResultList();

                assertEquals(1, trees.size());

                Tree tree = trees.iterator().next();

                assertEquals(expectedTree.getId(), tree.getId());
                assertEquals(expectedTree.getPath(), tree.getPath());

                return null;
            }
        });
    }

    @Table(name = "tree")
    @Entity(name = "Tree")
    @TypeDef(name = "ltree", typeClass = PostgreSQLLTreeType.class)
    public static class Tree {

        @Id
        private Long id;

        @Type(type = "ltree")
        @Column(columnDefinition = "ltree")
        private String path;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }
    }
}