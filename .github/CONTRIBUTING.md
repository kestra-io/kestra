## Code of Conduct

This project and everyone participating in it is governed by the
[Kestra Code of Conduct](https://github.com/kestra-io/kestrablob/master/CODE_OF_CONDUCT.md).
By participating, you are expected to uphold this code. Please report unacceptable behavior
to <hello@kestra.io>.

## I Want To Contribute

> ### Legal Notice
> When contributing to this project, you must agree that you have authored 100% of the content, that you have the necessary rights to the content and that the content you contribute may be provided under the project license.


### Submit issues

### Reporting bugs
Bug reports help us make Kestra better for everyone. We provide a preconfigured template for bugs to make it very clear what information we need.
Please search within our [already reported bugs](https://github.com/kestra-io/kestra/issues?q=is%3Aissue+is%3Aopen+label%3Abug) before raising a new one to make sure you're not raising a duplicate.

### Reporting security issues
Please do not create a public GitHub issue. If you've found a security issue, please email us directly at hello@kestra.io instead of raising an issue.


### Requesting new features
To request new features, please create an issue on this project.
If you would like to suggest a new feature, we ask that you please use our issue template. It contains a few essential questions that help us understand the problem you are looking to solve and how you think your recommendation will address it.
To see what has already been proposed by the community, you can look [here](https://github.com/kestra-io/kestra/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement).
Watch out for duplicates! If you are creating a new issue, please check existing open, or recently closed. Having a single voted for issue is far easier for us to prioritize.

### Your First Code Contribution

#### Requirements
The following dependencies are required to build Kestra locally.
- Java 11+
- Node 14+
- Docker & Docker Compose
- an IDE (Intellij IDEA, Eclipse or VS Code)

To start contributing:
- Start by [forking](https://docs.github.com/en/github/getting-started-with-github/fork-a-repo) the repository
- Clone the fork on your workstation:

```shell
git clone git@github.com:{YOUR_USERNAME}/kestra.git
cd kestra
```

#### Develop backend
Open the cloned repository in your favorite IDE. In most of decent IDE, gradle build will be detected and all dependencies will be downloaded.

- You may need to enable java annotation processors since we are using it a lot.
- The main class is `io.kestra.cli.App` from module `kestra.cli.main`
- Pass as program arguments the server you want to develop, for example `server standalone` will the [standalone server](https://kestra.io/docs/administrator-guide/servers/#kestra-standalone-development-environment-servers)
- ![Intellij Idea Configuration ](https://user-images.githubusercontent.com/2064609/161399626-1b681add-cfa8-4e0e-a843-2631cc59758d.png) Intellij Idea configuration can be found in screenshot below.
  - `MICRONAUT_ENVIRONMENTS`: can be set any string and will load a custom configuration file in `cli/src/main/resources/application-{env}.yml`
  - `KESTRA_PLUGINS_PATH`: is the path where you will save plugins as Jar and will be load on the startup.
- You can also use the gradle task `./gradlew runStandalone` that will run a standalone server with `MICRONAUT_ENVIRONMENTS=override` and plugins path `local/plugins`
- The server start by default on port 8080 and is reachable on `http://localhost:8080`


#### Develop frontend
The frontend is located on `/ui` folder.

- `npm install`
- create a files `ui/.env.development.local` with content `VUE_APP_API_URL=http://localhost:8080` (or your actual server url)
- `npm run serve` will start the development server with hot reload.
- The server start by default on port 8090 and is reachable on `http://localhost:8090`
- You can run `npm run build` in order to build the front-end that will be delivered from the 
backend (without running the `npm serve`) above.

> If you have CORS restrictions when using the local development npm server, you need to configure 
> the backend to allow the http://localhost:8090 origin
> ```cors:
>      enabled: true
>      configurations:
>        all:
>          allowedOrigins:
>            - http://localhost:8090```

#### Develop plugins
A complete documentation for developing plugin can be found [here](https://kestra.io/docs/plugin-developer-guide/).

### Improving The Documentation
The main documentation is located in a separate [repository](https://github.com/kestra-io/kestra.io). For task documentation, there are located directly on Java source using [swagger annotation](https://github.com/swagger-api/swagger-core/wiki/Swagger-2.X---Annotations) (Example: [for Bash tasks](https://github.com/kestra-io/kestra/blob/develop/core/src/main/java/io/kestra/core/tasks/scripts/AbstractBash.java))
