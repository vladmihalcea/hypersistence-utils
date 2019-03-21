package com.vladmihalcea.hibernate.type.tsvector.nondynamicparameters;

import org.hibernate.annotations.TypeDef;

import javax.persistence.*;


@Entity(name = "Document")
@Table(name = "document")
@TypeDef(name = "tsvector", typeClass = TSVectorStringType.class, defaultForType = PGTSVector.class)
public class Document {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text")
    private String docText;

    @Column(name = "tokens", columnDefinition = "tsvector")
    private PGTSVector tsVector;

    public Long getId() {
        return id;
    }

    public String getDocText() {
        return docText;
    }

    public void setDocText(String docText) {
        this.docText = docText;
    }

    public PGTSVector getTsVector() {
        return tsVector;
    }

    public void setTsVector(PGTSVector tsVector) {
        this.tsVector = tsVector;
    }
}

