<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestration Platform
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
<p align="center" style="color:grey;"><i>Click on the image to learn how to get started with Kestra in 4 minutes.</i></p>


## 🌟 What is Kestra?

Kestra is an open-source, event-driven orchestration platform that that makes both **scheduled** and **event-driven** workflows easy. By bringing **Infrastructure as Code** best practices to data, process, and microservice orchestration, you can build reliable workflows and manage them with confidence.

In just a few lines of YAML, you can [create a flow](https://kestra.io/docs/getting-started) directly from the UI.

**Key Features:**
- **Everything as Code and from the UI:** keep **workflows as code** with a full **Git Version Control** integration, even when building them from the UI.
- **Event-Driven & Scheduled Workflows:** automate both **scheduled** and **real-time** event-driven workflows via a simple `trigger` definition in YAML.
- **Declarative YAML Interface:** define workflows using a simple configuration built with autocompletion, syntax validation, and embedded documentation in the **built-in code editor**.
- **Rich Plugin Ecosystem:** hundreds of plugins built in to extract data from any database, cloud storage, or API, and **run scripts in any language**.
- **Intuitive UI & Code Editor:** build and visualize workflows directly from the UI with syntax highlighting, auto-completion and real-time syntax validation.
- **Scalable & Resilient:** designed to handle millions of workflows, with high availability and fault tolerance.
- **Version Control Friendly:** write your workflows from the built-in code Editor and push them to your preferred Git branch directly from Kestra, enabling best practices with CI/CD pipelines and version control systems.
- **Structure & Resilience**: tame chaos and bring resilience to your workflows with **namespaces**, **labels**, **subflows**, **retries**, **timeout**, **error handling**, **inputs**, **outputs** that generate artifacts in the UI, **variables**, **conditional branching**, **advanced scheduling**, **event triggers**, **backfills**, **dynamic tasks**, **sequential and parallel tasks**, and skip tasks or triggers when needed by setting the flag `disabled` to `true`.


> The YAML definition gets automatically adjusted any time you make changes to a workflow from the UI or via an API call. Therefore, the orchestration logic is **always managed declaratively in code**, even if you modify your workflows in other ways (UI, CI/CD, Terraform, API calls).


![Adding new tasks in the UI](https://kestra.io/adding-tasks.gif)

---

## 🚀 Quick Start

### Try Kestra Live

Experience Kestra firsthand with our [**Live Demo**](https://demo.kestra.io/ui/login?auto). No installation required!

### Get Started Locally in 5 Minutes

#### Prerequisites

- [Docker](https://docs.docker.com/engine/install/)
- [Docker Compose](https://docs.docker.com/compose/install/)

#### Launch Kestra

Download the `docker-compose.yml` file:

```bash
curl -o docker-compose.yml https://raw.githubusercontent.com/kestra-io/kestra/develop/docker-compose.yml
```

Alternatively:

```bash
wget https://raw.githubusercontent.com/kestra-io/kestra/develop/docker-compose.yml
```

Start Kestra:

```bash
docker compose up -d
```

Access the Kestra UI at [http://localhost:8080](http://localhost:8080) and start building your first flow!

#### Your First Flow: Hello World

Create a new flow with the following content:

```yaml
id: hello_world
namespace: dev

tasks:
  - id: say_hello
    type: io.kestra.plugin.core.log.Log
    message: "Hello, World!"
```

Run the flow and see the output in the UI!

---

## 🧩 Plugin Ecosystem

Kestra's functionality is extended through a rich ecosystem of plugins. Here's an overview of some popular plugins:

### Scripting and Automation

| Plugin     | Description                                                                                      |
|------------|--------------------------------------------------------------------------------------------------|
| **Python** | Run [Python scripts](https://kestra.io/docs/how-to-guides/python) in Docker, local process or scale to serverless containers using [Task Runners](https://kestra.io/docs/task-runners) |
| **Shell**  | Execute [shell commands](https://kestra.io/docs/how-to-guides/shell) and scripts                                                               |
| **SQL**    | Run SQL queries against various databases                                                        |
| **HTTP**   | Make [HTTP requests](https://kestra.io/plugins/core/tasks/http/io.kestra.plugin.core.http.request) to interact with APIs                                                         |
| **SSH**    | Execute commands on remote servers [via SSH](https://kestra.io/plugins/plugin-fs/tasks/ssh/io.kestra.plugin.fs.ssh.command)                                                       |

### Data Processing

| Plugin                  | Description                                       |
|-------------------------|---------------------------------------------------|
| **Kafka**               | [Real-time](https://kestra.io/docs/workflow-components/triggers/realtime-trigger) data streaming with Apache Kafka        |
| **BigQuery**            | Interact with [Google BigQuery](https://kestra.io/plugins/plugin-gcp#bigquery) for analytics       |
| **Spark**               | Run Apache Spark jobs for big data processing     |
| **Airflow Migration**   | Migrate workflows from [Apache Airflow](https://kestra.io/plugins/plugin-airflow/tasks/dags/io.kestra.plugin.airflow.dags.triggerdagrun)             |

### Cloud Integrations

| Plugin                   | Description                                                                                                          |
|--------------------------|----------------------------------------------------------------------------------------------------------------------|
| **AWS**                  | Run flows when a new file arrives in your Amazon S3 bucket or when you receive an event from SQS, SNS or EventBridge |
| **Google Cloud Storage** | Interact with a variety og Google Cloud services                                                                     |
| **Azure**                | Work with Azure Batch, Eventhubs, Blob Storage and more                                                              |
| **Docker**               | Run Docker containers within your workflows                                                                          |
| **Kubernetes**           | Launch Kubernetes jobs to scale your workflows                                                                       |

### Monitoring and Notifications

| Plugin                  | Description                                       |
|-------------------------|---------------------------------------------------|
| **Slack**               | Send messages to Slack channels                   |
| **Email**               | Send email notifications                          |
| **PagerDuty**           | Trigger alerts in PagerDuty                       |
| **Elasticsearch**       | Log and monitor workflows with Elasticsearch      |

> **Note:** This is just a snapshot of the available plugins. Explore the full list on our [Plugins Page](https://kestra.io/plugins/).

---


## 🎨 Build Workflows Visually

Kestra provides an intuitive UI that allows you to interactively build and visualize your workflows:

- **Drag-and-Drop Interface:** add and rearrange tasks from the Topology Editor.
- **Real-Time Validation:** instant feedback on your workflow's syntax and structure to catch errors early.
- **Auto-Completion:** smart suggestions as you type.
- **Live Topology View:** see your workflow as a Directed Acyclic Graph (DAG) that updates in real-time.

---

## 📚 Key Concepts

- **Flows:** the core unit in Kestra, representing a workflow composed of tasks.
- **Tasks:** individual units of work, such as running a script, moving data, or calling an API.
- **Namespaces:** logical grouping of flows for organization and isolation.
- **Triggers:** schedule or events that initiate the execution of flows.
- **Inputs & Variables:** parameters and dynamic data passed into flows and tasks.

---

## 🔧 Extensible and Developer-Friendly

### Plugin Development

Create custom plugins to extend Kestra's capabilities. Check out our [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/) to get started.

### Infrastructure as Code

- **Version Control:** store your flows in Git repositories.
- **CI/CD Integration:** automate deployment of flows using CI/CD pipelines.
- **Terraform Provider:** manage Kestra resources with the [official Terraform provider](https://kestra.io/docs/terraform/).

---

## 🌐 Join the Community

Stay connected and get support:

- **Slack:** Join our [Slack community](https://kestra.io/slack) to ask questions and share ideas.
- **Twitter:** Follow us on [Twitter](https://twitter.com/kestra_io) for the latest updates.
- **YouTube:** Subscribe to our [YouTube channel](https://www.youtube.com/@kestra-io) for tutorials and webinars.
- **LinkedIn:** Connect with us on [LinkedIn](https://www.linkedin.com/company/kestra/).

---

## 🤝 Contributing

We welcome contributions of all kinds!

- **Report Issues:** Found a bug or have a feature request? Open an [issue on GitHub](https://github.com/kestra-io/kestra/issues).
- **Contribute Code:** Check out our [Contributor Guide](https://github.com/kestra-io/kestra/blob/develop/.github/CONTRIBUTING.md) to start contributing.
- **Develop Plugins:** Build and share plugins using our [Plugin Developer Guide](https://kestra.io/docs/plugin-developer-guide/).

---

## 📄 License

Kestra is licensed under the Apache 2.0 License © [Kestra Technologies](https://kestra.io).

---

## ⭐️ Stay Updated

Give our repository a star to stay informed about the latest features and updates!

[![Star the Repo](https://kestra.io/star.gif)](https://github.com/kestra-io/kestra)

---

Thank you for considering Kestra for your workflow orchestration needs. We can't wait to see what you'll build!

