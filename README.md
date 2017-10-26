[![License](https://img.shields.io/github/license/vladmihalcea/hibernate-types.svg)](https://raw.githubusercontent.com/vladmihalcea/hibernate-types/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.vladmihalcea/hibernate-types-parent.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vladmihalcea%22)
[![JavaDoc](https://javadoc-emblem.rhcloud.com/doc/com.vladmihalcea/hibernate-types-parent/badge.svg?color=blue)](http://www.javadoc.io/doc/com.vladmihalcea/hibernate-types-52)

### Introduction

The Hibernate Types repository gives you extra types that are not supported by the Hibernate ORM core.

### Features 

* [Jackson `JsonNode`](https://vladmihalcea.com/2017/08/08/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/)
* [Any Java object to String or Binary JSON column mapping](https://vladmihalcea.com/2016/06/20/how-to-map-json-objects-using-generic-hibernate-types/)
* [PostgreSQL ARRAY mapping](https://vladmihalcea.com/2017/06/21/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/)
* [`Character` to nullable CHAR column](https://vladmihalcea.com/2016/09/22/how-to-implement-a-custom-basic-type-using-hibernate-usertype/)
* [`ImmutableType` utiility to simplify `UserType` implementations](https://vladmihalcea.com/2016/09/22/how-to-implement-a-custom-basic-type-using-hibernate-usertype/)

### Installation

Depending on the Hibernate version you are using, you need to following dependency:

#### Hibernate 5.2

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-52</artifactId>
        <version>1.1.0</version>
    </dependency>

#### Hibernate 5.1 and 5.0

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-5</artifactId>
        <version>1.1.0</version>
    </dependency>
    
#### Hibernate 4.3

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-43</artifactId>
        <version>1.1.0</version>
    </dependency>

#### Hibernate 4.2 and 4.1

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-4</artifactId>
        <version>1.1.0</version>
    </dependency>

### If you like it, you are going to love my book as well! 

<a href="https://leanpub.com/high-performance-java-persistence?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes">
<img src="https://vladmihalcea.files.wordpress.com/2015/11/hpjp_small.jpg" alt="High-Performance Java Persistence">
</a>

### Requirements

* Java 1.6 or above. Same as the Hibernate ORM version you are using.
* SLF4J

