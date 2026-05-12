import {propNames as topologyDetailsPropNames, type Props as TopologyDetailsProps} from "./topologyDetails"

export const KnownSlotsPropNames = {
    "topology-details": topologyDetailsPropNames,
    "topology-task-drawer": topologyDetailsPropNames,
} as const

export type KnownSlotProps = {
    "topology-details": TopologyDetailsProps
    "topology-task-drawer": TopologyDetailsProps
}
