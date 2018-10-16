[![License](https://img.shields.io/github/license/vladmihalcea/hibernate-types.svg)](https://raw.githubusercontent.com/vladmihalcea/hibernate-types/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.vladmihalcea/hibernate-types-parent.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vladmihalcea%22)
[![JavaDoc](http://javadoc.io/badge/com.vladmihalcea/hibernate-types-52.svg)](http://www.javadoc.io/doc/com.vladmihalcea/hibernate-types-52)

### Introduction

The Hibernate Types repository gives you extra types that are not supported by the Hibernate ORM core.

### Features

#### JSON 

* [Jackson `JsonNode`](https://vladmihalcea.com/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/)
* [Java Object to String or Binary JSON column mapping](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/)
* [Java Collection to String or Binary JSON column mapping](https://vladmihalcea.com/how-to-map-json-collections-using-jpa-and-hibernate/)
* [How to customize the Jackson ObjectMapper used by Hibernate-Types](https://vladmihalcea.com/hibernate-types-customize-jackson-objectmapper/)
* [How to customize the JSON Serializer used by Hibernate-Types](https://vladmihalcea.com/how-to-customize-the-json-serializer-used-by-hibernate-types/)
* [How to fix the Hibernate “No Dialect mapping for JDBC type: 1111” issue when mixing JSON types with native SQL queries](https://vladmihalcea.com/hibernate-no-dialect-mapping-for-jdbc-type/)

#### PostgreSQL Types (e.g. ARRAY, ENUM, INET)

* [PostgreSQL ARRAY mapping](https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/)
* [Java Enum to PostgreSQL Enum Type](https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/)
* [How to map the PostgreSQL inet type with JPA and Hibernate](https://vladmihalcea.com/postgresql-inet-type-hibernate/)

#### Generic Types

* [`java.time.YearMonth` to DATE or INTEGER column](https://vladmihalcea.com/java-yearmonth-jpa-hibernate/)
* [`Character` to nullable CHAR column](https://vladmihalcea.com/how-to-implement-a-custom-basic-type-using-hibernate-usertype/)
* [`ImmutableType` utility to simplify `UserType` implementations](https://vladmihalcea.com/how-to-implement-a-custom-basic-type-using-hibernate-usertype/)

### Installation

Depending on the Hibernate version you are using, you need to following dependency:

#### Hibernate 5.4, 5.3 and 5.2

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-52</artifactId>
        <version>2.3.1</version>
    </dependency>

#### Hibernate 5.1 and 5.0

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-5</artifactId>
        <version>2.3.1</version>
    </dependency>
    
#### Hibernate 4.3

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-43</artifactId>
        <version>2.3.1</version>
    </dependency>

#### Hibernate 4.2 and 4.1

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-4</artifactId>
        <version>2.3.1</version>
    </dependency>

### If you like it, you are going to love my book as well! 

<a href="https://vladmihalcea.com/books/high-performance-java-persistence?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes">
<img src="https://vladmihalcea.files.wordpress.com/2015/11/hpjp_small.jpg" alt="High-Performance Java Persistence">
</a>

### Requirements

* Java version supported by the Hibernate ORM version you are using.
* SLF4J
* Jackson Databind

