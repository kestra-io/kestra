import type {JSONProperty} from "../plugins/schema/utils/schemaUtils"
import type {RootJsonSchema} from "../../stores/plugins"

interface MetricTask {
    id: string;
    type: string;
    tasks?: MetricTask[];
    errors?: MetricTask[];
}

/**
 * Collects task IDs and their plugin types, including nested tasks and errors.
 */
export function collectTaskTypes(tasks: MetricTask[] = []): Map<string, string> {
    const taskTypes = new Map<string, string>()

    const visit = (taskList: MetricTask[]) => {
        for (const task of taskList) {
            taskTypes.set(task.id, task.type)

            if (task.tasks) {
                visit(task.tasks)
            }

            if (task.errors) {
                visit(task.errors)
            }
        }
    }

    visit(tasks)
    return taskTypes
}

/**
 * Finds the description for a metric by its name.
 */
export function findMetricDescription(
    metrics: JSONProperty[] | undefined,
    metricName: string,
): string | undefined {
    return metrics?.find(metric => metric.name === metricName)?.description || undefined
}

/**
 * Extracts metric definitions from a plugin schema.
 */
export function getSchemaMetrics(
    schema: RootJsonSchema,
    pluginType: string,
): JSONProperty[] | undefined {
    const rootProperties = (
        schema as RootJsonSchema & {
            properties?: {$metrics?: JSONProperty[]};
        }
    ).properties

    if (rootProperties?.$metrics) {
        return rootProperties.$metrics
    }

    const definitionKey = schema.$ref.replace(/^#\/definitions\//, "")
    const definition = schema.definitions[definitionKey]
        ?? schema.definitions[pluginType]

    const properties = definition?.properties as
        | {$metrics?: JSONProperty[]}
        | undefined

    return properties?.$metrics
}
