import {z} from "zod"
import {defineArtifactSlot} from "./define-artifact-slot"
import {propsSchema} from "./topologyDetails"


export default defineArtifactSlot({
    key: "topology-task-drawer",
    props: propsSchema,
    manifest: z.object(),
})
