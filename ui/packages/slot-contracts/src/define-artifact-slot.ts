import {z as zImported} from "zod"

/**
 * Bundles a slot's props schema and manifest additional-properties schema
 * into a single definition object, deriving `propNames` automatically.
 *
 * Usage:
 * ```ts
 * const slot = defineArtifactSlot({
 *   key: "example-slot",
 *   props: z.object({ ... }),
 *   manifest: z.object({ ... }),
 * });
 *
 * export const propsSchema = slot.propsSchema;
 * export type Props = z.infer<typeof propsSchema>;
 * export const propNames = slot.propNames;
 * export type ManifestAdditionalProperties = z.infer<typeof slot.manifestSchema>;
 * ```
 */
export function defineArtifactSlot<
    TKey extends string,
    TProps extends  zImported.ZodRawShape,
    TManifest extends zImported.ZodRawShape,
>(config: (z: typeof zImported) => { 
    key: TKey; 
    props: zImported.ZodObject<TProps>; 
    manifest: zImported.ZodObject<TManifest> 
}) {
    const result = config(zImported)
    return {
        key: result.key,
        propsSchema: result.props,
        propNames: Object.keys(result.props.shape) as Array<
            keyof zImported.infer<zImported.ZodObject<TProps>>
        >,
        manifestSchema: result.manifest,
    }
}
