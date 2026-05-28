{{/*
Expand the name of the chart.
*/}}
{{- define "kestra.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Create a default fully qualified app name.
We truncate at 63 chars because some Kubernetes name fields are limited to this (by the DNS naming spec).
If release name contains chart name it will be used as a full name.
*/}}
{{- define "kestra.fullname" -}}
{{- if .Values.fullnameOverride }}
{{- .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- $name := default .Chart.Name .Values.nameOverride }}
{{- if contains $name .Release.Name }}
{{- .Release.Name | trunc 63 | trimSuffix "-" }}
{{- else }}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" }}
{{- end }}
{{- end }}
{{- end }}

{{/*
Create chart name and version as used by the chart label.
*/}}
{{- define "kestra.chart" -}}
{{- printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/*
Common labels
*/}}
{{- define "kestra.labels" -}}
app.kubernetes.io/name: {{ include "kestra.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- if .Chart.AppVersion }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
{{- end }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ include "kestra.chart" . }}
{{- end -}}

{{/*
Selectors labels
*/}}
{{- define "kestra.selectorLabels" -}}
app.kubernetes.io/name: {{ include "kestra.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{/*
Create the name of the service account to use
*/}}
{{- define "kestra.serviceAccountName" -}}
{{- if .Values.serviceAccount.create }}
{{- default (include "kestra.fullname" .) .Values.serviceAccount.name }}
{{- else }}
{{- default "default" .Values.serviceAccount.name }}
{{- end }}
{{- end }}

{{/*
Create the content of MICRONAUT_CONFIG_FILES
*/}}
{{- define "kestra.micronautConfigFiles" -}}
{{- $files := list "/app/confs/_default.yml" }}
{{- range .Values.configurations.configmaps }}
  {{- $files = append $files (printf "/app/confs/%s" .key) }}
{{- end }}
{{- range .Values.configurations.secrets }}
  {{- $files = append $files (printf "/app/secrets/%s" .key) }}
{{- end }}
{{- join "," $files }}
{{- end }}

{{/*
Renders a value that contains template.
Usage:
{{ include "kestra.extra-render" ( dict "value" .Values.path.to.the.Value "context" $) }}
*/}}
{{- define "kestra.extra-render" -}}
    {{- if typeIs "string" .value }}
        {{- tpl .value .context }}
    {{- else }}
        {{- tpl (.value | toYaml) .context }}
    {{- end }}
{{- end -}}


{{- define "kestra.jvmOpts" -}}
{{- $common := .common -}}
{{- $content := .content -}}

{{- /* extraOpts : common then override per component */ -}}
{{- $commonJvm := default (dict) $common.jvm -}}
{{- $contentJvm := default (dict) $content.jvm -}}
{{- $extraCommon := default "" $commonJvm.extraOpts -}}
{{- $extraContent := default "" $contentJvm.extraOpts -}}
{{- $extra := default $extraCommon $extraContent -}}

{{- $active := include "kestra.activeProcessorCount" (dict "common" $common "content" $content) | trim -}}

{{- if and $active $extra -}}
-XX:ActiveProcessorCount={{ $active }} {{ $extra }}
{{- else if $active -}}
-XX:ActiveProcessorCount={{ $active }}
{{- else -}}
{{- $extra -}}
{{- end -}}
{{- end -}}

{{- define "kestra.activeProcessorCount" -}}
{{- $common := .common -}}
{{- $content := .content -}}

{{- /* JVM config: common, then override per component */ -}}
{{- $commonJvm := default (dict) $common.jvm -}}
{{- $contentJvm := default (dict) $content.jvm -}}

{{- $forceCommon := default (dict) $commonJvm.forceActiveProcessors -}}
{{- $forceContent := default (dict) $contentJvm.forceActiveProcessors -}}

{{- /* If the component has a config, take it, otherwise fall back to common */ -}}
{{- $forceCfg := ternary $forceContent $forceCommon (not (empty $forceContent)) -}}

{{- if not $forceCfg.enabled -}}
{{- /* not activated then nothing */ -}}
{{- else -}}

  {{- if eq $forceCfg.count "value" -}}
    {{- printf "%d" (int $forceCfg.value) -}}

  {{- else if eq $forceCfg.count "auto" -}}

    {{- /* resources : common then override per component */ -}}
    {{- $resCommon := default (dict) $common.resources -}}
    {{- $resContent := default (dict) $content.resources -}}
    {{- $res := default $resCommon $resContent -}}
    {{- $limits := default (dict) $res.limits -}}
    {{- $cpuLimit := default "" $limits.cpu -}}

    {{- if ne $cpuLimit "" -}}
      {{- /* Case 1250m, 500m, etc. */ -}}
      {{- if hasSuffix "m" $cpuLimit -}}
        {{- $milli := trimSuffix "m" $cpuLimit | int -}}
        {{- $cpus := div $milli 1000 -}}
        {{- if lt $cpus 1 }}{{- $cpus = 1 }}{{- end -}}
        {{- printf "%d" $cpus -}}

      {{- /* Case 0.5, 1.5, etc. */ -}}
      {{- else if contains "." $cpuLimit -}}
        {{- $f := float64 $cpuLimit -}}
        {{- $cpus := ceil $f | int -}}
        {{- if lt $cpus 1 }}{{- $cpus = 1 }}{{- end -}}
        {{- printf "%d" $cpus -}}

      {{- /* Case "1", "2", "8", etc. */ -}}
      {{- else -}}
        {{- $cpus := $cpuLimit | int -}}
        {{- if lt $cpus 1 }}{{- $cpus = 1 }}{{- end -}}
        {{- printf "%d" $cpus -}}
      {{- end -}}
    {{- end -}}

  {{- end -}}
{{- end -}}
{{- end -}}
