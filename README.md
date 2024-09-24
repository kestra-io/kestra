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

<p align="center">
    <a href="https://go.kestra.io/video/product-overview" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 4 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>Click on the image to get started with Kestra in 4 minutes.</i></p>

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
    - a **flow trigger** - flows can be triggered from other flows using a [flow trigger](https://kestra.io/docs/workflow-components/triggers/flow-trigger) or a [subflow](https://kestra.io/docs/workflow-components/subflows), enabling highly modular workflows.
    - **custom events**, including a new file arrival (*file detection event*), a new message in a message bus, query completion, and more.
5. `Inputs` allow you to pass runtime-specific variables to a flow. They are strongly typed, and allow additional [validation rules](https://kestra.io/docs/workflow-components/inputs#input-validation).


## Extensible platform

Most tasks in Kestra are available as [plugins](https://kestra.io/plugins) including plugins reacting to events from external systems in real-time (Kafka, Redis, Pulsar, AMQP, MQTT, NATS, AWS SQS, Google Pub/Sub, Azure Event Hubs) and script tasks supporting many programming languages (a.o., Python, R, Node.js, Shell, Go) and Docker containers.

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
namespace: company.team

description: Let's `write` some **markdown** - [first flow](https://t.ly/Vemr0) 🚀

labels:
  owner: rick.astley
  project: never-gonna-give-you-up
  environment: dev
  country: US

inputs:
  - id: user
    type: STRING
    required: false
    defaults: Rick Astley
    description: This is an optional input. If not set at runtime, it will use the default value "Rick Astley".

variables:
  first: 1
  second: "{{ vars.first }} < 2"

tasks:
  - id: hello
    type: io.kestra.plugin.core.log.Log
    description: this is a *task* documentation
    message: |
      The variables we used are {{ vars.first }} and {{ render(vars.second) }}.
      The input is {{ inputs.user }} and the task was started at {{ taskrun.startDate }} from flow {{ flow.id }}.

  - id: parallel
    type: io.kestra.plugin.core.flow.Parallel
    disabled: false
    timeout: PT10M
    concurrent: 2
    tasks:
      - id: task1
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - echo "running {{task.id}}"
          - sleep 2

      - id: task2
        type: io.kestra.plugin.scripts.shell.Commands
        commands:
          - echo "running {{task.id}}"
          - sleep 3

pluginDefaults:
  - type: io.kestra.plugin.core.log.Log
    values:
      level: TRACE
      retry:
        type: constant # type: string
        interval: PT15M # type: Duration
        maxDuration: PT1H # type: Duration
        maxAttempt: 5 # type: int
        warningOnRetry: true # type: boolean, default is false

triggers:
  - id: monthly
    type: io.kestra.plugin.core.trigger.Schedule
    cron: "0 9 1 * *" # every first day of the month at 9am
```


## Built-in code editor

You can write workflows directly from the UI. When writing your workflows, the UI provides:
- autocompletion
- syntax validation
- embedded plugin documentation
- example flows provided as [blueprints](https://kestra.io/blueprints)
- topology view (view of your dependencies in a Directed Acyclic Graph) that gets updated live as you modify and add new tasks.


## Stay up to date

We release new versions every first Tuesday of every second month. Give the repository a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)


## Getting Started

Follow the steps below to start local development.

### Prerequisites

Make sure that Docker is installed and running on your system. The default installation requires the following:
- [Docker](https://docs.docker.com/engine/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)


### Launch Kestra

Download the Docker Compose file:

```bash
curl -o docker-compose.yml https://raw.githubusercontent.com/kestra-io/kestra/develop/docker-compose.yml
```

Alternatively, you can use `wget https://raw.githubusercontent.com/kestra-io/kestra/develop/docker-compose.yml`.


Start Kestra:

```bash
docker compose up -d
```


Open `http://localhost:8080` in your browser and create your first flow.


### Hello-World flow

Here is a simple example logging hello world message to the terminal:

```yaml
id: getting_started
namespace: dev

tasks:
  - id: hello_world
    type: io.kestra.plugin.core.log.Log
    message: Hello World!
```

For more information:

- Follow the [getting started tutorial](https://kestra.io/docs/getting-started/).
- Read the [documentation](https://kestra.io/docs/) to learn how to:
    - [Develop your flows](https://kestra.io/docs/developer-guide/)
    - [Deploy Kestra](https://kestra.io/docs/installation/)
    - Use the official [Terraform provider](https://kestra.io/docs/terraform/) to deploy your flows.


## Plugins

Kestra is built on top of a [plugin ecosystem](https://kestra.io/plugins/). You can browse through hundreds of pre-built plugins or [build your own](https://kestra.io/docs/plugin-developer-guide/).

This list is growing quickly and we welcome contributions.

## Community Support

If you need help or have any questions, reach out using one of the following channels:

- [Slack](https://kestra.io/slack) - join the community and get the latest updates.
- [Twitter](https://twitter.com/kestra_io) - to follow up with the latest updates.


## Contributing

We love contributions, big or small. Check out [our contributor guide](https://github.com/kestra-io/kestra/blob/develop/.github/CONTRIBUTING.md) for details on how to contribute to Kestra.

See our [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/) for details on developing and publishing Kestra plugins.


## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)
