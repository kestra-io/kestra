<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<div align="center">
 <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?color=blueviolet" alt="Last Version" /></a>
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?color=blueviolet" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra?color=blueviolet&logo=github" alt="Github star" /></a> <br>
<a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?color=blueviolet" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
<a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-Join%20Community-blueviolet?logo=slack" alt="Slack"></a>
</div>

<br />

<p align="center">
    <a href="https://twitter.com/kestra_io"><img height="25" src="https://kestra.io/twitter.svg" alt="twitter" /></a> &nbsp;
    <a href="https://www.linkedin.com/company/kestra/"><img height="25" src="https://kestra.io/linkedin.svg" alt="linkedin" /></a> &nbsp;
<a href="https://www.youtube.com/@kestra-io"><img height="25" src="https://kestra.io/youtube.svg" alt="youtube" /></a> &nbsp;
</p>

<br />
<p align="center">
    <a href="https://www.youtube.com/watch?v=h-P0eK2xN58&ab_channel=Kestra" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 4 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>"Click on the image to get started in 4 minutes with Kestra."</i></p>

## Live Demo

Try Kestra using our [live demo](https://demo.kestra.io/ui/login?auto).

## What is Kestra

Kestra is a universal open-source orchestrator that makes both **scheduled** and **event-driven** workflows easy. By bringing **Infrastructure as Code** best practices to data, process, and microservice orchestration, you can build reliable workflows and manage them with confidence.

In just a few lines of code, you can [create a flow](https://kestra.io/docs/getting-started) directly from the UI. Thanks to the declarative YAML interface for defining orchestration logic, business stakeholders can participate in the workflow creation process.

Kestra offers a versatile set of **language-agnostic** developer tools while simultaneously providing an intuitive user interface tailored for business professionals. The YAML definition gets automatically adjusted any time you make changes to a workflow from the UI or via an API call. Therefore, the orchestration logic is always managed **declaratively in code**, even if some workflow components are modified in other ways (UI, CI/CD, Terraform, API calls).


![Adding new tasks in the UI](https://kestra.io/adding-tasks.gif)


## Key concepts

1. `Flow` is the main component in Kestra. It's a container for your tasks and orchestration logic.
2. `Namespace` is used to provide logical isolation, e.g., to separate development and production environments. Namespaces are like folders on your file system — they organize flows into logical categories and can be nested to provide a hierarchical structure.
3. `Tasks` are atomic actions in a flow. By default, all tasks in the list will be executed sequentially, with additional customization options, a.o. to run tasks in parallel or allow a failure of specific tasks when needed.
4. `Triggers` define when a flow should run. In Kestra, flows are triggered based on events. Examples of such events include:
    - a regular time-based **schedule**
    - an **API** call (*webhook trigger*)
    - ad-hoc execution from the **UI**
    - a **flow trigger** - flows can be triggered from other flows using a [flow trigger](https://kestra.io/docs/developer-guide/triggers/flow) or a [subflow](https://kestra.io/docs/flow-examples/subflow), enabling highly modular workflows.
    - **custom events**, including a new file arrival (*file detection event*), a new message in a message bus, query completion, and more.
5. `Inputs` allow you to pass runtime-specific variables to a flow. They are strongly typed, and allow additional [validation rules](https://kestra.io/docs/developer-guide/inputs#input-validation).


## Extensible platform via plugins

Most tasks in Kestra are available as [plugins](https://kestra.io/plugins), but many type of tasks are available in the core library, including a.o. script tasks supporting various programming languages (e.g., Python, Node, Bash) and the ability to orchestrate your business logic packaged into Docker container images.

To create your own plugins, check the [plugin developer guide](https://kestra.io/docs/plugin-developer-guide).

## Rich orchestration capabilities

Kestra provides a variety of tasks to handle both simple and complex business logic, including:

- subflows
- retries
- timeout
- error handling
- conditional branching
- dynamic tasks
- sequential and parallel tasks
- skipping tasks or triggers when needed by setting the flag `disabled` to `true`.
- configuring dependencies between tasks, flows and triggers
- advanced scheduling and trigger conditions
- backfills
- blueprints
- documenting your flows, tasks and triggers by adding a markdown description to any component
- adding labels to add additional metadata to your flows such as the flow owner or team:

```yaml
id: getting_started
namespace: dev

description: |
  # Getting Started
  Let's `write` some **markdown** - [first flow](https://t.ly/Vemr0) 🚀

labels:
  owner: rick.astley
  project: never-gonna-give-you-up

tasks:
  - id: hello
    type: io.kestra.core.tasks.log.Log
    message: Hello world!
    description: a *very* important task
    disabled: false
    timeout: PT10M
    retry:
      type: constant # type: string
      interval: PT15M # type: Duration
      maxDuration: PT1H # type: Duration
      maxAttempt: 5 # type: int
      warningOnRetry: true # type: boolean, default is false

- id: parallel
    type: io.kestra.core.tasks.flows.Parallel
    concurrent: 3
    tasks:
      - id: task1
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - 'echo "running {{task.id}}"'
          - 'sleep 2'
      - id: task2
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - 'echo "running {{task.id}}"'
          - 'sleep 1'
      - id: task3
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - 'echo "running {{task.id}}"'
          - 'sleep 3'

triggers:
  - id: schedule
    type: io.kestra.core.models.triggers.types.Schedule
    cron: "*/15 * * * *"
    backfill:
      start: 2023-10-05T14:00:00Z
```


## Built-in code editor

You can write workflows directly from the UI. When writing your workflows, the UI provides:
- autocompletion
- syntax validation
- embedded plugin documentation
- example flows provided as blueprints
- topology view (view of your dependencies in a Directed Acyclic Graph) that get updated live as you modify and add new tasks.


## Stay up to date

We release new versions every month. Give the repository a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)


## Getting Started

Follow the steps below to start local development.

### Prerequisites

Make sure that Docker is installed and running on your system. The default installation requires the following:
- [Docker](https://docs.docker.com/engine/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)


### Launch Kestra

Download the Docker Compose file:

```sh
curl -o docker-compose.yml https://raw.githubusercontent.com/kestra-io/kestra/develop/docker-compose.yml
```

Alternatively, you can use `wget https://raw.githubusercontent.com/kestra-io/kestra/develop/docker-compose.yml`.


Start Kestra:

```sh
docker compose up
```


Open `http://localhost:8080` in your browser and create your first flow.


### Hello-World flow

Here is a simple example logging hello world message to the terminal:

```yaml
id: getting_started
namespace: dev
tasks:
  - id: hello_world
    type: io.kestra.core.tasks.log.Log
    message: Hello World!
```

For more information:

- Follow the [getting started tutorial](https://kestra.io/docs/getting-started/).
- Read the [documentation](https://kestra.io/docs/) to understand how to:
    - [Develop your flows](https://kestra.io/docs/developer-guide/)
    - [Deploy Kestra](https://kestra.io/docs/administrator-guide/)
    - Use our [Terraform provider](https://kestra.io/docs/terraform/) to deploy your flows
    - Develop your [own plugins](https://kestra.io/docs/plugin-developer-guide/).




## Plugins
Kestra is built on a [plugin system](https://kestra.io/plugins/). You can find your plugin to interact with your provider; alternatively, you can follow [these steps](https://kestra.io/docs/plugin-developer-guide/) to develop your own plugin.


For a full list of plugins, check the [plugins page](https://kestra.io/plugins/).

Here are some examples of the available plugins:

<table>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-airbyte#cloudjobs">Airbyte Cloud</a></td>
        <td><a href="https://kestra.io/plugins/plugin-airbyte#connections">Airbyte OSS</a></td>
        <td><a href="https://kestra.io/plugins/plugin-aws#athena">Amazon Athena</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-aws#cli">Amazon CLI</a></td>
        <td><a href="https://kestra.io/plugins/plugin-aws#dynamodb">Amazon DynamoDb</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-redshift">Amazon Redshift</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-aws#s3">Amazon S3</a></td>
        <td><a href="https://kestra.io/plugins/plugin-aws#sns">Amazon SNS</a></td>
        <td><a href="https://kestra.io/plugins/plugin-aws#sqs">Amazon SQS</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-amqp">AMQP</a></td>
        <td><a href="https://kestra.io/plugins/plugin-serdes#avro">Apache Avro</a></td>
        <td><a href="https://kestra.io/plugins/plugin-cassandra">Apache Cassandra</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-kafka">Apache Kafka</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-pinot">Apache Pinot</a></td>
        <td><a href="https://kestra.io/plugins/plugin-serdes#parquet">Apache Parquet</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-pulsar">Apache Pulsar</a></td>
        <td><a href="https://kestra.io/plugins/plugin-spark">Apache Spark</a></td>
        <td><a href="https://kestra.io/plugins/plugin-tika">Apache Tika</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-azure/#batchjob">Azure Batch</a></td>
        <td><a href="https://kestra.io/plugins/plugin-azure/#storage-blob">Azure Blob Storage</a></td>
        <td><a href="https://kestra.io/plugins/plugin-azure/#storagetable">Azure Blob Table</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-serdes#csv">CSV</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-clickhouse">ClickHouse</a></td>
        <td><a href="https://kestra.io/plugins/plugin-compress">Compression</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-couchbase">Couchbase</a></td>
        <td><a href="https://kestra.io/plugins/plugin-databricks">Databricks</a></td>
        <td><a href="https://kestra.io/plugins/plugin-dbt#cloud">dbt cloud</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-dbt#cli">dbt core</a></td>
        <td><a href="https://kestra.io/plugins/plugin-debezium-sqlserver">Debezium Microsoft SQL Server</a></td>
        <td><a href="https://kestra.io/plugins/plugin-debezium-mysql">Debezium MYSQL</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-debezium-postgres">Debezium Postgres</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-duckdb">DuckDb</a></td>
        <td><a href="https://kestra.io/plugins/plugin-elasticsearch">ElasticSearch</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-notifications#mail">Email</a></td>
        <td><a href="https://kestra.io/plugins/plugin-fivetran">Fivetran</a></td>
        <td><a href="https://kestra.io/plugins/plugin-fs#ftp">FTP</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-fs#ftps">FTPS</a></td>
        <td><a href="https://kestra.io/plugins/plugin-git">Git</a></td>
        <td><a href="https://kestra.io/plugins/plugin-gcp#bigquery">Google Big Query</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-gcp#pubsub">Google Pub/Sub</a></td>
        <td><a href="https://kestra.io/plugins/plugin-gcp#gcs">Google Cloud Storage</a></td>
        <td><a href="https://kestra.io/plugins/plugin-gcp#dataproc">Google DataProc</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-gcp#firestore">Google Firestore</a></td>
        <td><a href="https://kestra.io/plugins/plugin-gcp#cli">Google Cli</a></td>
        <td><a href="https://kestra.io/plugins/plugin-gcp#vertexai/">Google Vertex AI</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-gcp#gke">Google Kubernetes Engines</a></td>
        <td><a href="https://kestra.io/plugins/plugin-googleworkspace#drive">Google Drive</a></td>
        <td><a href="https://kestra.io/plugins/plugin-googleworkspace#sheets">Google Sheets</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-script-groovy">Groovy</a></td>
        <td><a href="https://kestra.io/plugins/plugin-fs#http">Http</a></td>
        <td><a href="https://kestra.io/plugins/plugin-serdes#json">JSON</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-script-julia">Julia</a></td>
        <td><a href="https://kestra.io/plugins/plugin-script-jython">Jython</a></td>
        <td><a href="https://kestra.io/plugins/plugin-kubernetes">Kubernetes</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-sqlserver">Microsoft SQL Server</a></td>
        <td><a href="https://kestra.io/plugins/plugin-notifications#teams">Microsoft Teams</a></td>
        <td><a href="https://kestra.io/plugins/plugin-mongodb">MongoDb</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-mqtt">MQTT</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-mysql">MySQL</a></td>
        <td><a href="https://kestra.io/plugins/plugin-script-nashorn">Nashorn</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-nats">NATS</a></td>
        <td><a href="https://kestra.io/plugins/plugin-neo4j">Neo4j</a></td>
        <td><a href="https://kestra.io/plugins/plugin-script-node">Node</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-openai">OpenAI</a></td>
        <td><a href="https://kestra.io/plugins/plugin-crypto#openpgp">Open PGP</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-oracle">Oracle</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-postgres">PostgreSQL</a></td>
        <td><a href="https://kestra.io/plugins/plugin-powerbi">Power BI</a></td>
        <td><a href="https://kestra.io/plugins/plugin-script-powershell">PowerShell</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-script-python">Python</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-rockset">Rockset</a></td>
        <td><a href="https://kestra.io/plugins/plugin-script-r">RScript</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-fs#sftp">SFTP</a></td>
        <td><a href="https://kestra.io/plugins/plugin-servicenow">ServiceNow</a></td>
        <td><a href="https://kestra.io/plugins/plugin-singer">Singer</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-script-shell">Shell</a></td>
        <td><a href="https://kestra.io/plugins/plugin-notifications#slack">Slack</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-snowflake">Snowflake</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-soda">Soda</a></td>
        <td><a href="https://kestra.io/plugins/plugin-fs#ssh">SSH</a></td>
        <td><a href="https://kestra.io/plugins/plugin-notifications#telegram">Telegram</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-trino">Trino</a></td>
        <td><a href="https://kestra.io/plugins/plugin-serdes#xml">XML</a></td>
        <td><a href="https://kestra.io/plugins/plugin-jdbc-vertica">Vertica</a></td>
    </tr>
</table>



This list is growing quickly and we welcome contributions.

## Community Support

If you need help or have any questions, reach out using one of the following channels:

- [Slack](https://kestra.io/slack) - join the community and get the latest updates.
- [GitHub discussions](https://github.com/kestra-io/kestra/discussions) - useful to start a conversation that is not a bug or feature request.
- [Twitter](https://twitter.com/kestra_io) - to follow up with the latest updates.


## Roadmap

See the [open issues](https://github.com/kestra-io/kestra/issues) for a list of proposed features (and known issues) or look at the [project board](https://github.com/orgs/kestra-io/projects/2).


## Contributing

We love contributions, big or small. Check out [our contributor guide](https://github.com/kestra-io/kestra/blob/develop/.github/CONTRIBUTING.md) for details on how to contribute to Kestra.

See our [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/) for details on developing and publishing Kestra plugins.


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)
