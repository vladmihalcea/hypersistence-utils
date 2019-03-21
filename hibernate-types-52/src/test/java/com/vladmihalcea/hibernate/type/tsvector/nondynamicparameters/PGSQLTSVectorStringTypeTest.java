package com.vladmihalcea.hibernate.type.tsvector.nondynamicparameters;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.junit.Test;

import java.sql.ResultSet;

import static org.junit.Assert.assertEquals;

/**
 * @author Lukman Adekunle
 */
public class PGSQLTSVectorStringTypeTest extends AbstractPostgreSQLIntegrationTest {

    private Document _document;
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

        _document = doInJPA(entityManager -> {
            Document document = new Document();
            document.setDocText(_initialDocumentText);
            PGTSVector tsVector = new PGTSVector();
            tsVector.setValue(_initialTokens);
            document.setTsVector(tsVector);
            entityManager.persist(document);
            return document;
        });
    }

    @Test
    public void testFindById() {
        Document updatedDocument = doInJPA(entityManager -> {
            Document document = entityManager.find(Document.class, _document.getId());

            assertEquals(_initialDocumentText, document.getDocText());
            assertEquals(_initialTokens, document.getTsVector().getValue());

            document.setDocText(_updatedDocumentText);
            PGTSVector tsVector = new PGTSVector();
            tsVector.setValue(_updatedTokens);
            document.setTsVector(tsVector);

            return document;
        });

        assertEquals(_updatedDocumentText, updatedDocument.getDocText());
        assertEquals(_initialTokens, _document.getTsVector().getValue());
    }

    @Test
    public void testJPQLQuery() {
        doInJPA(entityManager -> {
            Document document = entityManager.createQuery(
                    "select d from Document d where d.docText is not null", Document.class)
                    .getSingleResult();

            assertEquals(_initialDocumentText, document.getDocText());
            assertEquals(_initialTokens, document.getTsVector().getValue());
        });
    }

    @Test
    public void testNativeQuery() {
        doInJPA(entityManager -> {
            Document doc = (Document) entityManager.createNativeQuery("select d.* from document d where d.id = :id_param", Document.class)
                    .setParameter("id_param", _document.getId())
                    .getSingleResult();

            assertEquals(_initialDocumentText, doc.getDocText());
            assertEquals(_initialTokens, doc.getTsVector().getValue());
        });
    }

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Document.class
        };
    }

}