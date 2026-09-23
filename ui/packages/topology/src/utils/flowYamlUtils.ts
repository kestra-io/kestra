// Public surface of the flow YAML helpers, kept as one entry point so that
// `@kestra-io/topology/flow-yaml-utils` consumers are unaffected by the split.

export {parse, stringify, pairsToMap} from "./yaml/serialization.ts"
export {parsePath, appendKeyToPath, joinPath} from "./yaml/paths.ts"
export {getPathFromSectionAndId, extractBlock, extractBlockWithPath, replaceBlockWithPath, swapBlocks, pruneEmptySequences, insertBlockWithPath, deleteBlock, flowHaveTasks, isParentChildrenRelation} from "./yaml/blocks.ts"
export {replaceIdAndNamespace, updateMetadata, getMetadata, deleteMetadata} from "./yaml/metadata.ts"
export {extractFieldFromMaps, extractTypedBlocks, extractTypedBlocksWithMeta} from "./yaml/fields.ts"
export type {FieldMatch, TypedBlock, FlowSourceData} from "./yaml/fields.ts"
export {getTypeAtPosition, getVersionAtPosition, localizeElementAtIndex, getAllCharts, getChartAtPosition, getTasksLines} from "./yaml/positions.ts"
export type {YamlElement} from "./yaml/positions.ts"
