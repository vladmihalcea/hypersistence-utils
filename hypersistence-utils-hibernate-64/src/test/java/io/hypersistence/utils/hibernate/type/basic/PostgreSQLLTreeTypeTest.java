package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.test.transaction.EntityManagerTransactionFunction;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Root;
import org.hibernate.annotations.Type;
import org.junit.Test;

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
    public void init() {
        DataSource dataSource = newDataSource();
        Connection connection = null;
        Statement statement = null;
        try {
            connection = dataSource.getConnection();
            statement = connection.createStatement();

            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS ltree");
        } catch (SQLException e) {
            fail(e.getMessage());
        } finally {
            if (statement != null) {
                try {
                    statement.close();
                } catch (SQLException e) {
                    fail(e.getMessage());
                }
            }
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    fail(e.getMessage());
                }
            }
        }
        super.init();
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
        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                entityManager.persist(tree);

                return null;
            }
        });
    }

    private void testFindById(final Long treeId, final String expectedPath) {
        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Tree tree = entityManager.find(Tree.class, treeId);

                assertEquals(expectedPath, tree.getPath());

                return null;
            }
        });
    }

    private void testFindTreeByPath(final String searchablePath, final Tree expectedTree) {
        doInJPA(new EntityManagerTransactionFunction<Void>() {
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
    public static class Tree {

        @Id
        private Long id;

        @Type(PostgreSQLLTreeType.class)
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