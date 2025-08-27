# Kestra Devcontainer

This devcontainer provides a quick and easy setup for anyone using VSCode to get up and running quickly with this project to start development on either the frontend or backend. It bootstraps a docker container for you to develop inside of without the need to manually setup the environment.

---

## INSTRUCTIONS

### Setup:

Take a look at this guide to get an idea of what the setup is like as this devcontainer setup follows this approach: https://kestra.io/docs/getting-started/contributing

Once you have this repo cloned to your local system, you will need to install the VSCode extension [Remote Development](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.vscode-remote-extensionpack).

Then run the following command from the command palette:
`Dev Containers: Open Folder in Container...` and select your Kestra root folder.

This will then put you inside a docker container ready for development.

NOTE: you'll need to wait for the gradle build to finish and compile Java files but this process should happen automatically within VSCode.

In the meantime, you can move onto the next step...

---

### Development:
- (Optional) By default, your dev server will target `localhost:8080`. If your backend is running elsewhere, you can create `.env.development.local` under `ui` folder with this content:
```
VITE_APP_API_URL={myApiUrl}
```

- Navigate into the `ui` folder and run `npm install` to install the dependencies for the frontend project.

- Now go to the `cli/src/main/resources` folder and create a `application-override.yml` file.

Now you have two choices:

`Local mode`:

Runs the Kestra server in local mode which uses a H2 database, so this is the only config you'd need:

```yaml
micronaut:
  server:
    cors:
      enabled: true
      configurations:
        all:
          allowedOrigins:
            - http://localhost:5173
```

You can then open a new terminal and run the following command to start the backend server: `./gradlew runLocal`

`Standalone mode`:

Runs in standalone mode which uses Postgres. Make sure to have a local Postgres instance already running on localhost:

```yaml
kestra:
  repository:
    type: postgres
  storage:
    type: local
    local:
      base-path: "/app/storage"
  queue:
    type: postgres
  tasks:
    tmp-dir:
      path: /tmp/kestra-wd/tmp
  anonymous-usage-report:
    enabled: false

datasources:
  postgres:
    # It is important to note that you must use the "host.docker.internal" host when connecting to a docker container outside of your devcontainer as attempting to use localhost will only point back to this devcontainer.
    url: jdbc:postgresql://host.docker.internal:5432/kestra
    driverClassName: org.postgresql.Driver
    username: kestra
    password: k3str4

flyway:
  datasources:
    postgres:
      enabled: true
      locations:
        - classpath:migrations/postgres
      # We must ignore missing migrations as we may delete the wrong ones or delete those that are not used anymore.
      ignore-migration-patterns: "*:missing,*:future"
      out-of-order: true

micronaut:
  server:
    cors:
      enabled: true
      configurations:
        all:
          allowedOrigins:
            - http://localhost:5173
```

Then add the following settings to the `.vscode/launch.json` file:

```json
{
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Kestra Standalone",
      "request": "launch",
      "mainClass": "io.kestra.cli.App",
      "projectName": "cli",
      "args": "server standalone"
    }
  ]
}
```

You can then use the VSCode `Run and Debug` extension to start the Kestra server.

Additionally, if you're doing frontend development, you can run `npm run dev` from the `ui` folder after having the above running (which will provide a backend) to access your application from `localhost:5173`. This has the benefit to watch your changes and hot-reload upon doing frontend changes.

#### Plugins
If you want your plugins to be loaded inside your devcontainer, point the `source` field to a folder containing jars of the plugins you want to embed in the following snippet in `devcontainer.json`:
```
"mounts": [
  {
    "source": "/absolute/path/to/your/local/jar/plugins/folder",
    "target": "/workspaces/kestra/local/plugins",
    "type": "bind"
  }
],
```

---

### GIT

If you want to commit to GitHub, make sure to navigate to the `~/.ssh` folder and either create a new SSH key or override the existing `id_ed25519` file and paste an existing SSH key from your local machine into this file. You will then need to change the permissions of the file by running: `chmod 600 id_ed25519`. This will allow you to then push to GitHub.

---
