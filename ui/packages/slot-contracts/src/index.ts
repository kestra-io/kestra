import {z} from "zod"
import assetCardFooter from "./asset-card-footer"
import executionArtifact from "./execution-artifact"

const slots = [assetCardFooter, executionArtifact] as const

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