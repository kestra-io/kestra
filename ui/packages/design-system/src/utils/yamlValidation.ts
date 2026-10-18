import {parseDocument, isMap, isSeq, Scalar, YAMLMap} from "yaml"

export interface EditorMarker {
    taskId: string;
    message: string;
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
    severity: "error" | "warning" | "info";
}

const TASK_CONTAINER_KEYS = new Set([
    "tasks",
    "errors",
    "finally",
    "afterExecution",
    "then",
    "else",
    "defaults",
])

export function findDuplicateTaskIds(yamlSource: string): EditorMarker[] {
    const markers: EditorMarker[] = []

    try {
        const doc = parseDocument(yamlSource, {keepSourceTokens: true})
        const seenIds = new Map<string, {line: number; col: number}>()

        const processTask = (taskMap: YAMLMap) => {
            const idPair = taskMap.items.find(
                (pair) => (pair.key as Scalar)?.value === "id",
            )

            if (idPair && idPair.value) {
                const idNode = idPair.value as Scalar
                if (idNode.value !== null && idNode.value !== undefined) {
                    const idValue = String(idNode.value)

                    if (idValue && idNode.range) {
                        const pos = getLineCol(yamlSource, idNode.range[0])

                        if (seenIds.has(idValue)) {
                            const endPos = getLineCol(yamlSource, idNode.range[1])
                            markers.push({
                                taskId: idValue,
                                message: `Duplicate task id: "${idValue}"`,
                                startLineNumber: pos.line,
                                startColumn: pos.col,
                                endLineNumber: endPos.line,
                                endColumn: endPos.col,
                                severity: "error",
                            })
                        } else {
                            seenIds.set(idValue, pos)
                        }
                    }
                }
            }

            for (const pair of taskMap.items) {
                const key = (pair.key as Scalar)?.value
                if (typeof key !== "string") continue

                if (TASK_CONTAINER_KEYS.has(key)) {
                    processTaskContainer(pair.value)
                } else if (key === "cases" && isMap(pair.value)) {
                    for (const casePair of (pair.value as YAMLMap).items) {
                        processTaskContainer(casePair.value)
                    }
                } else if (key === "onPause") {
                    processTaskContainer(pair.value)
                }
            }
        }

        const processTaskItem = (item: YAMLMap) => {
            const taskPair = item.items.find(
                (pair) => (pair.key as Scalar)?.value === "task",
            )
            if (taskPair && isMap(taskPair.value)) {
                processTask(taskPair.value as YAMLMap)
            } else {
                processTask(item)
            }
        }

        const processTaskContainer = (container: unknown) => {
            if (isSeq(container)) {
                for (const item of container.items) {
                    if (isMap(item)) {
                        processTaskItem(item as YAMLMap)
                    }
                }
            } else if (isMap(container)) {
                processTaskItem(container as YAMLMap)
            }
        }

        const contents = doc.contents
        if (isMap(contents)) {
            for (const pair of contents.items) {
                const key = (pair.key as Scalar)?.value
                if (typeof key === "string" && TASK_CONTAINER_KEYS.has(key)) {
                    processTaskContainer(pair.value)
                }
            }
        }
    } catch {
        // syntax errors handled elsewhere
    }

    return markers
}

function getLineCol(source: string, offset: number): {line: number; col: number} {
    let line = 1
    let col = 1
    for (let i = 0; i < offset && i < source.length; i++) {
        if (source[i] === "\n") {
            line++
            col = 1
        } else {
            col++
        }
    }
    return {line, col}
}
