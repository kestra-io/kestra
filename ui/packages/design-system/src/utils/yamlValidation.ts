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

// The containers the backend's FlowValidator walks, through Flow.allTasks() and each flowable's allChildTasks().
const TASK_LIST_KEYS = new Set(["tasks", "errors", "finally", "afterExecution", "then", "else", "defaults", "onWait", "onApprove", "onDeny"])

export function findDuplicateTaskIds(yamlSource: string): EditorMarker[] {
    const markers: EditorMarker[] = []

    try {
        const doc = parseDocument(yamlSource, {keepSourceTokens: true})
        const seenIds = new Map<string, {line: number; col: number}>()

        const processTask = (task: YAMLMap) => {
            const idPair = task.items.find(
                (pair) => (pair.key as Scalar)?.value === "id",
            )

            if (idPair && idPair.value) {
                const idNode = idPair.value as Scalar
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

            processChildren(task)
        }

        const processTasks = (tasks: unknown) => {
            if (!isSeq(tasks)) return

            for (const item of tasks.items) {
                if (!isMap(item)) continue

                const dagTask = item.get("task")
                processTask(!item.has("id") && isMap(dagTask) ? dagTask : item)
            }
        }

        const processChildren = (node: YAMLMap) => {
            for (const pair of node.items) {
                const key = (pair.key as Scalar)?.value
                if (typeof key === "string" && TASK_LIST_KEYS.has(key)) {
                    processTasks(pair.value)
                } else if (key === "cases" && isMap(pair.value)) {
                    for (const casePair of pair.value.items) {
                        processTasks(casePair.value)
                    }
                } else if (key === "onPause" && isMap(pair.value)) {
                    processTask(pair.value)
                }
            }
        }

        if (isMap(doc.contents)) {
            processChildren(doc.contents)
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
