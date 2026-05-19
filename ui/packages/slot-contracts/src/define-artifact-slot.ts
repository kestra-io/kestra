import {z} from "zod"

/**
 * Bundles a slot's props schema and manifest additional-properties schema
 * into a single definition object, deriving `propNames` automatically.
 *
 * Usage:
 * ```ts
 * const slot = defineArtifactSlot({
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
    TProps extends z.ZodRawShape,
    TManifest extends z.ZodRawShape,
>(config: { key: TKey; props: z.ZodObject<TProps>; manifest: z.ZodObject<TManifest> }) {
    return {
        key: config.key,
        propsSchema: config.props,
        propNames: Object.keys(config.props.shape) as Array<
            keyof z.infer<z.ZodObject<TProps>>
        >,
        manifestSchema: config.manifest,
    }
}
