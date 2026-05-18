import jsonApiToDuckdb from "./json-api-to-duckdb.yaml?raw"
import ansibleInstallNginx from "./ansible-install-nginx.yaml?raw"
import buildDbtPipeline from "./build-dbt-pipeline.yaml?raw"
import convertCsvToExcel from "./convert-csv-to-excel.yaml?raw"
import etlWorkflow from "./etl-workflow.yaml?raw"
import manualApproval from "./manual-approval.yaml?raw"
import microservicesApis from "./microservices-apis.yaml?raw"
import runDockerImage from "./run-docker-image.yaml?raw"
import scheduledPdfReports from "./scheduled-pdf-reports.yaml?raw"
import weeklySalesKpisToSlack from "./weekly-sales-kpis-to-slack.yaml?raw"

export const flowExamples = {
    jsonApiToDuckdb: {
        flow: jsonApiToDuckdb,
        labelKey: "welcome_copilot.flows.jsonApiToDuckdb.label",
        promptKey: "welcome_copilot.flows.jsonApiToDuckdb.prompt",
    },
    installNginxViaAnsible: {
        flow: ansibleInstallNginx,
        labelKey: "welcome_copilot.flows.installNginxViaAnsible.label",
        promptKey: "welcome_copilot.flows.installNginxViaAnsible.prompt",
    },
    buildDbtPipeline: {
        flow: buildDbtPipeline,
        labelKey: "welcome_copilot.flows.buildDbtPipeline.label",
        promptKey: "welcome_copilot.flows.buildDbtPipeline.prompt",
    },
    etlWorkflow: {
        flow: etlWorkflow,
        labelKey: "welcome_copilot.flows.etlWorkflow.label",
        promptKey: "welcome_copilot.flows.etlWorkflow.prompt",
    },
    microservicesApis: {
        flow: microservicesApis,
        labelKey: "welcome_copilot.flows.microservicesApis.label",
        promptKey: "welcome_copilot.flows.microservicesApis.prompt",
    },
    buildDockerImageAndRunIt: {
        flow: runDockerImage,
        labelKey: "welcome_copilot.flows.buildDockerImageAndRunIt.label",
        promptKey: "welcome_copilot.flows.buildDockerImageAndRunIt.prompt",
    },
    manualApproval: {
        flow: manualApproval,
        labelKey: "welcome_copilot.flows.manualApproval.label",
        promptKey: "welcome_copilot.flows.manualApproval.prompt",
    },
    convertCsvToExcel: {
        flow: convertCsvToExcel,
        labelKey: "welcome_copilot.flows.convertCsvToExcel.label",
        promptKey: "welcome_copilot.flows.convertCsvToExcel.prompt",
    },
    scheduledPdfReports: {
        flow: scheduledPdfReports,
        labelKey: "welcome_copilot.flows.scheduledPdfReports.label",
        promptKey: "welcome_copilot.flows.scheduledPdfReports.prompt",
    },
    weeklySalesKpisToSlack: {
        flow: weeklySalesKpisToSlack,
        labelKey: "welcome_copilot.flows.weeklySalesKpisToSlack.label",
        promptKey: "welcome_copilot.flows.weeklySalesKpisToSlack.prompt",
    },
} as const

export const labels: Array<keyof typeof flowExamples> = [
    "jsonApiToDuckdb",
    "installNginxViaAnsible",
    "buildDbtPipeline",
    "etlWorkflow",
    "microservicesApis",
    "buildDockerImageAndRunIt",
    "manualApproval",
    "convertCsvToExcel",
    "scheduledPdfReports",
    "weeklySalesKpisToSlack",
]
