package com.vladmihalcea.hibernate.type.tsvector;

import org.hibernate.annotations.TypeDef;

import javax.persistence.*;


@Entity(name = "Document")
@Table(name = "document")
@TypeDef(name = "tsvector", typeClass = PostgresSQLTSVectorType.class, defaultForType = TSVector.class)
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text")
    private String docText;

    @Column(name = "tokens", columnDefinition = "tsvector")
    private TSVector tsVector;

    public Long getId() {
        return id;
    }

    public String getDocText() {
        return docText;
    }

    public void setDocText(String docText) {
        this.docText = docText;
    }

    public TSVector getTsVector() {
        return tsVector;
    }

    public void setTsVector(TSVector tsVector) {
        this.tsVector = tsVector;
    }
}

