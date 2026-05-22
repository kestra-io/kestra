import type {Execution, Task} from "@kestra-io/kestra-sdk"
import {z} from "zod"
import {defineArtifactSlot} from "./define-artifact-slot"

export const propsSchema = z.object({
    taskType: z.string(),
    task: z.custom<Task>(),
    // Optional even though the LowCodeEditor only mounts the federated module
    // when `task.taskRunner` is set — keeping them optional aligns the prop
    // shape with the other slots so `KnownSlotProps[T]` stays structurally
    // compatible across the registry.
    taskRunnerType: z.string().optional(),
    taskRunner: z.record(z.string(), z.unknown()).optional(),
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
