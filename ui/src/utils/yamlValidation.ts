import {parseDocument, isMap, isSeq, Scalar, YAMLMap} from "yaml";

export interface EditorMarker {
    taskId: string;
    message: string;
    startLineNumber: number;
    startColumn: number;
    endLineNumber: number;
    endColumn: number;
    severity: "error" | "warning" | "info";
}

/**
 * Finds duplicate task IDs in a YAML flow source and returns markers for the duplicates.
 */
export function findDuplicateTaskIds(yamlSource: string): EditorMarker[] {
    const markers: EditorMarker[] = [];

    try {
        const doc = parseDocument(yamlSource, {keepSourceTokens: true});
        const seenIds = new Map<string, {line: number; col: number}>();

        const processTasks = (tasks: unknown) => {
            if (!isSeq(tasks)) return;

            for (const task of tasks.items) {
                if (!isMap(task)) continue;

                const idPair = (task as YAMLMap).items.find(
                    (pair) => (pair.key as Scalar)?.value === "id"
                );

                if (idPair && idPair.value) {
                    const idNode = idPair.value as Scalar;
                    const idValue = String(idNode.value);

                    if (idValue && idNode.range) {
                        const pos = getLineCol(yamlSource, idNode.range[0]);

                        if (seenIds.has(idValue)) {
                            // Found a duplicate - add marker
                            const endPos = getLineCol(yamlSource, idNode.range[1]);
                            markers.push({
                                taskId: idValue,
                                message: `Duplicate task id: "${idValue}"`,
                                startLineNumber: pos.line,
                                startColumn: pos.col,
                                endLineNumber: endPos.line,
                                endColumn: endPos.col,
                                severity: "error"
                            });
                        } else {
                            seenIds.set(idValue, pos);
                        }
                    }
                }

                // Recursively check nested tasks (Sequential, Parallel, etc.)
                const taskMap = task as YAMLMap;
                for (const pair of taskMap.items) {
                    const key = (pair.key as Scalar)?.value;
                    if (key === "tasks" || key === "errors" || key === "finally") {
                        processTasks(pair.value);
                    }
                }
            }
        };

        // Process top-level tasks, errors, and finally
        const contents = doc.contents;
        if (isMap(contents)) {
            for (const pair of contents.items) {
                const key = (pair.key as Scalar)?.value;
                if (key === "tasks" || key === "errors" || key === "finally") {
                    processTasks(pair.value);
                }
            }
        }
    } catch {
        // If YAML parsing fails, return empty (other validators handle syntax errors)
    }

    return markers;
}

function getLineCol(source: string, offset: number): {line: number; col: number} {
    let line = 1;
    let col = 1;
    for (let i = 0; i < offset && i < source.length; i++) {
        if (source[i] === "\n") {
            line++;
            col = 1;
        } else {
            col++;
        }
    }
    return {line, col};
}