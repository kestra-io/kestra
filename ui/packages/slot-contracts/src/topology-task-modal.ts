import {defineArtifactSlot} from "./define-artifact-slot"
import {propsSchema} from "./topology-details"
import {z} from "zod"

export default defineArtifactSlot(() => ({
    key: "topology-task-modal",
    props: propsSchema,
    manifest: z.object({}),
}))
