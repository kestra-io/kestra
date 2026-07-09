import type {Execution, MetricEntry, Task} from "@kestra-io/kestra-sdk"
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
    task: z.custom<Task>(),
    execution: z.custom<Execution>().optional(),
    namespace: z.string().optional(),
    flowId: z.string().optional(),
    // Current (possibly unsaved) flow source, so plugins can resolve draft expressions for display.
    source: z.string().optional(),
    metrics: z.custom<MetricEntry>().array(),
    progress: progressEventSchema.array(),
})

export default defineArtifactSlot(() => ({
    key: "topology-details",
    props: propsSchema,
    manifest: z.object({
        heightWithExecution: z.number().optional(),
        height: z.number().optional(),
    }),
}))
