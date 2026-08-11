import {defineArtifactSlot} from "./define-artifact-slot"
import {propsSchema} from "./topology-details"


export default defineArtifactSlot((z) => ({
    key: "topology-task-drawer",
    props: propsSchema,
    manifest: z.object({}),
}))
