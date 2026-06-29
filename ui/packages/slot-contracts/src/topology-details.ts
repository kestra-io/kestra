import type {Execution, LogEntry, MetricEntry, Task} from "@kestra-io/kestra-sdk"
import {z} from "zod"
import {defineArtifactSlot} from "./define-artifact-slot"

export const propsSchema = z.object({
    taskType: z.string(),
    task: z.custom<Task>(),
    execution: z.custom<Execution>().optional(),
    namespace: z.string().optional(),
    flowId: z.string().optional(),
    metrics: z.custom<MetricEntry>().array(),
    // Live log lines for the selected task, streamed while the execution is running.
    // Lets a slot follow per-step progress in real time (metrics/outputs only exist post-completion).
    logs: z.custom<LogEntry>().array().optional(),
})

export default defineArtifactSlot(() => ({
    key: "topology-details",
    props: propsSchema,
    manifest: z.object({
        heightWithExecution: z.number().optional(),
        height: z.number().optional(),
    }),
}))
