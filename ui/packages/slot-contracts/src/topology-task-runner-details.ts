import type {Execution, Task} from "@kestra-io/kestra-sdk"
import {z} from "zod"
import {defineArtifactSlot} from "./define-artifact-slot"

export const propsSchema = z.object({
    taskType: z.string(),
    taskRunnerType: z.string(),
    task: z.custom<Task>(),
    taskRunner: z.record(z.string(), z.unknown()),
    execution: z.custom<Execution>().optional(),
    taskRun: z.record(z.string(), z.unknown()).optional(),
    taskRunnerDetail: z.record(z.string(), z.unknown()).optional(),
    namespace: z.string().optional(),
    flowId: z.string().optional(),
})

export default defineArtifactSlot(() => ({
    key: "topology-task-runner-details",
    props: propsSchema,
    manifest: z.object({
        heightWithExecution: z.number().optional(),
        height: z.number().optional(),
    }),
}))
