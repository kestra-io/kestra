import type {Execution, MetricEntry, Task} from "@kestra-io/kestra-sdk"
import {z} from "zod"
import {defineArtifactSlot} from "./define-artifact-slot"

export const propsSchema = z.object({
    taskType: z.string(),
    task: z.custom<Task>(),
    execution: z.custom<Execution>().optional(),
    namespace: z.string().optional(),
    flowId: z.string().optional(),
    metrics: z.custom<MetricEntry>().array(),
})

export default defineArtifactSlot(() => ({
    key: "topology-details",
    props: propsSchema,
    manifest: z.object({
        heightWithExecution: z.number().optional(),
        height: z.number().optional(),
    }),
}))
