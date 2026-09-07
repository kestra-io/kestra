import type {Execution, PagedResultsMetricEntry, Task} from "@kestra-io/kestra-sdk"
import {z} from "zod"
import {defineArtifactSlot} from "./define-artifact-slot"

export const progressEventSchema = z.object({
    taskId: z.string(),
    taskRunId: z.string(),
    step: z.string(),
    timestamp: z.string(),
})

export const propsSchema = z.object({
    taskType: z.string(),
    // The graph node's task, merged with the same task parsed out of `source`: an execution graph
    // is served through forExecution(), which strips properties an artifact needs to render.
    task: z.custom<Task>(),
    execution: z.custom<Execution>().optional(),
    namespace: z.string().optional(),
    flowId: z.string().optional(),
    // Current tenant, so an artifact bundling its own SDK copy doesn't have to guess it.
    tenant: z.string().optional(),
    // Current (possibly unsaved) flow source, so plugins can resolve draft expressions for display.
    source: z.string().optional(),
    progress: progressEventSchema.array(),
    // Outputs of this task's current task run, fetched on call so an artifact that doesn't render
    // them costs no request. A looped task has one run per iteration; pass `taskRunId` to read
    // another. Resolves to `{}` outside an execution.
    fetchOutputs: z.custom<(query?: {
        taskRunId?: string
    }) => Promise<Record<string, unknown>>>().optional(),
    // This task's metrics in the current execution — searchByExecution with the execution, the
    // tenant and this task already bound. A looped task has one entry set per iteration; pass
    // `taskRunId` to narrow to one. Resolves to an empty page outside an execution.
    fetchMetrics: z.custom<(query?: {
        page?: number
        size?: number
        sort?: string
        taskRunId?: string
    }) => Promise<PagedResultsMetricEntry>>().optional(),
})

export default defineArtifactSlot(() => ({
    key: "topology-details",
    props: propsSchema,
    manifest: z.object({
        heightWithExecution: z.number().optional(),
        height: z.number().optional(),
    }),
}))
