import {defineArtifactSlot} from "./define-artifact-slot"
import {propsSchema} from "./topology-task-drawer"
import {z} from "zod"

export default defineArtifactSlot(() => ({
    key: "topology-task-modal",
    props: propsSchema,
    manifest: z.object({}),
}))
