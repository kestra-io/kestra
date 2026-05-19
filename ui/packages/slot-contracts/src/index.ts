import {z} from "zod"
import topologyDetails from "./topology-details"
import topologyTaskDrawer from "./topology-task-drawer"

const slots = [topologyDetails, topologyTaskDrawer] as const

type SlotTuple = typeof slots;
type ByKey<T extends readonly { key: string }[]> = {
    [S in T[number] as S["key"]]: S;
};
type Registry = ByKey<SlotTuple>;

export const KnownSlotsPropNames = Object.fromEntries(
    slots.map((slot) => [slot.key, slot.propNames]),
) as { [K in keyof Registry]: Registry[K]["propNames"] }

export type KnownSlotProps = {
    [K in keyof Registry]: z.infer<Registry[K]["propsSchema"]>;
};

export type ManifestsRegistry = {
    [K in keyof Registry]?: z.infer<Registry[K]["manifestSchema"]>;
};