import {
    propNames as topologyDetailsPropNames, 
    type Props as TopologyDetailsProps,
    type ManifestAdditionalProperties as TopologyDetailsManifestAdditionalProperties,
} from "./topologyDetails"

export const KnownSlotsPropNames = {
    "topology-details": topologyDetailsPropNames,
    "topology-task-drawer": topologyDetailsPropNames,
} as const

export type KnownSlotProps = {
    "topology-details": TopologyDetailsProps
    "topology-task-drawer": TopologyDetailsProps
}

export type ManifestsRegistry = {
    "topology-details"?: TopologyDetailsManifestAdditionalProperties
    "topology-task-drawer"?: {}
}
