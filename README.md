[![License](https://img.shields.io/github/license/vladmihalcea/hibernate-types.svg)](https://raw.githubusercontent.com/vladmihalcea/hibernate-types/master/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/com.vladmihalcea/hibernate-types-parent.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%22com.vladmihalcea%22)
[![JavaDoc](http://javadoc.io/badge/com.vladmihalcea/hibernate-types-52.svg)](http://www.javadoc.io/doc/com.vladmihalcea/hibernate-types-52)

### Introduction

The Hibernate Types repository gives you extra types and general purpose utilities that are not supported by the Hibernate ORM core. 

The main advantage of this project is that it supports a broad range of Hibernate versions, spanning from Hibernate 4.1 to Hibernate 5.4.

### Features

#### JSON 

##### Oracle

You should use the `JsonStringType` to map a `VARCHAR2` column type storing JSON.

You should use the `JsonBlobType` to map a `BLOB` column type storing JSON.

For more details, check out [this article](https://vladmihalcea.com/oracle-json-jpa-hibernate/).

##### SQL Server

You should use this `JsonStringType` to map an `NVARCHAR` column type storing JSON.

For more details, check out [this article](https://vladmihalcea.com/sql-server-json-hibernate/).

##### PostgreSQL

You should use this `JsonBinaryType` to map both `jsonb` and `json` column types.

For more details, check out [this article](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/).

##### MySQL

You should use this `JsonStringType` to map the `json` column type.

For more details, check out [this article](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/).

##### JSON mapping examples

* [How to map Oracle JSON columns using JPA and Hibernate](https://vladmihalcea.com/oracle-json-jpa-hibernate/)
* [How to map SQL Server JSON columns using JPA and Hibernate](https://vladmihalcea.com/sql-server-json-hibernate/)
* [How to map a Jackson `JsonNode` to a JSON column](https://vladmihalcea.com/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/)
* [How to map a POJO to a JSON column using JPA and Hibernate](https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/)
* [How to map JSON collections using JPA and Hibernate](https://vladmihalcea.com/how-to-map-json-collections-using-jpa-and-hibernate/)
* [How to map a Java Map entity property as a JSON column with JPA and Hibernate](https://vladmihalcea.com/java-map-json-jpa-hibernate/)
* [How to customize the Jackson ObjectMapper used by Hibernate-Types](https://vladmihalcea.com/hibernate-types-customize-jackson-objectmapper/)
* [How to customize the JSON Serializer used by Hibernate-Types](https://vladmihalcea.com/how-to-customize-the-json-serializer-used-by-hibernate-types/)
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

* [How to map `java.time.YearMonth` to DATE or INTEGER column](https://vladmihalcea.com/java-yearmonth-jpa-hibernate/)
* [How to map `java.time.Year` and `java.time.Month` with JPA and Hibernate](https://vladmihalcea.com/java-time-year-month-jpa-hibernate/)
* [`Character` to nullable CHAR column](https://vladmihalcea.com/how-to-implement-a-custom-basic-type-using-hibernate-usertype/)
* [`ImmutableType` utility to simplify `UserType` implementations](https://vladmihalcea.com/how-to-implement-a-custom-basic-type-using-hibernate-usertype/)

#### Utilities

##### Naming Strategy

* [`CamelCaseToSnakeCaseNamingStrategy` - How to map camelCase properties to snake_case column names with Hibernate](https://vladmihalcea.com/map-camel-case-properties-snake-case-column-names-hibernate/)

##### DTO Projection and ResultTransformer

* [`ClassImportIntegrator` - How to write a compact DTO projection query with JPA](https://vladmihalcea.com/dto-projection-jpa-query/)
* [`ListResultTransformer` - The best way to use a Hibernate ResultTransformer](https://vladmihalcea.com/hibernate-resulttransformer/)
* [`MapResultTransformer` - How to return a Map result from a JPA or Hibernate query](https://vladmihalcea.com/jpa-query-map-result/)

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

### Installation

Depending on the Hibernate version you are using, you need to add the following dependency:

#### Hibernate 5.4, 5.3 and 5.2

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-52</artifactId>
        <version>2.9.12</version>
    </dependency>

#### Hibernate 5.1 and 5.0

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-5</artifactId>
        <version>2.9.12</version>
    </dependency>
    
#### Hibernate 4.3

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-43</artifactId>
        <version>2.9.12</version>
    </dependency>

#### Hibernate 4.2 and 4.1

    <dependency>
        <groupId>com.vladmihalcea</groupId>
        <artifactId>hibernate-types-4</artifactId>
        <version>2.9.12</version>
    </dependency>

### Requirements

* Java version supported by the Hibernate ORM version you are using.
* SLF4J
* Jackson Databind

### How to remove the Hypersistence Optimizer banner from the log?

#### Why the Hypersistence Optimizer banner?

Maintaining this project costs thousands of dollars per year, and, without the support of [Hypersistence](https://hypersistence.io/),
this project would have to be abandoned.

> The reason why this banner was added is that, when I enabled GitHub donations, [no one ever donated a single cent](
https://twitter.com/vlad_mihalcea/status/1194584719133958146) to support this project.

And, spreading the news about the amazing [Hypersistence Optimizer](https://vladmihalcea.com/hypersistence-optimizer/) tool I'm currently building is much more beneficial for the vast majority of Hibernate projects.

#### Adding Hypersistence Optimizer to your project

Using Hibernate without [Hypersistence Optimizer](https://vladmihalcea.com/hypersistence-optimizer/) is highly discouraged, hence the reason for that banner. You can view it as a `WARN` log message that tells you are risking application performance issues if you don't make sure you use the right JPA mappings and Hibernate configuration properties.

> If you want to see why it's a bad idea to use JPA and Hibernate without a tool that inspects your mappings and configurations, check out this [video presentation](https://www.youtube.com/watch?v=x1nOVct9P2g).

So, the easiest way to have the banner removed is to add [Hypersistence Optimizer](https://vladmihalcea.com/hypersistence-optimizer/) to your project.

### Setting the `hibernate.types.print.banner=false` configuration setting

You can disable the banner by providing the `hibernate.types.print.banner=false` in either `hibernate.properties` or `hibernate-types.properties` file.

> For Spring and Spring Boot, this [Pull Request](https://github.com/hibernate/hibernate-orm/pull/2649) is needed to be integrated into Hibernate ORM. 
>
> Once this Pull Request is integrated, you could pass the `hibernate.types.print.banner=false` configuration property from the `application.properties` file. 
>
> So, in the meanwhile, you could vote for the [HHH-14020 issue](https://hibernate.atlassian.net/browse/HHH-14020) and remind the Hibernate team that you really need that Pull Request to be integrated. Hopefully, it will be added to the project sooner than later.

If can also provide the `hibernate.types.print.banner=false`setting as a Java System property when bootstrapping your Java application:

    java -Dhibernate.types.print.banner=false -jar target/application-1.0.0.jar
    
#### Will I remove the banner?

I know the banner might be a little annoying. But you know what's even more annoying? Having all those [open issues](https://github.com/vladmihalcea/hibernate-types/issues) waiting to be fixed.

So, I decided that I would remove the banner only when the vast majority of the currently opened issues were going to be fixed. If you don't have the time to do it, I totally understand that. I don't have the time to do it either. But, I could be paid to do that, and then, you could all benefit from those new features. I estimated the cost for working on all those issues to be around 25000$. If anyone is willing to sponsor the development of this project, don't hesitate to contact me.

> If you're wondering why that banner was not removed by now, it's because there are still plenty of issues waiting to be fixed, and that's not the true spirit of open-source software development!

##### Should you open a GitHub issue asking me to remove the banner?

Disabling this banner takes very little time, so there is no point in opening a GitHub issue to ask me to remove this banner, 
especially since there's already [a pinned GitHub issue explaining how to remove it](https://github.com/vladmihalcea/hibernate-types/issues/196).
 
Keep in mind that all issues related to the banner removal will be deleted because I use the GitHub issue feature for technical features only (e.g., enhancements, bug fixing).

In case you think that the banner is unfair and disabling it is too much of an effort for you, then you can also:

- fork the repository
- remove the banner from the source code
- build the project and use your own banner-free version

This project is _free_ as in [Libre, not Gratis](https://en.wikipedia.org/wiki/Gratis_versus_libre).

## How to start the test environment

```bash
cd docker
docker-compose up -d
```

## How to stop the test environment

```bash
cd docker
docker-compose down -v
```

## How to get access to database logs

```bash
docker logs -f mysql-hibernate-types
docker logs -f postgresql-hibernate-types
```
