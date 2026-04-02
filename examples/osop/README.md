# OSOP Workflow Example — Kestra ETL Pipeline

This directory contains a portable workflow definition for a typical **ETL pipeline** pattern, written in [OSOP](https://github.com/osop-org/osop-spec) format.

## What is OSOP?

**OSOP** (Open Standard for Orchestration Protocols) is a YAML-based workflow standard that describes multi-step processes — including data pipelines, CI/CD, and AI agent workflows — in a portable, tool-agnostic format. Think of it as "OpenAPI for workflows."

- Any tool can read and render an `.osop` file
- Workflows become shareable, diffable, and version-controllable
- No vendor lock-in: the same workflow runs across different orchestration engines

## Files

| File | Description |
|------|-------------|
| `etl-pipeline.osop` | Extract-validate-transform-load pipeline with parallel loading to PostgreSQL and BigQuery, plus error notifications |

## How to use

You can read the `.osop` file as plain YAML. To validate or visualize it:

```bash
# Validate the workflow
pip install osop
osop validate etl-pipeline.osop

# Generate a visual report
npx osop-report etl-pipeline.osop -o report.html
```

## Learn more

- [OSOP Spec](https://github.com/osop-org/osop-spec) — Full specification
- [OSOP Examples](https://github.com/osop-org/osop-examples) — 30+ workflow templates
- [Kestra Documentation](https://kestra.io/docs) — Kestra orchestration docs
