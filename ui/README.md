# Kestra UI

Kestra UI is running using [Vite](https://vite.dev/).

---

## INSTRUCTIONS

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

If you're doing frontend development, you can run `npm run dev` from the `ui` folder after having the above running (which will provide a backend) to access your application from `localhost:5173`. This has the benefit to watch your changes and hot-reload upon doing frontend changes.
