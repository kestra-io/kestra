import {z} from "zod"
import {defineArtifactSlot} from "./define-artifact-slot"

export const propsSchema = z.object({
    // The asset's id, as shown in the dependencies graph.
    assetId: z.string(),
    assetType: z.string().optional(),
    // Everything the producing task attached to the asset, e.g. the keys a plugin emits through `Asset.metadata`.
    metadata: z.record(z.string(), z.unknown()),
    // Current tenant, so an artifact bundling its own SDK copy doesn't have to guess it.
    tenant: z.string().optional(),
})

// Resolved by the task type that produced the asset, like the other slots resolve by task type.
export default defineArtifactSlot(() => ({
    key: "asset-card-footer",
    props: propsSchema,
    manifest: z.object({
        // Extra pixels the footer adds below a card. The graph reserves it for every card at once,
        // so rows stay aligned; a graph with no footer keeps the compact card.
        footerHeight: z.number().optional(),
    }),
}))
