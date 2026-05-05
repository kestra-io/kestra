<p align="center">
  <a href="https://www.kestra.io">
    <img src="https://kestra.io/banner.png"  alt="Kestra workflow orchestrator" />
  </a>
</p>

<h1 align="center" style="border-bottom: none">
    Event-Driven Declarative Orchestrator
</h1>

<br />

<p align="center">
    <a href="https://twitter.com/kestra_io"><img height="25" src="https://kestra.io/twitter.svg" alt="twitter" /></a> &nbsp;
    <a href="https://www.linkedin.com/company/kestra/"><img height="25" src="https://kestra.io/linkedin.svg" alt="linkedin" /></a> &nbsp;
<a href="https://www.youtube.com/@kestra-io"><img height="25" src="https://kestra.io/youtube.svg" alt="youtube" /></a> &nbsp;
</p>

<br />
<p align="center">
    <a href="https://go.kestra.io/video/product-overview" target="_blank">
        <img src="https://kestra.io/startvideo.png" alt="Get started in 4 minutes with Kestra" width="640px" />
    </a>
</p>
<p align="center" style="color:grey;"><i>Get started with Kestra in 4 minutes.</i></p>

# kestra

![Version: 1.0.53](https://img.shields.io/badge/Version-1.0.53-informational?style=flat-square) ![AppVersion: v1.3.14](https://img.shields.io/badge/AppVersion-v1.3.14-informational?style=flat-square)

Infinitely scalable, event-driven, language-agnostic orchestration and scheduling platform to manage millions of workflows declaratively in code.

**Homepage:** <https://kestra.io>

## Installing the Chart

To install the chart with the release name `my-kestra`:

```console
$ helm repo add kestra https://helm.kestra.io/
$ helm install my-kestra kestra/kestra --version 1.0.53
```

## Migration from 0.x.x to 1.0.0

> Breaking changes have been made to the Helm chart in order to support the new features and improvements introduced in Kestra 1.0.0. Please review the following changes carefully before upgrading:

### If you need to change the kestra docker repository

Before:
```yaml
image:
  image: kestra/kestra
```

After:
```yaml
image:
  repository: kestra/kestra
```

### We changed the way to configure service account

Before:
```yaml
serviceAccountName: ""
```

After:
```yaml
serviceAccount:
  create: true
  automount: true
  annotations: {}
  name: ""
```

### We removed postgres, minio, kafka and elasticsearch from the chart dependencies. You can now use your own managed services or deploy them separately.

### Most of the deployment configuration options have been restructured. There is now a common entry in the values.yaml.

Before:
```yaml
nodeSelector: {}
tolerations: []
affinity: {}
extraVolumeMounts: []
extraVolumes: []
extraEnv: []
# more...
```

After:
```yaml
common:
  nodeSelector: {}
  tolerations: []
  affinity: {}
  extraVolumeMounts: []
  extraVolumes: []
  extraEnv: []
  # more...
```

### You can override all those configuration options in the deployments entry in the values.yaml.

```yaml
deployments:
  standalone:
    nodeSelector: {}
    tolerations: []
    affinity: {}
    extraVolumeMounts: []
    extraVolumes: []
    extraEnv: []
    # more...
```

### We changed the way to provide custom configuration files to Kestra. It's now all under configurations entry in the values.yaml.

Before:
```yaml
### This creates a config map of the Kestra configuration
configuration: {}
# Example: Setting the plugin defaults for the Docker runner
#   kestra:
#     plugins:
#       configurations:
#         - type:  io.kestra.plugin.scripts.runner.docker.Docker
#           values:
#             volume-enabled: true
### This will create a Kubernetes Secret for the values provided
## This will be appended to kestra-secret with the key application-secrets.yml
secrets: {}
# Example: Store your postgres backend credentials in a secret
#   secrets:
#     kestra:
#       datasources:
#         postgres:
#           username: pguser
#           password: mypass123
#           url: jdbc:postgresql://pghost:5432/db
### Load Kestra configuration from existing secret
## Here this assumes the secret is already deployed and the following apply:
## 1. The secret type is "Opaque"
## 2. The secret has a single key
## 3. The value of the secret is the Kestra configuration.
externalSecret: {}
  #secretName: secret-name
  #key: application-kestra.yml
### configuration files
## This option allows you to reference existing local files to configure Kestra, e.g.
configurationPath:
# configurationPath: /app/application.yml,/app/application-secrets.yml
extraConfigMapEnvFrom:
  # - name: my-existing-configmap-no-prefix
  # - name: my-existing-configmap-with-prefix
  #   prefix: KESTRA_
extraSecretEnvFrom:
  # - name: my-existing-no-prefix
  # - name: my-existing-with-prefix
  #   prefix: SECRET_
```

After:
```yaml
configurations:
  application:
    kestra:
      queue:
        type: h2
      repository:
        type: h2
      storage:
        type: local
        local:
          basePath: "/app/storage"
    datasources:
      h2:
        url: jdbc:h2:mem:public;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
        username: kestra
        password: ""
        driverClassName: org.h2.Driver
  configmaps:
    - name: kestra-others
      key: others.yml
  secrets:
    - name: kestra-basic-auth
      key: basic-auth.yml
```

No need of taking care of `configurationPath:`; It's automatically managed by the chart.

If you need to add extra environment variables from existing ConfigMaps or Secrets, you can still use `extraEnv` and `extraEnvFrom` under the `common` entry.

If you want your deployment to restart if `configurations.application` change, you can enable the option `common.configmapReloader.enabled: true` (default to false).

### We upgrade also the way the dind is managed. It's now under the `dind` entry in the values.yaml.

We add `dind.mode`, to choose between `rootless` and `insecure` ; `rootless` is the default and recommended mode.

### Usage for enterprise edition workerGroups

```yaml
workerGroups:
  test-000:
    enabled: false
  test-001:
    enabled: true
    workerThreads: 128
    serviceAccountName: "kestra-worker-sa-external"
  test-002:
    enabled: true
```
The **workerGroups** follow exactly the same pattern you see in deployments key **worker**."

## Values

### common settings

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| common.affinity | object | `{}` | Affinity rules for pod scheduling. |
| common.annotations | object | `{}` | Annotations applied to all resources. |
| common.autoscaler | object | `{"enabled":false,"extra":{},"maxReplicas":3,"metrics":[],"minReplicas":1}` | Enabled or not horizontal autoscaling. |
| common.configmapReloader | object | `{"enabled":false}` | Application configuration reloader if configmap changed. |
| common.extraContainers | list | `[]` | Additional sidecar containers. |
| common.extraEnv | list | `[]` | Extra environment variables for containers. |
| common.extraEnvFrom | list | `[]` | Import environment variables from ConfigMaps/Secrets. |
| common.extraVolumeMounts | list | `[]` | Extra volume mounts to add to containers. |
| common.extraVolumes | list | `[]` | Extra volumes to add to pods. |
| common.initContainers | list | `[]` | Additional init containers to run before main container. |
| common.jvm.forceActiveProcessors | object | `{"count":"auto","enabled":false,"value":2}` | Sometimes you can have problems with cgroup and cpu limits, then you can force the JVM to use a specific number of processors. |
| common.kind | string | `"Deployment"` | Kind of deployment (Deployment or StatefulSet). |
| common.labels | object | `{}` | Labels applied to all resources. |
| common.livenessProbe | object | `{"failureThreshold":3,"httpGet":{"path":"/health/liveness","port":"management"},"initialDelaySeconds":0,"periodSeconds":5,"successThreshold":1,"timeoutSeconds":3}` | Liveness probe configuration for container health checks. |
| common.nodeSelector | object | `{}` | Node selector constraints for scheduling pods. |
| common.podAnnotations | object | `{}` | Annotations applied specifically to pods. |
| common.podLabels | object | `{}` | Labels applied specifically to pods. |
| common.podSecurityContext | object | `{}` | Security context settings for pods. |
| common.priorityClassName | string | `""` | Priority class for scheduling pods. |
| common.readinessProbe | object | `{"failureThreshold":3,"httpGet":{"path":"/health/readiness","port":"management"},"initialDelaySeconds":0,"periodSeconds":5,"successThreshold":1,"timeoutSeconds":3}` | Readiness probe configuration to determine pod availability. |
| common.replicas | int | `1` | Number of pod replicas to run. |
| common.resources | object | `{}` | Resource requests and limits for containers. |
| common.revisionHistoryLimit | int | `10` | Number of old ReplicaSets to retain for rollback. |
| common.securityContext | object | `{}` | Security context settings for containers. |
| common.startupProbe | object | `{"failureThreshold":120,"httpGet":{"path":"/health","port":"management"},"initialDelaySeconds":1,"periodSeconds":1,"successThreshold":1,"timeoutSeconds":1}` | Startup probe configuration to verify app starts correctly. |
| common.strategy | object | `{}` | Deployment update strategy. |
| common.terminationGracePeriodSeconds | int | `360` | Grace period for pod termination. |
| common.tolerations | list | `[]` | Tolerations for scheduling pods on tainted nodes. |
| common.topologySpreadConstraints | list | `[]` | Topology spread constraints for pod scheduling. |

### kestra configurations

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| configurations.application | object | `{}` | Application configuration (Kestra settings, DB, storage, etc.). |
| configurations.configmaps | list | `[]` | Extra configmaps mounted as configuration files. |
| configurations.secrets | list | `[]` | Extra secrets mounted as configuration files. |

### kestra deployments

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| deployments.executor.enabled | bool | `false` | Enable executor in distributed mode. |
| deployments.executor.extraArgs | list | `[]` | Extra arguments to pass to the container. |
| deployments.indexer.enabled | bool | `false` | Enable indexer in distributed mode. |
| deployments.indexer.extraArgs | list | `[]` | Extra arguments to pass to the container. |
| deployments.scheduler.enabled | bool | `false` | Enable scheduler in distributed mode. |
| deployments.scheduler.extraArgs | list | `[]` | Extra arguments to pass to the container. |
| deployments.standalone.dind.enabled | bool | `true` | Enable dind sidecar in standalone deployment. |
| deployments.standalone.enabled | bool | `true` | Enable standalone Kestra deployment. |
| deployments.standalone.extraArgs | list | `[]` | Extra arguments to pass to the container. |
| deployments.standalone.workerThreads | int | `0` | Number of worker threads for standalone deployment ; "0" to auto-configure based on CPU. |
| deployments.webserver.enabled | bool | `false` | Enable webserver in distributed mode. |
| deployments.webserver.extraArgs | list | `[]` | Extra arguments to pass to the container. |
| deployments.worker.enabled | bool | `false` | Enable worker in distributed mode. |
| deployments.worker.extraArgs | list | `[]` | Extra arguments to pass to the container. |
| deployments.worker.workerThreads | int | `0` | Number of worker threads for worker deployment ; "0" to auto-configure based on CPU. |

### kestra dind insecure

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| dind.base.insecure | object | `{"args":["--log-level=fatal"],"image":{"pullPolicy":"IfNotPresent","repository":"docker","tag":"dind-rootless"},"securityContext":{"allowPrivilegeEscalation":true,"capabilities":{"add":["SYS_ADMIN","NET_ADMIN","DAC_OVERRIDE","SETUID","SETGID"]},"privileged":true,"runAsGroup":0,"runAsUser":0}}` | Insecure dind configuration (privileged). |

### kestra dind rootless

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| dind.base.rootless | object | `{"args":["--log-level=fatal","--group=1000"],"image":{"pullPolicy":"IfNotPresent","repository":"docker","tag":"dind-rootless"},"securityContext":{"privileged":true,"runAsGroup":1000,"runAsUser":1000}}` | Rootless dind configuration. |

### kestra dind

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| dind.enabled | bool | `true` | Enable Docker-in-Docker (dind) sidecar. |
| dind.extraEnv | list | `[]` | Extra environment variables for dind container. |
| dind.mode | string | `"rootless"` | Dind mode (rootless or insecure). |
| dind.resources | object | `{}` | Resource requests and limits for dind sidecar. |
| dind.socketPath | string | `"/dind/"` | Path where dind socket is mounted. |
| dind.tmpPath | string | `"/tmp/"` | Path for temporary storage in dind. |

### image settings

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| image.pullPolicy | string | `"IfNotPresent"` | Policy for pulling container images (Always, IfNotPresent, Never). |
| image.repository | string | `"kestra/kestra"` | Image repository to use for deploying kestra. |
| image.tag | string | `""` | Overrides the image tag (defaults to chart appVersion). |
| imagePullSecrets | list | `[]` | References to secrets for pulling images from private registries. |

### kubernetes ingress

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| ingress.annotations | object | `{}` | Annotations to add to the Ingress. |
| ingress.className | string | `""` | IngressClass name to use. |
| ingress.enabled | bool | `false` | Enable Ingress resource. |
| ingress.hosts | list | `[]` | Hosts and paths for Ingress routing. |
| ingress.tls | list | `[]` | TLS configuration for Ingress. |

### kestra service

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| service.annotations | object | `{}` | Annotations to apply to the Service. |
| service.labels | object | `{}` | Labels to apply to the Service. |
| service.ports.http | object | `{"containerPort":8080,"port":8080,"protocol":"TCP","targetPort":"http"}` | HTTP service port mapping. |
| service.ports.management | object | `{"containerPort":8081,"port":8081,"protocol":"TCP","targetPort":"management"}` | Management (metrics/health) service port mapping. |
| service.type | string | `"ClusterIP"` | Kubernetes Service type (ClusterIP, NodePort, LoadBalancer). |

### kubernetes serviceAccount

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| serviceAccount.annotations | object | `{}` | Annotations to add to the ServiceAccount. |
| serviceAccount.automount | bool | `true` | Automatically mount API credentials to pods. |
| serviceAccount.create | bool | `true` | Specifies whether to create a ServiceAccount. |
| serviceAccount.name | string | `""` | Name of the ServiceAccount to use. |

### kestra workerGroups

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| workerGroups | object | `{}` | EE only - Define additional worker groups with custom settings. |

### Other Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| common.jvm.extraOpts | string | `""` |  |
| common.updateStrategy | object | `{}` | StatefulSet update strategy. |
| extraManifests | list | `[]` | Extra Kubernetes manifests to deploy with the chart. |
| fullnameOverride | string | `""` |  |
| nameOverride | string | `""` |  |

## Documentation
* Full documentation can be found under [kestra.io/docs](https://kestra.io/docs)
* Deployment on Kubernetes documentation can be found [here](https://kestra.io/docs/installation/kubernetes)

## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)

## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)
