# **Jade-Tipi: JSON Transparent Data Protocol**

**A Modern Protocol for Managing Metadata at the JGI and Beyond**

# **Summary**

Jade-Tipi (JDTP) is designed to be a domain-agnostic, technology-agnostic foundation for any organization needing scalable, FAIR, machine-actionable metadata.  By addressing key shortcomings of current FAIR systems, Jade-Tipi provides a frictionless ecosystem where data is easily shared and fully machine-actionable.

**Key Benefits:**

* **Flexible, Rapid Evolution:** Metadata is stored as JSON so new data types and properties can be added without disruptive migrations or schema changes.  
* **Designed to Work with Established Schema Languages:** May store objects constrained by LinkML, JSON Schema, Protocol Buffer, and Avro schemas.  See: [Extending Jade-Tipi with LinkML and External Schemas](#extending-jade-tipi-with-linkml-and-external-schemas)  
* **Foundation for Automated Science:** Jade-Tipi provides a machine-friendly protocol for adding new metadata and a standard API for retrieving results from past experiments.  
* **Mergeability:** Jade-Tipi metadata repositories are designed to facilitate merging data from different groups and institutions into common repositories.  
* **Granular Collaboration:** Fine-grained permission controls let multiple teams and collaborators safely annotate and enrich metadata, with clear ownership tracking.  
* **Development of Common Vocabulary:** Widespread use will help establish shared vocabularies and canonical properties.  Combining data into common repositories provides the opportunity to translate type and property names to facilitate the evolution of common nomenclature.  
* **Proven Technology Stack:** Leverages established, scalable technologies (Kafka, Flink, FoundationDB, etc.) for reliability and future integrations.  
* **Enables Full FAIR Compliance:** Adopting Jade-Tipi as a LIMS (Laboratory Information Management System) with integrated electronic notebooks would provide the detailed provenance of data.  
* **Seamless Lakehouse Integration:** Publishes all metadata changes to a transaction stream, allowing flexible loading and transformation into one or more lakehouses for analysis and experimentation.  See: [Jade-Tipi Architectural Diagram](#jade-tipi-architectural-diagram)  
* **Extendible Query API: T**he API can be extended to support advanced graph, geospatial, and full-text search, as well as aggregations.

The development and adoption of Jade-Tipi would position the JGI as a leader in open, reproducible science and machine-accessible metadata.

---

# **Introduction**

Jade-Tipi (JDTP) is a specification for metadata management using a collection of JSON objects. Every object in a Jade-Tipi is assigned a **world**\-unique ID.  Naming conventions ensure that IDs are unique: there is no need for a central repository to ensure uniqueness.  IDs are generated on demand by a local service.  Metadata objects are maintained in a repository and retrievable by ID or by links to other objects.

Because Jade-Tipi stores data as schemaless JSON objects, new metadata types can be loaded directly, without the need to modify existing database schemas.  This does not lead to “data mush” because each new object is clearly defined by its type document, which can be easily constructed and modified.

The protocol is entirely text-based, using the commonplace JSON format, and is consequently free of any tie-in to particular vendor implementations.  The JSON objects that comprise the system are completely transparent to machine actors seeking to ingest and develop insights from the repository.

The ID naming conventions allow metadata from one repository to be imported into another without modification.  Repositories managed by different groups can be seamlessly combined into a centralized repository.

# **Vision**

Jade-Tipi is intended as a generic implementation of the FAIR data principles outlined in the Nature article "The FAIR Guiding Principles for Scientific Data Management and Stewardship" ([https://www.nature.com/articles/sdata201618](https://www.nature.com/articles/sdata201618)) and summarized here: [https://www.go-fair.org/fair-principles/](https://www.go-fair.org/fair-principles/).  FAIR guidelines aim to make data sharing and machine access more efficient.  The original paper stresses that while adherence to FAIR principles is a step in the right direction, current FAIR systems suffer shortcomings that are a source of significant friction.  The primary problem is the proliferation of bespoke systems, which require myriad complex custom parsers.  Jade-Tipi offers a flexible, extensible standard suitable for any domain, scientific or otherwise. It promotes a transparent and navigable data ecosystem.

The broad vision for Jade-Tipi is to have it adopted by various systems seeking to follow FAIR guidelines. This adoption could involve translating existing systems into Jade-Tipi or developing new systems based on the protocol. Widespread adoption could foster shared vocabularies, canonical properties, and the development of general-purpose parsers, visualizers, and extensions.

A great challenge in fulfilling FAIR guidelines is describing the provenance of data:

* R1.2. (Meta)data are associated with detailed provenance  
  [https://www.go-fair.org/fair-principles/r1-2-metadata-associated-detailed-provenance/](https://www.go-fair.org/fair-principles/r1-2-metadata-associated-detailed-provenance/)

If Jade-Tipi is adopted to record how data is created, not just what data has been created, it can straightforwardly accomplish the objective of sharing data provenance.  This would involve adopting Jade-Tipi as a LIMS (Laboratory Information Management System), to record step-by-step how data is produced.  See the last section of this document for more information.

# **Metadata Overview**

Metadata falls into three broad categories:

1. entity  
2. property  
3. link

Entities are things, real or conceptual.  At the JGI, some examples are:

* organism  
* sequence file  
* assembly  
* annotation  
* collaborator

Properties are used to characterize entities.  An entity is a collection of properties.  The properties ascribed to an entity may grow in response to analyses that define additional attributes.  Some examples of properties:

* volume  
* Ensembl ID  
* gene function

Links are relationships between two entities.   They consist of a named pointer for each entity and a label.   Links may have additional properties, such as a “weight” property.

Entities, properties, and links comprise the core metadata of the institution.  Metadata is stored and curated in dedicated repositories.   One or more data lakes can be built from the metadata collection.  As metadata evolves, data streams push changes into established data lakes.

# **Jade-Tipi Specification**

Following is a brief introduction to the Jade-Tipi specification.  Many technical details are glossed over.  In addition to entities, properties, and links, Jade-Tipi defines additional classes: “group”, “type”, “validation” and “transaction”.  The basic set of Jade-Tipi classes is:

* entity       (ent)  
* property     (ppy)  
* link         (lnk)  
* group        (grp)  
* type         (typ)  
* validation   (val)  
* transaction  (txn)

All objects in a Jade-Tipi belong to a **group**.  Groups are used to define ownership of metadata and to assign read and write permissions on objects owned by the group.  Members of one group may add properties to objects owned by another group.  For example, a QC group may analyze a DNA assembly owned by the assembly group and assign a quality score.  Properties are owned by the group that added them.

A client accessing a Jade-Tipi will belong to one or more groups that define read and write access to objects in that repository.  Because properties are separate from the entities to which they apply, read and write permissions are granular on the level of properties.

Groups are managed at an institutional level and play a key role in ensuring that IDs are world-unique. 

A **type** is defined as a set of properties. Properties may be added to a type after its creation.  A type can include one or more **validations** used to determine whether instances of the class meet the requirements defined by those validations. Any validation mechanism may be used; schemas such as JSON Schema, Avro, and Protocol Buffers can be employed to characterize property values.  Schemas provided by [FAIR data organizations](#related-fair-data-resources) can be used to associate conforming properties with entities.  A type may subclass one or more other types. A subclass inherits the property sets and validations of its supertypes and may add new properties and validations.

Some examples of types:

* 96-well plate (entity type)  
* annotation    (entity type)  
* volume        (property type)  
* begat         (link type)  
* Avro schema   (validation type)

Metadata objects are added and modified in a Jade-Tipi via **transactions**.  Transactions are assigned a sequential ID when initiated.  Small messages referencing the ID are submitted to create or modify objects in the Jade-Tipi.  For instance, a transaction is initiated and receives ID 123\.  The Jade-Tipi client issues messages to create a “96-well plate” type, create a plate, and assign it a barcode:

* txn 123, uuid a, hash xx: create entity type “96-well plate” with ID “123\~ty\~a”  
* txn 123, uuid b, hash xx: create property type “barcode” with ID “123\~ty\~b”  
* txn 123, uuid c, hash xx: assign property type “123\~ty\~b” to entity type “123\~ty\~a”  
* txn 123, uuid d, hash xx: create entity of type “123\~ty\~a” with ID “123\~en\~c”  
* txn 123, uuid e, hash xx: set property “123\~ty\~b” to value “barcode-1” on entity “123\~en\~c”  
* txn 123, msg f, hash xx: close

A transaction ID is issued with a secret key.  Clients assign a UUID to each message and include a hash of the UUID created with the secret key.  Every message is uniquely identified by the combination of transaction ID and UUID.  After receiving a transaction ID, clients generate the IDs of objects they create using naming conventions.

A Jade-Tipi service reads the submission stream, verifies that messages are authentic using the secret key, and records the specified changes in the central repository.  After receiving a “close” and successfully processing all preceding messages, the service echoes the objects created or modified in the transaction to the “transaction” stream with an additional “commit” message with a commit ID.  Only clients participating in the transaction can access modified data before a commit ID has been assigned.

The transaction stream is the definitive record, the “source of truth” of the system state.  The history of the system is preserved in the transaction stream. Transaction stream messages can be compressed, saved in the filesystem, and written to tape.  The system can be recovered at any point in time from the transaction record.  Clients (e.g. Flink clients pushing changes to a data lake) may listen to the transaction stream without interacting with the Jade-Tipi repository.

The commit ID is just another new ID issued by the transaction ID generator for the system.  These IDs are sequential and the order of the commits performed on the system is preserved in the ASCII sort order of the commit IDs.

All Jade-Tipi objects have **world-**unique IDs so the metadata from one repository can be loaded into another without conflict.  The transaction stream of one Jade-Tipi can be fed into the submission stream of another to replicate the data in the first repository in the second.  Disparate Jade-Tipi repositories can be combined into an aggregating repository.

Rules for constructing IDs, which are text strings, ensure that each ID is world-unique.  The naming rules are designed to ensure that the IDs can be used directly as keys in popular databases, including Couchbase, MongoDB, Redis, PostgreSQL, and Oracle.  The naming scheme is simple.  Every ID begins with the institution owning the data, e.g., “lbl\_gov”.  (LBL, as the mother of Jade-Tipi, is responsible for issuing unique institution names to other institutions that set up Jade-Tipi repos.)  Only lowercase ASCII letters “a-z”, digits “0-9”, and underscores “\_” are allowed in ID segments. Leading, trailing, and consecutive underscores are not allowed.  A tilda “\~” separates segments.

* transaction ID  
  * institution         (lbl\_gov)  
  * group               (jgi\_pps)  
  * timestamp           (epoch milliseconds, numeric)  
  * increment            
* type                      (en,pp,ln,ty,va,gp,tx)  
* suffix  
* property assignment ID    (used only for setting property values)

A transaction ID server for each Jade-Tipi instance issues transaction IDs and secret keys.  Transaction IDs are issued one at a time, so care must be taken to minimize contention for these IDs.  Transaction IDs for the same timestamp must be issued with “increment” segments in ASCII sort order.  Clients submitting transactions compose the suffix of new class instances they create and must ensure that all suffixes for a given transaction ID are unique.  Additional rules apply to the IDs representing property assignment (that are not explained here).

An example of valid Jade-Tipi IDs:

lbl\_gov\~jgi\_pps\~1747977406\~azba\~tx\~zza  
lbl\_gov\~jgi\_pps\~1747977406\~azba\~en\~aab  
lbl\_gov\~jgi\_pps\~1747977406\~azba\~pp\~cik  
embl\_org\~ebit\_annot\~1748022243\~azba\~pp\~hgs  
lbl\_gov\~jgi\_pps\~1747977406\~azba\~en\~aab\~lbl\_gov\~jgi\_pps\~1747977406\~azba\~pp\~cik  
lbl\_gov\~jgi\_pps\~1747977406\~azba\~en\~aab\~embl\_org\~ebit\_annot\~1748022243\~azba\~pp\~hgs  
|- \- \- \- transaction ID \- \- \- |

Any ID with more than 6 segments, i.e., that includes a property assignment ID, represents the assignment of a property value to the object identified by the preceding 6 segments.  The property assignment ID is simply the ID of the property that is being assigned to the entity.

Suppose that entity “lbl\_gov\~jgi\_pps\~1747977406\~azba\~en\~aab” represents a DNA sample.  The two following IDs represent properties assigned to the sample.

1. lbl\_gov\~jgi\_pps\~1747977406\~azba\~en\~aab\~lbl\_gov\~jgi\_pps\~1747977406\~azba\~pp\~cik  
2. lbl\_gov\~jgi\_pps\~1747977406\~azba\~en\~aab\~embl\_org\~ebit\_annot\~1748022243\~azba\~pp\~hgs

The first ID represents the assignment of property “lbl\_gov\~jgi\_pps\~1747977406\~azba\~pp\~cik” to the sample.   (Perhaps the volume has been set to 10µL.)  The second ID represents the assignment of a property created by the Annotation Team at the EBI Institute of the EMBL Organization.  (The Annotation Team is designating a “risk group” property on the sample with a value of “4”.)  The JGI is collaborating with the EBI and has given them write access to the jgi\_pps Jade-Tipi.

# **Reference Implementation**

The specification is technology agnostic.  The storage and retrieval of JSON objects in a Jade-Tipi may use products from disparate vendors.  A reference implementation is envisioned with the following components.

* Central metadata repository.  FoundationDB is the top contender.  Aerospike should also be considered.  Both can handle trillions of objects.  FoundationDB can support natively finding all links for an object and is transactional.  Maintained by Apple, it powers Apple’s iCloud and Snowflake.  
* Transaction ID generator.  This web service must be able to deliver sequential IDs for a given millisecond with extremely low latency and congestion.  (Spring Boot)  
* Kafka server to implement the submission and transaction streams as Kafka topics.  
* Kafka Streams application (Spring Boot) with these responsibilities:  
  * read the submission stream  
  * validate messages  
  * create or update Jade-Tipi objects in the repo  
  * echo messages with a commit ID to the transaction stream.  
* Flink connectors listen to the transaction stream to push changes to the data lake.  
* Web service API for metadata retrieval from the repository (Spring Boot).  
* Possible extensions to the basic API  
  * graph queries with JanusGraph backed by ScyllaDB  
  * text searches, aggregations, and geospatial queries with Elasticsearch  
  * data submission endpoints to streamline creating messages in the submission stream

# 

**Architectural Diagram (next page)**

# **Jade-Tipi Architectural Diagram** {#jade-tipi-architectural-diagram}

In this diagram, the Sequence Data Management team has written a custom connector using Flink to stream changes from their MongoDB metadata repository into the JGI Jade-Tipi submission stream. The LIMS team has set up their own Jade-Tipi (not shown).  The transaction stream of the LIMS Jade-Tipi is connected to the submission stream of the central JGI Jade-Tipi.

In response to demands for complex ad-hoc queries, the basic API may be extended with the support of JanusGraph (for graphical queries) and Elasticsearch (for full-text search, aggregations, and geospatial queries).

It is best practice to have one Jade-Tipi Kafka submission stream per application or group: different services should not write to the same submission stream.  The Jade-Tip transaction processor can be configured to read from multiple input streams.

# **Jade-Tipi LIMS – Recording Data Provenance**

Jade-Tipi can be straightforwardly extended to serve as a LIMS (Laboratory Information Management System) to record and share the details about how data was generated.  A few conventions can be followed to achieve this.

* Use a “Procedure” entity type in conjunction with all metadata changes.  
  * Recording the inputs to a Procedure using links to input entities.  
  * Use links to connect a Procedure to the entities it creates or modifies.  
  * Establish links between the outputs and the inputs from which they were created.  
    * Link properties can provide more detail.  For example, a pool object might have a link back to each constituent with the property “volume contributed”.  
    * A list of “input-output mapping” properties can be added to the Procedure entity to characterize how each output relates to the inputs.  
* Develop a set of detailed Procedure types to describe the different types of work performed at the laboratory.  
* Use “Task” entities as inputs to Procedure instances.  Develop a vocabulary of task types corresponding to the set of procedure types.  
  * Assign entities (via links) to a task of a specific type to express the intention to perform a certain procedure.  
  * Beginning and completing the procedure to which the task is an input marks the start and completion of the task. Gantt charts can be generated from task creation and completion dates.  
  * Tasks not yet assigned to a Procedure instance constitute queues of pending work.  Tasks can be assigned ranks to prioritize work or be linked to scheduling window entities to express the plan to perform work within specified periods.

Adopting these conventions would allow laboratories to share how data was produced including all the individual steps involved.  Recording when procedures were performed with what resources will provide insight into productivity and the capacity of the laboratory to complete planned work.

When Jade-Tipi is extended by adopting these conventions related to recording data provenance, Jade-Tipi becomes the “JSON Data Transparency Process Tracing Protocol” (JDTPTP) or Jade-Tipis.  
---

Author: Duncan Scott   
JGI Production Informatics  
[dscott@lbl.gov](mailto:dscott@lbl.gov)  
May 25, 2025

# **Addendum**

## **Selection of Document Store**

Aerospike and FoundationDB are ideally suited for storing trillions of small objects retrievable by key.  Aerospike cannot straightforwardly support finding all links to an object and would need to be supplemented with a secondary indexing system.  FoundationDB does support finding all links to an object.  FoundationDB is transactional.

| Database | Official Flink Connector | Format | Weakness | Strength | Webpage |
| :---- | :---- | :---- | :---- | :---- | :---- |
| Aerospike |  | complicated, retrieval by key | secondary index system is required to support finding links to an object.All keys must be held in memory. | extremely high performance, official support for Kafka, Spark, Presto/Trino; Jupyter Integration | [https://aerospike.com/](https://aerospike.com/) |
| Couchbase | yes | JSON | keys stored in RAM |  |  |
| FoundationDB |  | key / value | 100 Kb limit on values (strength?) | transactional, indexable, layers support: SQL, Graph, MongoDB protocols | [https://www.foundationdb.org/](https://www.foundationdb.org/) |
| MongoDB | no | JSON | scaling more complicated | Suited for small deployments, e.g. a LIMS |  |
| ScyllaDB | yes (based on Cassandra) | Big Table | Big table | a better version of Cassandra | [https://www.scylladb.com/](https://www.scylladb.com/) |

Note: Aerospike may be useful as a real-time mirror of FoundationDB for analytics via Spark, Presto, or Jupyter.

## **Avro and Iceberg Integration**

JSON objects, validated against Avro schemas, can be stored as properties in Jade-Tipi, with each property type referencing its Avro schema for validation. FoundationDB’s 100 KB value limit is sufficient for complex property objects and encourages fine-grained records, which aligns well with Iceberg’s architecture. These Avro property values and their associated schemas can be loaded directly into Iceberg tables, which natively support Avro schemas.

## **On-Prem Lakehouse Stack**

A modern on-premise lakehouse architecture can be built using fully open-source, cloud-native technologies for scalable analytics, data sharing, and FAIR compliance.  The standard way:

* Storage Layer: MinIO (S3-compatible object storage)  
* Lakehouse Table Format: Apache Iceberg (preferred for flexibility and ecosystem support)  
* Processing / ETL Engines:  
  * Apache Spark (batch/streaming/ML)  
  * Apache Flink (real-time streaming)  
* SQL Query & BI Engine: Trino (alternatives: Dremio, Hive)  
* Catalog & Metadata: Project Nessie  
* Orchestration: Apache Airflow

Strong consideration should be given to the newly released DuckLake platform.

[https://ducklake.select/](https://ducklake.select/)

##  **Related FAIR Data Resources** {#related-fair-data-resources}

* DATS  
  [https://faircookbook.elixir-europe.org/content/recipes/infrastructure/dats-model.html](https://faircookbook.elixir-europe.org/content/recipes/infrastructure/dats-model.html)  
  [https://fairsharing.org/10.25504/FAIRsharing.e20vsd](https://fairsharing.org/10.25504/FAIRsharing.e20vsd)  
* ISA Model  
  [https://www.isacommons.org/](https://www.isacommons.org/)  
  [https://isa-specs.readthedocs.io/en/latest/index.html](https://isa-specs.readthedocs.io/en/latest/index.html)  
* KBase Central Data Model (CDM)  
  [https://kbase.github.io/cdm-schema/](https://kbase.github.io/cdm-schema/)  
  [https://github.com/kbase/cdm-schema](https://github.com/kbase/cdm-schema)  
* [Schema.org](https://schema.org/) (Google, Microsoft, Yahoo\!, Yandex)

## 

## **Extending Jade-Tipi with LinkML and External Schemas** {#extending-jade-tipi-with-linkml-and-external-schemas}

Jade-Tipi is designed to be maximally flexible and agnostic regarding metadata schemas. However, interoperability, validation, and data harmonization are greatly strengthened by adopting community standards such as LinkML, JSON Schema, Protocol Buffers, Avro, and others.

### **Schema Integration in Jade-Tipi**

In Jade-Tipi, entities are conceived as buckets of properties rather than rigid objects defined by a schema. Entities of a particular type may, however, have a required property that references a schema as a “validation” for that type. For instance, the type definition for “genome\_assembly” entities may specify a required property “linkml\_definition” which references a LinkML schema used for validation. All “genome\_assembly” entities would then be guaranteed by the Jade-Tipi protocol to have a representation conforming to a particular LinkML schema.

In practice, it may be advisable to define the schema-constrained type as a subtype of “genome\_assembly (for example, “genome\_assembly\_linkml”) to allow for the coexistence of both schema-constrained and unconstrained entities within the broader type.

Note that while all objects in Jade-Tipi are represented as JSON, the value of a property could itself be a YAML document encoded as a JSON string, allowing the use of YAML to encode objects within the JSON capsule.

### **Working with the FoundationDB 100KB Size Limit**

Established schemas may result in representations of entities that exceed the 100KB size limit of FoundationDB. There are two principal approaches to resolve this:

1. **Encourage smaller, modular schemas:** Users should be encouraged to design schemas and entity representations that fit safely within the FoundationDB size limit.  
2. **Extend FoundationDB with a chunking layer:** Implement or use an FDB layer to transparently handle splitting (“chunking”) and reassembling large logical objects across multiple key/value pairs. This approach aligns with the FoundationDB philosophy and is a standard method for supporting higher-level abstractions.  This is trivial to implement using official FoundationDB bindings for Java and other languages.

### **Working with LinkML References**

Using Jade-Tipi world-unique IDs as the values for LinkML reference slots ensures that all references are resolvable within the Jade-Tipi repository. LinkML expects references to be IDs, not full objects, so using Jade-Tipi IDs fits perfectly with this pattern. By adopting this convention, any referenced ID can be directly retrieved from the Jade-Tipi store. The LinkML schema can validate that the “organism” slot contains a string and, if extended, that it matches a Jade-Tipi ID pattern or regex.

Jade-Tipi IDs are world-unique by design and are ideal values for LinkML identifier slots. LinkML does not itself enforce the core FAIR principle that all entities should have world-unique, persistent identifiers. When you use LinkML with Jade-Tipi, you implement the unique ID principle that LinkML encourages.

