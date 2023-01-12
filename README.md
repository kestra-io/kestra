<p align="center">
  <a href="https://www.kestra.io">
    <img width="460" src="https://kestra.io/logo.svg"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Infinitely scalable open source orchestration & scheduling platform. <br>
</h1>

<div align="center">
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?style=flat-square" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/pulse"><img src="https://img.shields.io/github/commit-activity/m/kestra-io/kestra?style=flat-square" alt="Commits-per-month"></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra.svg?style=flat-square" alt="Github star" /></a>
  <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?style=flat-square" alt="Last Version" /></a>
  <a href="https://hub.docker.com/r/kestra/kestra"><img src="https://img.shields.io/docker/pulls/kestra/kestra.svg?style=flat-square" alt="Docker pull" /></a>
  <a href="https://artifacthub.io/packages/helm/kestra/kestra"><img src="https://img.shields.io/badge/Artifact%20Hub-kestra-417598?style=flat-square&logo=artifacthub" alt="Artifact Hub" /></a>
  <a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?style=flat-square" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
  <a href="https://api.kestra.io/v1/communities/slack/redirect"><img src="https://img.shields.io/badge/Slack-chat-400d40?style=flat-square&logo=slack" alt="Slack"></a>
  <a href="https://github.com/kestra-io/kestra/discussions"><img src="https://img.shields.io/github/discussions/kestra-io/kestra?style=flat-square" alt="Github discussions"></a>
  <a href="https://twitter.com/kestra_io"><img src="https://img.shields.io/twitter/follow/kestra_io?style=flat-square" alt="Twitter" /></a>
  <a href="https://app.codecov.io/gh/kestra-io/kestra"><img src="https://img.shields.io/codecov/c/github/kestra-io/kestra?style=flat-square&token=It6L7BTaWK" alt="Code Cov" /></a>
  <a href="https://github.com/kestra-io/kestra/actions"><img src="https://img.shields.io/github/workflow/status/kestra-io/kestra/Main/develop?style=flat-square" alt="Github Actions" /></a>
</div>

<br />

<p align="center">
    <a href="https://kestra.io/"><b>Website</b></a> •
    <a href="https://twitter.com/kestra_io"><b>Twitter</b></a> •
    <a href="https://www.linkedin.com/company/kestra/"><b>Linked In</b></a> •
    <a href="https://api.kestra.io/v1/communities/slack/redirect"><b>Slack</b></a> •
    <a href="https://kestra.io/docs/"><b>Documentation</b></a>
</p>

<br />

<p align="center"><img src="https://kestra.io/video.gif" alt="modern data orchestration and scheduling platform " width="640px" /></p>


## Demo

Play with our [demo app](https://demo.kestra.io)!

## What is Kestra ?
Kestra is an infinitely scalable orchestration and scheduling platform, creating, running, scheduling, and monitoring millions of complex pipelines.

- 🔀 **Any kind of workflow**: Workflows can start simple and progress to more complex systems with branching, parallel, dynamic tasks, flow dependencies
- 🎓‍ **Easy to learn**: Flows are in simple, descriptive language defined in YAML—you don't need to be a developer to create a new flow.
- 🔣 **Easy to extend**: Plugins are everywhere in Kestra, many are available from the Kestra core team, but you can create one easily.
- 🆙 **Any triggers**: Kestra is event-based at heart—you can trigger an execution from API, schedule, detection, events
- 💻 **A rich user interface**: The built-in web interface allows you to create, run, and monitor all your flows—no need to deploy your flows, just edit them.
- ⏩ **Enjoy infinite scalability**: Kestra is built around top cloud native technologies—scale to millions of executions stress-free.

**Example flow:**

```yaml
id: my-first-flow
namespace: my.company.teams

inputs:
  - type: FILE
    name: uploaded
    description: A Csv file to be uploaded through API or UI

tasks:
  - id: archive
    type: io.kestra.plugin.gcp.gcs.Upload
    description: Archive the file on Google Cloud Storage bucket
    from: "{{ inputs.uploaded }}"
    to: "gs://my_bucket/archives/{{ execution.id }}.csv"

  - id: csvReader
    type: io.kestra.plugin.serdes.csv.CsvReader
    from: "{{ inputs.uploaded }}"

  - id: fileTransform
    type: io.kestra.plugin.scripts.nashorn.FileTransform
    description: This task will anonymize the contactName with a custom nashorn script (javascript over jvm). This show that you able to handle custom transformation or remapping in the ETL way
    from: "{{ outputs.csvReader.uri }}"
    script: |
      if (row['contactName']) {
        row['contactName'] = "*".repeat(row['contactName'].length);
      }

  - id: avroWriter
    type: io.kestra.plugin.serdes.avro.AvroWriter
    description: This file will convert the file from Kestra internal storage to avro. Again, we handling ETL since the conversion is done by Kestra before loading the data in BigQuery. This allow you to have some control before loading and to reject wrong data as soon as possible.
    from: "{{ outputs.fileTransform.uri }}"
    schema: |
      {
        "type": "record",
        "name": "Root",
        "fields":
          [
            { "name": "contactTitle", "type": ["null", "string"] },
            { "name": "postalCode", "type": ["null", "long"] },
            { "name": "entityId", "type": ["null", "long"] },
            { "name": "country", "type": ["null", "string"] },
            { "name": "region", "type": ["null", "string"] },
            { "name": "address", "type": ["null", "string"] },
            { "name": "fax", "type": ["null", "string"] },
            { "name": "email", "type": ["null", "string"] },
            { "name": "mobile", "type": ["null", "string"] },
            { "name": "companyName", "type": ["null", "string"] },
            { "name": "contactName", "type": ["null", "string"] },
            { "name": "phone", "type": ["null", "string"] },
            { "name": "city", "type": ["null", "string"] }
          ]
      }

  - id: load
    type: io.kestra.plugin.gcp.bigquery.Load
    description: Simply load the generated from avro task to BigQuery
    avroOptions:
      useAvroLogicalTypes: true
    destinationTable: kestra-prd.demo.customer_copy
    format: AVRO
    from: "{{outputs.avroWriter.uri }}"
    writeDisposition: WRITE_TRUNCATE

  - id: aggregate
    type: io.kestra.plugin.gcp.bigquery.Query
    description: Aggregate some data from loaded files
    createDisposition: CREATE_IF_NEEDED
    destinationTable: kestra-prd.demo.agg
    sql: |
      SELECT k.categoryName, p.productName, c.companyName, s.orderDate, SUM(d.quantity) AS quantity, SUM(d.unitPrice * d.quantity * r.exchange) as totalEur
      FROM `kestra-prd.demo.salesOrder` AS s
      INNER JOIN `kestra-prd.demo.orderDetail` AS d ON s.entityId = d.orderId
      INNER JOIN `kestra-prd.demo.customer` AS c ON c.entityId = s.customerId
      INNER JOIN `kestra-prd.demo.product` AS p ON p.entityId = d.productId
      INNER JOIN `kestra-prd.demo.category` AS k ON k.entityId = p.categoryId
      INNER JOIN `kestra-prd.demo.rates` AS r ON r.date = DATE(s.orderDate) AND r.currency = "USD"
      GROUP BY 1, 2, 3, 4
    timePartitioningField: orderDate
    writeDisposition: WRITE_TRUNCATE
```

## Getting Started

To get a local copy up and running, please follow these steps.

### Prerequisites

Make sure you have already installed:
- [Docker](https://docs.docker.com/engine/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)

### Launch Kestra
- Download the compose file [here](https://github.com/kestra-io/kestra/blob/develop/docker-compose.yml) and save it with the name `docker-compose.yml`, for linux and macos, you can run `wget https://raw.githubusercontent.com/kestra-io/kestra/develop/docker-compose.yml`
- Run `docker-compose up -d`
- Open `http://localhost:8080` on your browser
- Follow [this tutorial](https://kestra.io/docs/getting-started/) to create your first flow.
- Read the [documentation](https://kestra.io/docs/) to understand how to
  - [Develop your flows](https://kestra.io/docs/developer-guide/)
  - [Deploy Kestra](https://kestra.io/docs/administrator-guide/)
  - Use our [Terraform provider](https://kestra.io/docs/terraform/)
  - Develop your [own plugins](https://kestra.io/docs/plugin-developer-guide/)

## Plugins
Kestra is built on a [plugin system](https://kestra.io/plugins/). You can find your plugin to interact with your provider; alternatively, you can follow [these steps](https://kestra.io/docs/plugin-developer-guide/) to develop your own plugin. Here are the official plugins that are available:

<table>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-airbyte">Airbyte</a></td>
        <td><a href="https://kestra.io/plugins/plugin-aws#s3">Amazon S3</a></td>
        <td><a href="https://kestra.io/plugins/plugin-serdes#avro">Avro</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-azure/#storage-blob">Azure Blob Storage</a></td>
        <td><a href="https://kestra.io/plugins/core/tasks/scripts/io.kestra.core.tasks.scripts.Bash">Bash</a></td>
        <td><a href="https://kestra.io/plugins/plugin-gcp#bigquery">Big Query</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-serdes#csv">CSV</a></td>
        <td><a href="https://kestra.io/plugins/plugin-cassandra">Cassandra</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-clickhouse">ClickHouse</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-dbt">DBT</a></td>
        <td><a href="https://kestra.io/plugins/plugin-debezium-mysql">Debezium MYSQL</a></td>
        <td><a href="https://kestra.io/plugins/plugin-debezium-postgres">Debezium Postgres</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-debezium-sqlserver">Debezium Microsoft SQL Server</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-duckdb">DuckDb</a></td>
        <td><a href="https://kestra.io/plugins/plugin-elasticsearch">ElasticSearch</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-fivetran">Fivetran</a></td>
        <td><a href="https://kestra.io/plugins/plugin-notifications#mail">Email</a></td>
        <td><a href="https://kestra.io/plugins/plugin-fs#ftp">FTP</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-fs#ftps">FTPS</a></td>
        <td><a href="https://kestra.io/plugins/plugin-gcp#gcs">Google Cloud Storage</a></td>
        <td><a href="https://kestra.io/plugins/plugin-googleworkspace#drive">Google Drive</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-googleworkspace#sheets">Google Sheets</a></td>
        <td><a href="https://kestra.io/plugins/plugin-scripts-groovy">Groovy</a></td>
        <td><a href="https://kestra.io/plugins/plugin-fs#http">Http</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-serdes#json">JSON</a></td>
        <td><a href="https://kestra.io/plugins/plugin-scripts-jython">Jython</a></td>
        <td><a href="https://kestra.io/plugins/plugin-kafka">Kafka</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-kubernetes">Kubernetes</a></td>
        <td><a href="https://kestra.io/plugins/plugin-mqtt">MQTT</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-sqlserver">Microsoft SQL Server</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-mongodb">MongoDb</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-mysql">MySQL</a></td>
        <td><a href="https://kestra.io/plugins/plugin-scripts-nashorn">Nashorn</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/core/tasks/scripts/io.kestra.core.tasks.scripts.Node">Node</a></td>
        <td><a href="https://kestra.io/plugins/plugin-crypto#openpgp">Open PGP</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-oracle">Oracle</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-serdes#parquet">Parquet</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-pinot">Apache Pinot</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-postgres">Postgres</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-powerbi">Power BI</a></td>
        <td><a href="https://kestra.io/plugins/plugin-pulsar">Apache Pulsar</a></td>
        <td><a href="https://kestra.io/plugins/core/tasks/scripts/io.kestra.core.tasks.scripts.Python">Python</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-redshift">Redshift</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-rockset">Rockset</a></td>
        <td><a href="https://kestra.io/plugins/plugin-fs#sftp">SFTP</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-servicenow">ServiceNow</a></td>
        <td><a href="https://kestra.io/plugins/plugin-singer">Singer</a></td>
        <td><a href="https://kestra.io/plugins/plugin-notifications#slack">Slack</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-snowflake">Snowflake</a></td>
        <td><a href="https://kestra.io/plugins/plugin-soda">Soda</a></td>
        <td><a href="https://kestra.io/plugins/plugin-spark">Spark</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-tika">Tika</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-trino">Trino</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-vectorwise">Vectorwise</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-serdes#xml">XML</a></td>
        <td><a href="https://kestra.io/plugins/plugin-gcp#vertexai/">Vertex AI</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-vertica">Vertica</a></td>
    </tr>
</table>



This list is growing quickly as we are actively building more plugins, and we welcome contributions!

## Community Support

Join our community if you need help, want to chat, or have any other questions for us:

- [GitHub](https://github.com/kestra-io/kestra/discussions) - Discussion forums and updates from the Kestra team
- [Twitter](https://twitter.com/kestra_io) - For all the latest Kestra news
- [Slack](https://api.kestra.io/v1/communities/slack/redirect) - Join the conversation! Get all the latest updates and chat with the devs


## Roadmap

See the [open issues](https://github.com/kestra-io/kestra/issues) for a list of proposed features (and known issues) or look at the [project board](https://github.com/orgs/kestra-io/projects/2).

## Developing locally & Contributing

We love contributions big or small, check out [our guide](https://github.com/kestra-io/kestra/blob/develop/.github/CONTRIBUTING.md) on how to get started.

See our [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/) for developing Kestra plugins.


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)
