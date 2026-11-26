// The platform and the tools that wrote the asset; a write only adds to it, so a tool leaves the list only by hand.
export const TOOLS_METADATA_KEY = "system.tools"

// The plugin groups whose icon stands for a tool or platform, as the plugins spell them in system.tools; the first installed one wins.
const TOOL_ICON_GROUPS: Record<string, string[]> = {
    "Terraform": ["io.kestra.plugin.ee.terraform", "io.kestra.plugin.terraform"],
    "OpenTofu": ["io.kestra.plugin.ee.opentofu", "io.kestra.plugin.opentofu"],
    "Ansible": ["io.kestra.plugin.ee.ansible", "io.kestra.plugin.ansible"],
    "dbt": ["io.kestra.plugin.dbt"],
    "GCP": ["io.kestra.plugin.gcp"],
    "AWS": ["io.kestra.plugin.aws"],
    "Azure": ["io.kestra.plugin.azure"],
    "Snowflake": ["io.kestra.plugin.jdbc.snowflake"],
    "PostgreSQL": ["io.kestra.plugin.jdbc.postgresql"],
    "MySQL": ["io.kestra.plugin.jdbc.mysql"],
    "Redshift": ["io.kestra.plugin.jdbc.redshift"],
    "ClickHouse": ["io.kestra.plugin.jdbc.clickhouse"],
    "DuckDB": ["io.kestra.plugin.jdbc.duckdb"],
    "SQL Server": ["io.kestra.plugin.jdbc.sqlserver"],
    "Trino": ["io.kestra.plugin.jdbc.trino"],
    "Databricks": ["io.kestra.plugin.databricks"],
    "BigQuery": ["io.kestra.plugin.gcp.bigquery", "io.kestra.plugin.gcp"],
}

export function assetTools(metadata: Record<string, unknown> | undefined): string[] {
    const raw = metadata?.[TOOLS_METADATA_KEY]
    return Array.isArray(raw) ? raw.filter((tool): tool is string => typeof tool === "string" && tool.trim() !== "") : []
}

export function toolIconGroup(tool: string, installedGroups: Record<string, unknown>): string | undefined {
    return (Object.prototype.hasOwnProperty.call(TOOL_ICON_GROUPS, tool) ? TOOL_ICON_GROUPS[tool] : undefined)?.find(group => group in installedGroups)
}
