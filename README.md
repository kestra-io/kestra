<p align="center">
  <a href="https://www.kestra.io">
    <img width="460" src="https://kestra.io/logo.svg"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-driven declarative orchestrator to simplify data operations <br>
</h1>

<div align="center">
  <a href="https://github.com/kestra-io/kestra/blob/develop/LICENSE"><img src="https://img.shields.io/github/license/kestra-io/kestra?style=flat-square" alt="License" /></a>
  <a href="https://github.com/kestra-io/kestra/pulse"><img src="https://img.shields.io/github/commit-activity/m/kestra-io/kestra?style=flat-square" alt="Commits-per-month"></a>
  <a href="https://github.com/kestra-io/kestra/stargazers"><img src="https://img.shields.io/github/stars/kestra-io/kestra.svg?style=flat-square" alt="Github star" /></a>
  <a href="https://github.com/kestra-io/kestra/releases"><img src="https://img.shields.io/github/tag-pre/kestra-io/kestra.svg?style=flat-square" alt="Last Version" /></a>
  <a href="https://hub.docker.com/r/kestra/kestra"><img src="https://img.shields.io/docker/pulls/kestra/kestra.svg?style=flat-square" alt="Docker pull" /></a>
  <a href="https://artifacthub.io/packages/helm/kestra/kestra"><img src="https://img.shields.io/badge/Artifact%20Hub-kestra-417598?style=flat-square&logo=artifacthub" alt="Artifact Hub" /></a>
  <a href="https://kestra.io"><img src="https://img.shields.io/badge/Website-kestra.io-192A4E?style=flat-square" alt="Kestra infinitely scalable orchestration and scheduling platform"></a>
  <a href="https://kestra.io/slack"><img src="https://img.shields.io/badge/Slack-chat-400d40?style=flat-square&logo=slack" alt="Slack"></a>
  <a href="https://github.com/kestra-io/kestra/discussions"><img src="https://img.shields.io/github/discussions/kestra-io/kestra?style=flat-square" alt="Github discussions"></a>
  <a href="https://twitter.com/kestra_io"><img src="https://img.shields.io/twitter/follow/kestra_io?style=flat-square" alt="Twitter" /></a>
  <a href="https://app.codecov.io/gh/kestra-io/kestra"><img src="https://img.shields.io/codecov/c/github/kestra-io/kestra?style=flat-square&token=It6L7BTaWK" alt="Code Cov" /></a>
  <a href="https://github.com/kestra-io/kestra/actions"><img src="https://img.shields.io/github/actions/workflow/status/kestra-io/kestra/main.yml?branch=develop&style=flat-square" alt="Github Actions" /></a>
</div>

<br />

<p align="center">
    <a href="https://kestra.io/"><b>Website</b></a> •
    <a href="https://twitter.com/kestra_io"><b>Twitter</b></a> •
    <a href="https://www.linkedin.com/company/kestra/"><b>Linked In</b></a> •
    <a href="https://kestra.io/slack"><b>Slack</b></a> •
    <a href="https://kestra.io/docs/"><b>Documentation</b></a>
</p>

<br />

<p align="center"><img src="https://kestra.io/video.gif" alt="modern data orchestration and scheduling platform " width="640px" /></p>


## Live Demo

Try Kestra using our [live demo](https://demo.kestra.io).

## What is Kestra

Kestra is an open-source, **event-driven** orchestrator that simplifies data operations and improves collaboration between engineers and business users. By bringing **Infrastructure as Code** best practices to data pipelines, Kestra allows you to build reliable workflows and manage them with confidence.

Thanks to the **declarative YAML interface** for defining orchestration logic, everyone who benefits from analytics can participate in the data pipeline creation process. The UI automatically adjusts the YAML definition any time you make changes to a workflow from the UI or via an API call. Therefore, the orchestration logic is defined declaratively in code, even if some workflow components are modified in other ways.

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
- documenting your flows, tasks and triggers by adding a markdown description to any component
- adding labels to add additional metadata to your flows such as the flow owner or team:

```yaml
id: hello
namespace: prod
description: Hi from `Kestra` and a **markdown** description.
labels:
  owner: john-doe
  team: data-engineering
tasks:
  - id: hello
    type: io.kestra.core.tasks.log.Log
    message: Hello world!
    description: a *very* important task
    disabled: false
    timeout: 10M
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
          - 'sleep 10'
      - id: task2
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - 'echo "running {{task.id}}"'
          - 'sleep 10'
      - id: task3
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - 'echo "running {{task.id}}"'
          - 'sleep 10'
triggers:
  - id: schedule
    type: io.kestra.core.models.triggers.types.Schedule
    cron: "*/15 * * * *"
    backfill:
      start: 2023-06-25T14:00:00Z
```


## Built-in code editor

You can write workflows directly from the UI. When writing your workflows, the UI provides:
- autocompletion
- syntax validation
- embedded plugin documentation
- topology view (view of your dependencies in a Directed Acyclic Graph) that get updated live as you modify and add new tasks.


## Getting Started

To get a local copy up and running, follow the steps below.

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
docker-compose up
```


Open `http://localhost:8080` in your browser and create your first flow.


### Hello-World flow

Here is a simple example logging hello world message to the terminal:

```yaml
id: hello
namespace: prod
tasks:
  - id: hello-world
    type: io.kestra.core.tasks.log.Log
    message: Hello world!
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
        <td><a href="https://kestra.io/plugins/plugin-airbyte">Airbyte</a></td>
        <td><a href="https://kestra.io/plugins/plugin-aws#s3">Amazon S3</a></td>
        <td><a href="https://kestra.io/plugins/plugin-serdes#avro">Avro</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-azure/#storage-blob">Azure Blob Storage</a></td>
        <td><a href="https://kestra.io/plugins/core/tasks/scripts/io.kestra.core.tasks.scripts.bash">Bash</a></td>
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
        <td><a href="https://kestra.io/plugins/plugin-script-groovy">Groovy</a></td>
        <td><a href="https://kestra.io/plugins/plugin-fs#http">Http</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/plugin-serdes#json">JSON</a></td>
        <td><a href="https://kestra.io/plugins/plugin-script-jython">Jython</a></td>
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
        <td><a href="https://kestra.io/plugins/plugin-script-nashorn">Nashorn</a></td>
    </tr>
    <tr>
        <td><a href="https://kestra.io/plugins/core/tasks/scripts/io.kestra.core.tasks.scripts.node">Node</a></td>
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
        <td><a href="https://kestra.io/plugins/core/tasks/scripts/io.kestra.core.tasks.scripts.python">Python</a></td>
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



This list is growing quickly and we welcome contributions.

## Community Support

If you need help or have any questions, reach out using one of the following channels:

- [GitHub discussions](https://github.com/kestra-io/kestra/discussions) - useful to start a conversation that is not a bug or feature request.
- [Slack](https://kestra.io/slack) - join the community and get the latest updates.
- [Twitter](https://twitter.com/kestra_io) - to follow up with the latest updates.


## Roadmap

See the [open issues](https://github.com/kestra-io/kestra/issues) for a list of proposed features (and known issues) or look at the [project board](https://github.com/orgs/kestra-io/projects/2).


## Contributing

We love contributions, big or small. Check out [our contributor guide](https://github.com/kestra-io/kestra/blob/develop/.github/CONTRIBUTING.md) for details on how to contribute to Kestra.

See our [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/) for details on developing and publishing Kestra plugins.


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)
