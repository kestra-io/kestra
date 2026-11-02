import {z} from "zod"
import {defineArtifactSlot} from "./define-artifact-slot"
import {propsSchema as topologyDetailsProps} from "./topology-details"

export const propsSchema = topologyDetailsProps.omit({progress: true})

// The artifact module may also export `getSubtitle({task, execution, fetchOutputs, fetchMetrics})`,
// returning the line shown under its title in the Artifacts tab, e.g. "8 nodes · 8 dependencies".
// A task type ships several of these: each is listed in the manifest as `execution-artifact/<id>`
// and exposed by the plugin UI as `./<TaskRoot>/execution-artifact/<id>`.
export default defineArtifactSlot(() => ({
    key: "execution-artifact",
    props: propsSchema,
    manifest: z.object({
        // Label of the artifact in the Artifacts tab.
        title: z.string(),
        description: z.string().optional(),
    }),
}))
