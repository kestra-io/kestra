import {test, expect} from "vitest"
import * as barrel from "../../../../src/utils/flowYamlUtils.ts"

// The barrel is the package's `flow-yaml-utils` subpath: dropping a re-export
// during a refactor breaks consumers without failing any per-module test.
const EXPECTED = [
    "appendKeyToPath",
    "deleteBlock",
    "deleteMetadata",
    "extractBlock",
    "extractBlockWithPath",
    "extractFieldFromMaps",
    "extractTypedBlocks",
    "extractTypedBlocksWithMeta",
    "flowHaveTasks",
    "getAllCharts",
    "getChartAtPosition",
    "getMetadata",
    "getPathFromSectionAndId",
    "getTasksLines",
    "getTypeAtPosition",
    "getVersionAtPosition",
    "insertBlockWithPath",
    "isParentChildrenRelation",
    "joinPath",
    "localizeElementAtIndex",
    "pairsToMap",
    "parse",
    "parsePath",
    "pruneEmptySequences",
    "replaceBlockWithPath",
    "replaceIdAndNamespace",
    "stringify",
    "swapBlocks",
    "updateMetadata",
]

test("re-exports every public helper, and nothing extra", () => {
    expect(Object.keys(barrel).sort()).toEqual(EXPECTED)
})

test("re-exports are callable, not just present", () => {
    for (const name of EXPECTED) {
        expect(typeof barrel[name as keyof typeof barrel], name).toBe("function")
    }
})
