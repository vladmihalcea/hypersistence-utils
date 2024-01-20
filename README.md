[![License](https://img.shields.io/github/license/vladmihalcea/hypersistence-utils.svg)](https://raw.githubusercontent.com/vladmihalcea/hypersistence-utils/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.hypersistence/hypersistence-utils-parent.svg)](https://central.sonatype.com/artifact/io.hypersistence/hypersistence-utils-hibernate-60/)
[![JavaDoc](http://javadoc.io/badge/io.hypersistence/hypersistence-utils-hibernate-60.svg)](http://www.javadoc.io/doc/io.hypersistence/hypersistence-utils-hibernate-60)

### Migrating to version 3

The project name was changed from Hibernate Types to Hypersistence Utils because the scope
of the project is much broader now, offering Spring utilities as well.

For this reason, when migrating from the Hibernate Types 2.x to Hypersistence Utils 3.x,
you will need to follow these steps:

1. First, you will need to change your Maven or Gradle dependency, as illustrated by the [Installation Guide](#installation-guide).
2. Second, you will need to change the package name from `com.vladmihalcea.hibernate` to `io.hypersistence.utils.hibernate`.
3. Third, you will need to change the package name from `com.vladmihalcea.spring` to `io.hypersistence.utils.spring`.

That's it!

### Introduction

The Hypersistence Utils project gives you general-purpose utilities for both Spring and Hibernate. 

The main advantage of this project is that it supports a broad range of Hibernate versions, spanning from **Hibernate ORM 6.3**, **6.2** to **6.1**, **6.0**, **5.6**, **5.5**, **5.4**, **5.3**, and Hibernate **5.2**.

### Installation Guide

Depending on the Hibernate version you are using, you need to add the following dependency:

#### Hibernate 6.3

    <dependency>
        <groupId>io.hypersistence</groupId>
        <artifactId>hypersistence-utils-hibernate-63</artifactId>
        <version>3.7.0</version>
    </dependency>

#### Hibernate 6.2

    <dependency>
        <groupId>io.hypersistence</groupId>
        <artifactId>hypersistence-utils-hibernate-62</artifactId>
        <version>3.7.0</version>
    </dependency>

#### Hibernate 6.1 and 6.0

    <dependency>
        <groupId>io.hypersistence</groupId>
        <artifactId>hypersistence-utils-hibernate-60</artifactId>
        <version>3.7.0</version>
    </dependency>

#### Hibernate 5.6 and 5.5

    <dependency>
        <groupId>io.hypersistence</groupId>
        <artifactId>hypersistence-utils-hibernate-55</artifactId>
        <version>3.7.0</version>
    </dependency>

#### Hibernate 5.4, 5.3 and 5.2

    <dependency>
        <groupId>io.hypersistence</groupId>
        <artifactId>hypersistence-utils-hibernate-52</artifactId>
        <version>3.7.0</version>
    </dependency>

#### Optional Maven Dependencies

The Hypersistence Utils project defines a list of optional dependencies that you will have to declare explicitly in your project in order to use them.

The reason why all these dependencies are optional, like Guava, Jackson, or PostgreSQL JDBC Driver, is that not all projects may need them.

Moreover, the dependency version is extremely important because, from time to time, security issues may be discovered that get fixed in newer versions.

> So, relying on this library to supply you with the dependency versions is a very dangerous thing to do. 
> 
> For instance, there have been [65 security issues discovered in the Jackson Data Bind library](https://www.cvedetails.com/vulnerability-list/vendor_id-15866/product_id-42991/Fasterxml-Jackson-databind.html) this project is heavily relying on. 
> 
> To avoid risking security issues, you need to take the responsibility of constantly upgrading all the dependencies that you are using along with the Hypersistence Utils library.

##### JSON Optional Maven Dependencies

If you are using JSON Types, then you might be interested in setting the following dependencies based on your Hibernate version:

###### Hibernate 6.3, 6.2, 6.1 and 6.0

````xml
<dependency>
    <groupId>com.fasterxml.jackson.module</groupId>
    <artifactId>jackson-module-jakarta-xmlbind-annotations</artifactId>
    <version>${jackson-module-jakarta-xmlbind-annotation}</version>
</dependency>
````

###### Hibernate 5.6, 5.5, 5.4, 5.3, and 5.2

````xml
<dependency>
    <groupId>com.fasterxml.jackson.core</groupId>
    <artifactId>jackson-databind</artifactId>
    <version>${jackson-databind.version}</version>
</dependency>

<dependency>
    <groupId>com.fasterxml.jackson.module</groupId>
    <artifactId>jackson-module-jaxb-annotations</artifactId>
    <version>${jackson-module-jaxb-annotation}</version>
</dependency>
````

##### Guava Optional Maven Dependency

If you are mapping a `Range` using Guava, then you have to provide the Guava dependency explicitly:

````xml
<dependency>
    <groupId>com.google.guava</groupId>
    <artifactId>guava</artifactId>
    <version>${guava.version}</version>
</dependency>
````

##### Java Money and Currency API Optional Maven Dependency

If you are mapping a `MonetaryAmount`, then you have to provide the Moneta dependency explicitly:

````xml
<dependency>
    <groupId>org.javamoney</groupId>
    <artifactId>moneta</artifactId>
    <version>${moneta.version}</version>
    <type>pom</type>
</dependency>
````

##### PostgreSQL Optional Maven Dependency

If you are mapping a PostgreSQL-specific column type (e.g., `inet`, `hstore`, `array`, `interval`), then you have to provide the PostgreSQL dependency explicitly:

````xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>${postgresql.version}</version>
</dependency>
````

### Features

#### JSON 

##### Generic JSON Type

The `JsonType` allows you to map JSON column types, no matter if you're using Oracle,
SQL Server, PostgreSQL or MySQL.

###### Hibernate 6

If you're using Hibernate 6, you can map any JSON column to `Map`, `List`, POJO, `String`, or `JsonNode` entity property:

````java
@Type(JsonType.class)
private Map<String, String> properties = new HashMap<>();
````

###### Hibernate 5

If you're using Hibernate 5, you can either provide the fully-qualified name of the Hibernate Type:

````
@Type(type = "io.hypersistence.utils.hibernate.type.json.JsonType")
````

Or, you can add the following mapping to your `package-info.java` class in the same package where your JPA entities are located:

````java
@TypeDef(
    name = "json", typeClass = JsonType.class
)
package io.hypersistence.optimizer;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import org.hibernate.annotations.TypeDef;
````

Later, you can map the `Map`, `List`, POJO, `String`, or `JsonNode` entity properties to JSON columns like this:

````java
@Type(type = "json")
private Map<String, String> properties = new HashMap<>();
````

For more details, check out [this article](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/).

##### Best Practices

> When mapping a JSON column type to a `POJO`, `List<POJO>` or `Map<String, POJO>`, you need to make sure that the `POJO` type overrides the default `equals` and `hashCode` methods and implements them according to the JSON object content. 
> 
> Otherwise, the Hibernate dirty checking mechanism may trigger unexpected UPDATE statements. Check out the [#134 issue for more details](https://github.com/vladmihalcea/hypersistence-utils/issues/138).

##### Database-specific JSON types

###### Oracle

When using Oracle, you have several options:

- you can use the generic `JsonType` that can work with the `JSON`, `VARCHAR`, or `BLOB` column types,
as long as you hint the column type using the `columnDefinition` attribute of the JPA `@Column` annotation.
- you can use the `JsonStringType` to map a `VARCHAR2` column type storing JSON.
- you can use the `JsonBlobType` to map a `BLOB` column type storing JSON.

For more details, check out [this article](https://vladmihalcea.com/oracle-json-jpa-hibernate/).

###### SQL Server

When using SQL Server, you can use the generic `JsonType` or the `JsonStringType` to map an `NVARCHAR` column type storing JSON.

For more details, check out [this article](https://vladmihalcea.com/sql-server-json-hibernate/).

###### PostgreSQL

When using PostgreSQL, you can use the generic `JsonType` or the `JsonBinaryType` to map both `jsonb` and `json` column types.

For more details, check out [this article](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/).

###### MySQL

When using MySQL, you can use the generic `JsonType` or the `JsonStringType` to map the `json` column type.

For more details, check out [this article](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/).

##### JSON mapping examples

* [How to map a POJO to a JSON column using JPA and Hibernate](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/)
* [How to map a String JPA property to a JSON column using Hibernate](https://vladmihalcea.com/map-string-jpa-property-json-column-hibernate/)
* [How to map a Jackson `JsonNode` to a JSON column](https://vladmihalcea.com/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/)
* [How to map JSON collections using JPA and Hibernate](https://vladmihalcea.com/how-to-map-json-collections-using-jpa-and-hibernate/)
* [How to map a Java Map entity property as a JSON column with JPA and Hibernate](https://vladmihalcea.com/java-map-json-jpa-hibernate/)
* [How to map Java Records to JSON columns using Hibernate](https://vladmihalcea.com/java-records-json-hibernate/)
* [How to map polymorphic JSON objects with JPA and Hibernate](https://vladmihalcea.com/polymorphic-json-objects-hibernate/)
* [How to encrypt and decrypt JSON properties with JPA and Hibernate](https://vladmihalcea.com/encrypt-decrypt-json-jpa/)
* [Why you should use Hibernate Dynamic Update for JSON properties](https://vladmihalcea.com/hibernate-dynamic-update-json-properties/)
* [How to map Oracle JSON columns using JPA and Hibernate](https://vladmihalcea.com/oracle-json-jpa-hibernate/)
* [How to map SQL Server JSON columns using JPA and Hibernate](https://vladmihalcea.com/sql-server-json-hibernate/)
* [How to customize the Jackson ObjectMapper used by Hypersistence Utils](https://vladmihalcea.com/hibernate-types-customize-jackson-objectmapper/)
* [How to customize the JSON Serializer used by Hypersistence Utils](https://vladmihalcea.com/how-to-customize-the-json-serializer-used-by-hibernate-types/)
* [How to fix the Hibernate `No Dialect mapping for JDBC type: 1111` issue when mixing JSON types with native SQL queries](https://vladmihalcea.com/hibernate-no-dialect-mapping-for-jdbc-type/)
* [How to fix the `column is of type jsonb but expression is of type record` or `bytea` issue](https://vladmihalcea.com/jpa-query-setparameter-hibernate/)

#### ARRAY 

* [How to map a PostgreSQL ARRAY to a Java `List` with JPA and Hibernate](https://vladmihalcea.com/postgresql-array-java-list/)
* [How to map the PostgreSQL ARRAY to Java Arrays with JPA and Hibernate](https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/)
* [Multidimensional array mapping with JPA and Hibernate](https://vladmihalcea.com/multidimensional-array-jpa-hibernate/)
* [How to map the HSQLDB ARRAY type with JPA and Hibernate](https://vladmihalcea.com/hibernate-hsqldb-array-type/)

#### PostgreSQL Types (e.g. ENUM, INET, HSTORE, RANGE)

* [Java Enum to PostgreSQL Enum Type](https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/)
* [How to map the PostgreSQL Inet type with JPA and Hibernate](https://vladmihalcea.com/postgresql-inet-type-hibernate/)
* [How to map a PostgreSQL HStore entity property with JPA and Hibernate](https://vladmihalcea.com/map-postgresql-hstore-jpa-entity-property-hibernate/)
* [How to map a PostgreSQL Enum ARRAY to a JPA entity property using Hibernate](https://vladmihalcea.com/map-postgresql-enum-array-jpa-entity-property-hibernate/)
* [How to map a PostgreSQL Range column type with JPA and Hibernate](https://vladmihalcea.com/map-postgresql-range-column-type-jpa-hibernate/)
* [How to map a PostgreSQL Interval to a Java `Duration` with Hibernate](https://vladmihalcea.com/map-postgresql-interval-java-duration-hibernate/)

#### Generic Types

* [The best way to map `MonetaryAmount` with JPA and Hibernate](https://vladmihalcea.com/monetaryamount-jpa-hibernate/)
* [How to map `java.time.YearMonth` to DATE or INTEGER column](https://vladmihalcea.com/java-yearmonth-jpa-hibernate/)
* [How to map `java.time.Year` and `java.time.Month` with JPA and Hibernate](https://vladmihalcea.com/java-time-year-month-jpa-hibernate/)
* [`Character` to nullable CHAR column](https://vladmihalcea.com/how-to-implement-a-custom-basic-type-using-hibernate-usertype/)
* [`ImmutableType` utility to simplify `UserType` implementations](https://vladmihalcea.com/how-to-implement-a-custom-basic-type-using-hibernate-usertype/)

#### Utilities

##### Spring

* [The awesome BaseJpaRepository](https://vladmihalcea.com/basejparepository-hypersistence-utils/)
* [The best Spring Data JpaRepository](https://vladmihalcea.com/best-spring-data-jparepository/)
* [The Spring Data `findAll` Anti-Pattern](https://vladmihalcea.com/spring-data-findall-anti-pattern/)
* [The Spring `@Retry` annotation to automatically retry on failure](https://vladmihalcea.com/optimistic-locking-retry-with-jpa/)

###### BaseJpaRepository

The `BaseJpaRepository` is a much better alternative to the default Spring Data `JpaRepository` because it does not provide a `findAll` method or a `save` method that makes no sense in JPA terminology.

To use the `BaseJpaRepository` utility, make sure that you provide the `repositoryBaseClass` attribute in the `@EnableJpaRepositories` configuration to reference the `BaseJpaRepositoryImpl` from the Hypersistence Utils project:

````java
@Configuration
@EnableJpaRepositories(
    value = "your.repository.package",
    repositoryBaseClass = BaseJpaRepositoryImpl.class
)
public class JpaConfiguration {
    ...
}
````

The `your.repository.package` is the Java package of your Spring repositories.

###### HibernateRepository

While the `BaseJpaRepository` is to be preferred, in case you need to use the default `JpaRepository`, then you can at least extend the `HibernateRepository` as well to deprecate the methods that may cause problems.

To use the `HibernateRepository`, make sure that you include the `io.hypersistence.utils.spring.repository` package in your `@EnableJpaRepositories` configuration:

````java
@Configuration
@EnableJpaRepositories(
    value = {
        "io.hypersistence.utils.spring.repository",
        "your.repository.package",
        ...
    }
)
public class JpaConfiguration {
    ...
}
````

The `your.repository.package` is the Java package of your Spring repositories.

##### Identifier Generators

* [`BatchSequenceGenerator` - Hibernate Batch Sequence Generator](https://vladmihalcea.com/hibernate-batch-sequence-generator/)

##### Naming Strategy

* [`CamelCaseToSnakeCaseNamingStrategy` - How to map camelCase properties to snake_case column names with Hibernate](https://vladmihalcea.com/map-camel-case-properties-snake-case-column-names-hibernate/)

##### DTO Projection and ResultTransformer

* [`ClassImportIntegrator` - How to write a compact DTO projection query with JPA](https://vladmihalcea.com/dto-projection-jpa-query/)
* [`ListResultTransformer` - The best way to use a Hibernate ResultTransformer](https://vladmihalcea.com/hibernate-resulttransformer/)
* [`MapResultTransformer` - How to return a Map result from a JPA or Hibernate query](https://vladmihalcea.com/jpa-query-map-result/)

##### SQL Statement Count Validator

* [`SQLStatementCountValidator` - How to detect the Hibernate N+1 query problem during testing](https://vladmihalcea.com/how-to-detect-the-n-plus-one-query-problem-during-testing/)

##### Query Utilities

* [`SQLExtract` - How to get the SQL query from JPQL or JPA Criteria](https://vladmihalcea.com/get-sql-from-jpql-or-criteria/)
* [`QueryStackTraceLogger` - How to find the source of an SQL query generated by Hibernate](https://vladmihalcea.com/source-sql-query-hibernate/)

### Requirements

* Java version supported by the Hibernate ORM version you are using.
* SLF4J
* Jackson Databind

### Bug fixes and enhancements

**This project is Free, as in Libre, not Gratis.**

> There is no free-of-charge support. Only the source code and the binaries are available for free.
> 
> If you need assistance with a given issue, you will need to purchase either the [Coaching Basic](https://vladmihalcea.teachable.com/p/high-performance-java-persistence-coaching-basic) or the [Coaching Pro](https://vladmihalcea.teachable.com/p/high-performance-java-persistence-coaching-pro) programs.

#### Providing your own fix

Besides the Paid support options, you have the option of providing your own fix. For that, here's what you need to do:

1. Provide a replicating test case using the existing test cases as a template
2. Provide a fix proposal
3. Send a Pull Request with the fix proposal and the test case 

However, due to lack of time, I will review the submitted Pull Requests from time to time, so you may need to wait several months until the Pull Request is reviewed and integrated.

### Are you struggling with application performance issues?

<a href="https://vladmihalcea.com/hypersistence-optimizer/?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes">
<img src="https://vladmihalcea.com/wp-content/uploads/2019/03/Hypersistence-Optimizer-300x250.jpg" alt="Hypersistence Optimizer">
</a>

Imagine having a tool that can automatically detect if you are using JPA and Hibernate properly. No more performance issues, no more having to spend countless hours trying to figure out why your application is barely crawling.

Imagine discovering early during the development cycle that you are using suboptimal mappings and entity relationships or that you are missing performance-related settings. 

More, with Hypersistence Optimizer, you can detect all such issues during testing and make sure you don't deploy to production a change that will affect data access layer performance.

[Hypersistence Optimizer](https://vladmihalcea.com/hypersistence-optimizer/?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes) is the tool you've been long waiting for!

#### Training

If you are interested in on-site training, I can offer you my [High-Performance Java Persistence training](https://vladmihalcea.com/trainings/?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes),
which can be adapted to one, two or three days of sessions. For more details, check out [my website](https://vladmihalcea.com/trainings/?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes).

#### Consulting

If you want me to review your application and provide insight into how you can optimize it to run faster, 
then check out my [consulting page](https://vladmihalcea.com/consulting/?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes).

#### High-Performance Java Persistence Video Courses

If you want the fastest way to learn how to speed up a Java database application, then you should definitely enroll in [my High-Performance Java Persistence video courses](https://vladmihalcea.com/courses/?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes).

#### High-Performance Java Persistence Book

Or, if you prefer reading books, you are going to love my [High-Performance Java Persistence book](https://vladmihalcea.com/books/high-performance-java-persistence?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes) as well.

<a href="https://vladmihalcea.com/books/high-performance-java-persistence?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes">
<img src="https://i0.wp.com/vladmihalcea.com/wp-content/uploads/2018/01/HPJP_h200.jpg" alt="High-Performance Java Persistence book">
</a>

<a href="https://vladmihalcea.com/courses?utm_source=GitHub&utm_medium=banner&utm_campaign=hibernatetypes">
<img src="https://i0.wp.com/vladmihalcea.com/wp-content/uploads/2018/01/HPJP_Video_Vertical_h200.jpg" alt="High-Performance Java Persistence video course">
</a>

#### Contributing Guide

The project uses [Maven Toolchains](https://maven.apache.org/guides/mini/guide-using-toolchains.html) as different modules are compiled and tested using different Java versions. 

Hypersistence Utils 6 requires Java 11 while the other modules are compiled with Java 8.

To see how to configure Maven Toolchains, check out [this article](https://vladmihalcea.com/maven-and-java-multi-version-modules/).

The project uses various database systems for integration testing, and you can configure the JDBC connection settings using the 
`DatasourceProvider` instances (e.g., `PostgreSQLDataSourceProvider`), and the project uses Testcontainers to bootstrap a Docker container
with the required Oracle, SQL Server, PostgreSQL, or MySQL instance on demand. 

> If you are a regular contributor, it's advisable to set up the required database locally or use the Docker Compose configuration provided in the `docker` folder,
> as bootstrapping the containers on demand is slower, and your tests are going to take longer to run.

If you want to fix an issue or add support for a new feature, please provide the associated integration test case that proves the improvement is working as expected.

