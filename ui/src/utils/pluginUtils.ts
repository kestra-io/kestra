const KNOWN_PUBLIC_PLUGINS = new Set([
    "aws", "kubernetes", "langchain4j", "sifflet", "gcp", "graphql", "fs", "jdbc", 
    "azure", "airbyte", "zendesk", "weaviate", "typesense", "transform", "tika", 
    "terraform", "surrealdb", "sqlmesh", "spark", "solace", "soda", "singer", 
    "servicenow", "serdes", "scripts", "redis", "pulsar", "powerbi", "opensearch", 
    "openai", "ollama", "notifications", "neo4j", "nats", "mqtt", "mongodb", 
    "modal", "minio", "meilisearch", "malloy", "linear", "ldap", "kafka", "jira", 
    "jenkins", "influxdb", "huggingface", "hubspot", "hightouch", "graalvm", 
    "googleworkspace", "github", "git", "fivetran", "elasticsearch", "docker", 
    "debezium", "dbt", "datahub", "dataform", "databricks", "crypto", "couchbase", 
    "compress", "cloudquery", "cassandra", "ansible", "amqp", "airflow", "camel", 
    "template", "template-maven", "notion", "quickwit"
]);

export const getPluginReleaseUrl = (pluginClass?: string): string | null => {
    const parts = pluginClass?.split(".") ?? [];
    if (parts.length < 4 || !parts.slice(0, 3).every((p, i) => p === ["io", "kestra", "plugin"][i])) return null;
    
    const pluginType = parts[3];
    return pluginType === "core" || pluginType.includes("ee") || !KNOWN_PUBLIC_PLUGINS.has(pluginType) 
        ? null 
        : `https://github.com/kestra-io/plugin-${pluginType}/releases`;
};
