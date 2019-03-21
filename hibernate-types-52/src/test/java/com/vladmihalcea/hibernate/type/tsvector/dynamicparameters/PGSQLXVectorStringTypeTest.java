package com.vladmihalcea.hibernate.type.tsvector.dynamicparameters;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.junit.Test;

import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;

/**
 * @author Lukman Adekunle
 */
public class PGSQLXVectorStringTypeTest extends AbstractPostgreSQLIntegrationTest {

    private DocX _docX;
    private String _initialTokens;
    private String _updatedTokens;
    private String _initialDocumentText = "The quick brown fox jumped over the lazy dog";
    private String _updatedDocumentText = "The five boxing wizards jump quickly";

    @Override
    public void afterInit() {
        doInJDBC(connection -> {
            ResultSet resultSet = connection.createStatement().executeQuery("select  to_tsvector('" + _initialDocumentText + "')");
            while (resultSet.next()) {
                _initialTokens = resultSet.getString(1);
            }
        });

        doInJDBC(connection -> {
            ResultSet resultSet = connection.createStatement().executeQuery("select  to_tsvector('" + _updatedDocumentText + "')");
            while (resultSet.next()) {
                _updatedTokens = resultSet.getString(1);
            }
        });

        _docX = doInJPA(entityManager -> {
            DocX docX = new DocX();
            docX.setDocText(_initialDocumentText);
            docX.setxVector(_initialTokens);
            entityManager.persist(docX);
            return docX;
        });
    }

    @Test
    public void testFindById() {
        DocX updatedDocX = doInJPA(entityManager -> {
            DocX docX = entityManager.find(DocX.class, _docX.getId());

            assertEquals(_initialDocumentText, docX.getDocText());
            assertEquals(_initialTokens, docX.getxVector());

            docX.setDocText(_updatedDocumentText);
            docX.setxVector(_updatedTokens);

            return docX;
        });

        assertEquals(_updatedDocumentText, updatedDocX.getDocText());
        assertEquals(_initialTokens, _docX.getxVector());
    }

    @Test
    public void testJPQLQuery() {
        doInJPA(entityManager -> {
            DocX docX = entityManager.createQuery(
                    "select d from DocX d where d.docText is not null", DocX.class)
                    .getSingleResult();

            assertEquals(_initialDocumentText, docX.getDocText());
            assertEquals(_initialTokens, docX.getxVector());
        });
    }

    @Test
    public void testNativeQuery() {
        doInJPA(entityManager -> {
            DocX doc = (DocX) entityManager.createNativeQuery("select d.* from docx d where d.id = :id_param", DocX.class)
                    .setParameter("id_param", _docX.getId())
                    .getSingleResult();

            assertEquals(_initialDocumentText, doc.getDocText());
            assertEquals(_initialTokens, doc.getxVector());
        });
    }

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                DocX.class
        };
    }

}