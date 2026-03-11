import initial from "./initial.yaml?raw";

import ansibleInstallNginx from "./ansible-install-nginx.yaml?raw";
import buildDbtPipeline from "./build-dbt-pipeline.yaml?raw";
import convertCsvToExcel from "./convert-csv-to-excel.yaml?raw";
import etlWorkflow from "./etl-workflow.yaml?raw";
import manualApproval from "./manual-approval.yaml?raw";
import microservicesApis from "./microservices-apis.yaml?raw";
import runDockerImage from "./run-docker-image.yaml?raw";
import scheduledPdfReports from "./scheduled-pdf-reports.yaml?raw";
import weeklySalesKpisToSlack from "./weekly-sales-kpis-to-slack.yaml?raw";

export const flows = {
    initial,

    "Install Nginx via Ansible": ansibleInstallNginx,
    "Build a dbt pipeline": buildDbtPipeline,
    "ETL Workflow": etlWorkflow,
    "Microservices & APIs": microservicesApis,
    "Build a Docker image and run it": runDockerImage,
    "Manual approval": manualApproval,
    "Convert a CSV to Excel": convertCsvToExcel,
    "Scheduled PDF reports": scheduledPdfReports,
    "Weekly Sales KPIs to Slack": weeklySalesKpisToSlack,
} as const;

export const labels = Object.keys(flows).filter((label) => label !== "initial") as Array<Exclude<keyof typeof flows, "initial">>;