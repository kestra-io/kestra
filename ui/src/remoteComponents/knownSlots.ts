import {propNames as topologyDetailsPropNames, Props as TopologyDetailsProps} from "./topologyDetails"

export const KnownSlots = {
    "topology-details": topologyDetailsPropNames,
    "topology-task-drawer": topologyDetailsPropNames,
} as const

export type KnownSlotProps = {
    "topology-details": TopologyDetailsProps
    "topology-task-drawer": TopologyDetailsProps
}