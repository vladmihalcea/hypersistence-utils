package com.vladmihalcea.hibernate.type.tsvector.dynamicparameters;

import com.vladmihalcea.hibernate.type.tsvector.dynamicparameters.XVectorStringType;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;


@Entity
@Table(name = "docx")
@TypeDef(name = "tsvector", typeClass = XVectorStringType.class)
public class DocX {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "text")
    private String docText;

    @Type(type = "tsvector")
    @Column(name = "token", columnDefinition = "tsvector")
    private String xVector;

    public Long getId() {
        return id;
    }

    public String getDocText() {
        return docText;
    }

    public void setDocText(String docText) {
        this.docText = docText;
    }

    public String getxVector() {
        return xVector;
    }

    public void setxVector(String xVector) {
        this.xVector = xVector;
    }
}

