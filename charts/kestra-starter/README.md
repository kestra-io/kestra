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

# kestra-starter

![Version: 1.1.23](https://img.shields.io/badge/Version-1.1.23-informational?style=flat-square) ![AppVersion: v1.3.14](https://img.shields.io/badge/AppVersion-v1.3.14-informational?style=flat-square)

Infinitely scalable, event-driven, language-agnostic orchestration and scheduling platform to manage millions of workflows declaratively in code.

**Homepage:** <https://kestra.io>

## Installing the Chart

To install the chart with the release name `my-kestra-starter`:

```console
$ helm repo add kestra https://helm.kestra.io/
$ helm install my-kestra-starter kestra/kestra-starter --version 1.1.23
```

## Requirements

| Repository | Name | Version |
|------------|------|---------|
| https://groundhog2k.github.io/helm-charts/ | postgres | 1.5.7 |
| https://helm.kestra.io/ | kestra | 1.0.53 |

## Values

### Kestra Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| kestra.common | object | `{"configmapReloader":{"enabled":true},"initContainers":[{"args":["set -euo pipefail\necho \"waiting for versitygw...\"\nfor i in $(seq 1 60); do\n  echo \"attempt $i...\"\n  aws --endpoint-url \"$S3_ENDPOINT_URL\" s3api list-buckets >/dev/null 2>&1 && break\n  sleep 2\ndone\naws --endpoint-url \"$S3_ENDPOINT_URL\" s3api head-bucket --bucket \"$BUCKET\" >/dev/null 2>&1 \\\n  || aws --endpoint-url \"$S3_ENDPOINT_URL\" s3 mb \"s3://$BUCKET\"\n"],"command":["sh","-lc"],"env":[{"name":"AWS_ACCESS_KEY_ID","valueFrom":{"secretKeyRef":{"key":"ROOT_ACCESS_KEY","name":"versitygw-root"}}},{"name":"AWS_SECRET_ACCESS_KEY","valueFrom":{"secretKeyRef":{"key":"ROOT_SECRET_KEY","name":"versitygw-root"}}},{"name":"AWS_DEFAULT_REGION","value":"eu-west-1"},{"name":"S3_ENDPOINT_URL","value":"http://versitygw:7070"},{"name":"BUCKET","value":"kestra"}],"image":"amazon/aws-cli:2.15.57","name":"ensure-kestra-bucket"}],"revisionHistoryLimit":5}` | see https://artifacthub.io/packages/helm/kestra/kestra for all available configurations |
| kestra.configurations | object | `{"application":{"datasources":{"postgres":{"driverClassName":"org.postgresql.Driver","password":"ChangeMe#1234","url":"jdbc:postgresql://kestra-starter-postgres:5432/kestra","username":"kestra"}},"kestra":{"queue":{"type":"postgres"},"repository":{"type":"postgres"},"storage":{"s3":{"access-key":"kestra","bucket":"kestra","endpoint":"http://versitygw:7070","force-path-style":true,"region":"eu-west-1","secret-key":"ChangeMe#1234"},"type":"s3"},"tutorialFlows":{"enabled":true}}}}` | see https://artifacthub.io/packages/helm/kestra/kestra for all available configurations |
| kestra.dind | object | `{"enabled":true,"mode":"insecure"}` | see https://artifacthub.io/packages/helm/kestra/kestra for all available configurations |
| kestra.fullnameOverride | string | `"kestra-starter"` | see https://artifacthub.io/packages/helm/kestra/kestra for all available configurations |

### PostgreSQL Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| postgres.fullnameOverride | string | `"kestra-starter-postgres"` | see https://artifacthub.io/packages/helm/groundhog2k/postgres for all available configurations |
| postgres.revisionHistoryLimit | int | `5` | see https://artifacthub.io/packages/helm/groundhog2k/postgres for all available configurations |
| postgres.serviceAccount | object | `{"create":true}` | see https://artifacthub.io/packages/helm/groundhog2k/postgres for all available configurations |
| postgres.settings | object | `{"superuser":{"value":"postgres"},"superuserPassword":{"value":"SuperChangeMe#1234"}}` | see https://artifacthub.io/packages/helm/groundhog2k/postgres for all available configurations |
| postgres.userDatabase | object | `{"name":{"value":"kestra"},"password":{"value":"ChangeMe#1234"},"user":{"value":"kestra"}}` | see https://artifacthub.io/packages/helm/groundhog2k/postgres for all available configurations |

### Other Values

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| s3Emulator.image.repository | string | `"versity/versitygw"` |  |
| s3Emulator.image.tag | string | `"v1.1.0"` |  |
| s3Emulator.sidecar.enabled | bool | `false` |  |
| s3Emulator.sidecar.size | string | `"10Gi"` |  |
| s3Emulator.storage.size | string | `"20Gi"` |  |

## Documentation
* Full documentation can be found under [kestra.io/docs](https://kestra.io/docs)
* Deployment on Kubernetes documentation can be found [here](https://kestra.io/docs/installation/kubernetes)

## License
Apache 2.0 © [Kestra Technologies](https://kestra.io)

## Stay up to date

We release new versions every month. Give the [main repository](https://github.com/kestra-io/kestra) a star to stay up to date with the latest releases and get notified about future updates.

![Star the repo](https://kestra.io/star.gif)
