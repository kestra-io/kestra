import JsYaml from "js-yaml";
import yaml, {Document, isMap, isPair, isSeq, LineCounter, Pair, Scalar, YAMLMap, YAMLSeq} from "yaml";
import _cloneDeep from "lodash/cloneDeep"
import {SECTIONS} from "./constants";

const yamlKeyCapture = "([^:\\n]+): *"
const indentAndYamlKeyCapture = new RegExp(`(( *)(?:${yamlKeyCapture})?)[^\\n]*?$`);

const TOSTRING_OPTIONS = {lineWidth: 0};

function index<T = any>(based: T[], value: T) {
    const index = based.indexOf(value);

    return index === -1 ? Number.MAX_SAFE_INTEGER : index;
}

function sort(value: Record<string, any>) {
    const SORT_FIELDS = [
        "id",
        "type",
        "namespace",
        "description",
        "revision",
        "inputs",
        "variables",
        "tasks",
        "errors",
        "triggers",
        "listeners",
    ];

    return Object.keys(value)
        .sort()
        .sort((a, b) => {
            return index(SORT_FIELDS, a) - index(SORT_FIELDS, b);
        });
}

function _transform(value: any) {
    if (value instanceof Array) {
        return value.map((r: any): any => {
            return _transform(r);
        })
    } else if (typeof (value) === "string" || value instanceof String) {
        // value = value
        //     .replaceAll(/\u00A0/g, " ");
        //
        // if (value.indexOf("\\n") >= 0) {
        //     return value.replaceAll("\\n", "\n") + "\n";
        // }

        return value;
    } else if (value instanceof Object) {
        return sort(value)
            .reduce((accumulator, r) => {
                if (value[r] !== undefined) {
                    accumulator[r] = _transform(value[r])
                }

                return accumulator;
            }, Object.create({}))
    }

    return value;
}

function yamlParseDocument(source: string): yaml.Document<any> {
    return yaml.parseDocument(source);
}

export default {
    stringify(value: any) {
        if (typeof value === "undefined") {
            return "";
        }

        if (value.deleted !== undefined) {
            delete value.deleted
        }

        return JsYaml.dump(_transform(_cloneDeep(value)), {
            lineWidth: -1,
            noCompatMode: true,
            quotingType: "\"",
        });
    },
    pairsToMap(pairs: any[]) {
        const map = new YAMLMap();
        if (!isPair(pairs?.[0])) {
            return map;
        }

        pairs.forEach(pair => {
            map.add(pair);
        });
        return map;
    },
    parse(item?: string, throwIfError = true) {
        if (item === undefined) {
            return undefined;
        }
        try {
            return JsYaml.load(item);
        } catch(e) {
            if (throwIfError) {
                throw e;
            }

            return undefined;
        }
    },
    extractTask(source: string, taskId: string) {
        const yamlDoc = yaml.parseDocument(source);
        const taskNode = this._extractTask(yamlDoc, taskId);
        return taskNode === undefined ? undefined : new yaml.Document(taskNode).toString(TOSTRING_OPTIONS);
    },
    _extractTask(yamlDoc: yaml.Document, taskId: string, callback?: (el: any) => any) {
        const find = (element: any): any => {
            if (!element) {
                return;
            }
            if (element instanceof YAMLMap) {
                if (element.get("type") !== undefined && taskId === element.get("id")) {
                    return callback ? callback(element) : element;
                }
            }
            if (element.items) {
                for (const [key, item] of element.items.entries()) {
                    let result;

                    if (item instanceof YAMLMap) {
                        result = find(item);
                    } else {
                        result = find(item.value);
                    }

                    if (result) {
                        if (callback) {
                            if (element instanceof YAMLMap) {
                                element.set(item.key.value, result);
                            } else {
                                element.items[key] = result;
                            }
                        }

                        if (!callback && result) {
                            return result
                        }
                    }
                }
            }
        }
        const result = find(yamlDoc.contents)

        if (result === undefined) {
            return undefined;
        }

        if (callback) {
            return new Document(result)
        } else {
            return new Document(result);
        }
    },
    replaceTaskInDocument(source: string, taskId: string, newContent: string) {
        const yamlDoc = yaml.parseDocument(source);
        const newItem = yamlDoc.createNode(yaml.parseDocument(newContent))

        this._extractTask(yamlDoc, taskId, (oldValue) => {
            this.replaceCommentInTask(oldValue, newItem)

            return newItem;
        })

        return yamlDoc.toString(TOSTRING_OPTIONS);
    },
    replaceCommentInTask(oldTask: any, newTask: any) {
        for (const oldProp of oldTask.items) {
            for (const newProp of newTask.items) {
                if (oldProp.key.value === newProp.key.value && newProp.value.comment === undefined) {
                    newProp.value.comment = oldProp.value.comment
                    break;
                }
            }
        }
    },
    nextDelimiterIndex(content: string, currentIndex: number) {
        if (currentIndex === content.length - 1) {
            return currentIndex;
        }

        const remainingContent = content.substring(currentIndex + 1);

        const nextDelimiterMatcher = remainingContent.match(/[ .}]/);
        if (nextDelimiterMatcher === null || nextDelimiterMatcher.index === undefined) {
            return content.length - 1;
        } else {
            return currentIndex + nextDelimiterMatcher.index;
        }
    }

    // Specify a source yaml doc, the field to extract recursively in every map of the doc and optionally a predicate to define which paths should be taken into account
    // parentPathPredicate will take a single argument which is the path of each parent property starting from the root doc (joined with ".")
    // "my.parent.task" will mean that the field was retrieved in my -> parent -> task path.
    ,
    extractFieldFromMaps(source: string, fieldName: string, parentPathPredicate = (_: any, __?: any) => true, valuePredicate = (_: any) => true) {
        const yamlDoc = yaml.parseDocument(source);
        const maps: {[fieldName:string]: any, range?: yaml.Range | null}[] = [];
        yaml.visit(yamlDoc, {
            Map(_, map, parent) {
                if (parentPathPredicate(parent.filter(p => yaml.isPair<{value: string}>(p)).map(p => p.key.value).join(".")) && map.items) {
                    for (const item of map.items as yaml.Pair<{value: string}, {value: any, items?: any[] }>[]) {
                        if (item.key.value === fieldName) {
                            const fieldValue = item.value?.value ?? item.value?.items;
                            if (valuePredicate(fieldValue)) {
                                maps.push({[fieldName]: fieldValue, range: map.range});
                            }
                        }
                    }
                }
            }
        })
        return maps;
    },
    extractMaps(source: string, fieldConditions: {[fieldName: string]: {present: boolean, populated: boolean}}): { parents: Record<string, any>[], key: string, map: Record<string, any>, range: FixedLengthArray<[number, number, number]> }[] {
        if (source.match(/^\s*{{/)) {
            return [];
        }

        const yamlDoc = yaml.parseDocument(source);
        const maps: {[fieldName:string]: any, range?: yaml.Range | null}[] = [];
        yaml.visit(yamlDoc, {
            Map(_, yamlMap, parents: any[]) {
                if (yamlMap.items) {
                    const map = yamlMap.toJS(yamlDoc);
                    for (const [fieldName, condition] of Object.entries(fieldConditions ?? {})) {
                        if (condition.present) {
                            if (map[fieldName] === undefined) {
                                return;
                            }

                            if (map[fieldName] === null) {
                                map[fieldName] = undefined;
                            }
                        }
                        if (condition.populated) {
                            if (map[fieldName] === undefined || map[fieldName] === null || map[fieldName] === "") {
                                return;
                            }
                        }
                    }

                    const parentKey = parents[parents.length - 1]?.key?.value;
                    const mapParents = parents.length > 1 ? parents.slice(0, parents.length - 1).filter(p => yaml.isMap(p)).map(p => p.toJS(yamlDoc)) : [];
                    maps.push({parents: mapParents, key: parentKey, map, range: yamlMap.range});
                }
            }
        });

        return maps;
    },
    localizeElementAtIndex(source: string, indexInSource: number): YamlElement {
        const tillCursor = source.substring(0, indexInSource);

        const indentAndYamlKey = this.extractIndentAndMaybeYamlKey(tillCursor);
        let {yamlKey} = indentAndYamlKey;
        const {indent} = indentAndYamlKey;
        // We search in previous keys to find the parent key
        let valueStartIndex;
        if (yamlKey === undefined) {
            const parentKeyExtract = this.getParentKeyByChildIndent(tillCursor, indent);
            yamlKey = parentKeyExtract?.key;
            valueStartIndex = parentKeyExtract?.valueStartIndex;
        } else {
            valueStartIndex = tillCursor.lastIndexOf(yamlKey + ":") + yamlKey.length + 1;
        }

        const yamlDoc = yaml.parseDocument(source);
        const elements = [];

        yaml.visit(yamlDoc, {
            Pair(_, pair, parents: any[]) {
                if (pair.value?.range !== undefined && pair.key.value === yamlKey) {
                    const beforeElement = source.substring(0, pair.value.range[0]);
                    elements.push({
                        parents: parents.filter(p => yaml.isMap(p)).map(p => p.toJS(yamlDoc)),
                        key: pair.key.value,
                        value: pair.value.toJS(yamlDoc),
                        range: [pair.value.range[0] - (beforeElement.length - beforeElement.replaceAll(/\s*$/g, "").length), ...pair.value.range.slice(1)]
                    });
                }
            }
        });

        const filter = elements.filter(map => map.range[0] <= valueStartIndex && valueStartIndex <= map.range[2]);
        return filter.sort((a, b) => b.range[0] - a.range[0])?.[0];
    },

    // Find map a cursor position, optionally filtering by a property name that the map must contain
    getMapAtPosition(source, position, fieldName = null) {
        const yamlDoc = yaml.parseDocument(source);
        const lineCounter = new yaml.LineCounter();
        yaml.parseDocument(source, {lineCounter});
        const cursorIndex = (lineCounter.lineStarts[position.lineNumber - 1] + position.column) - 1;
        let targetMap = null;
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.range[0] <= cursorIndex && map.range[1] >= cursorIndex) {
                    for (const item of map.items) {
                        if (fieldName === null || item.key.value === fieldName) {
                            targetMap = map;
                        }
                    }
                }
            }
        });

        return targetMap ? targetMap.toJSON() : null;
    },
    extractAllTypes(source: string, validTypes = []) {
        return this.extractFieldFromMaps(source, "type", undefined, value => validTypes.some(t => t === value));
    },
    getTaskType(source: string, position: {lineNumber: number, column: number}, validTypes: string[]) {
        const types = this.extractAllTypes(source, validTypes);

        const lineCounter = new LineCounter();
        yaml.parseDocument(source, {lineCounter});
        const cursorIndex = lineCounter.lineStarts[position.lineNumber - 1] + position.column;

        for(const type of types.reverse()) {
            if (type.range && cursorIndex >= type.range[0]) {
                return type.type;
            }
        }
        return null;
    },
    insertTask(source: string, taskId: string, newTask: string, insertPosition: "before" | "after" = "after") {
        const yamlDoc = yamlParseDocument(source);
        if(!yamlDoc) return
        const newTaskNode = yamlDoc.createNode(yaml.parseDocument(newTask))
        const tasksNode: any = yamlDoc.contents.items.find((e: any) => e.key.value === "tasks");
        if (!tasksNode || tasksNode?.value.value === null) {
            if (tasksNode) {
                yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(tasksNode), 1)
            }
            const taskList = new YAMLSeq()
            taskList.items.push(newTaskNode)
            const tasks = new Pair(new Scalar("tasks"), taskList)
            yamlDoc.contents.items.push(tasks as any)
            return yamlDoc.toString(TOSTRING_OPTIONS);
        }
        let added = false;
        yaml.visit(yamlDoc, {
            Seq(_, seq) {
                for (const map of seq.items) {
                    if (isMap(map)) {
                        if (added) {
                            return yaml.visit.BREAK;
                        }
                        if (map.get("id") === taskId) {
                            const index = seq.items.indexOf(map);
                            if (insertPosition === "before") {
                                if (index === 0) {
                                    seq.items.unshift(newTaskNode)
                                } else {
                                    seq.items.splice(index, 0, newTaskNode)
                                }
                            } else {
                                if (index === seq.items.length - 1) {
                                    seq.items.push(newTaskNode)
                                } else {
                                    seq.items.splice(index + 1, 0, newTaskNode)
                                }
                            }
                            added = true;
                            return seq
                        }
                    }
                }
            }
        })
        return yamlDoc.toString(TOSTRING_OPTIONS);
    },
    insertTrigger(source: string, triggerTask: string) {
        const yamlDoc = yamlParseDocument(source);
        const newTriggerNode = yamlDoc.createNode(yaml.parseDocument(triggerTask));
        let added = false;
        const triggers: any = yamlDoc.contents.items.find((item: any) => item.key.value === "triggers");
        if (triggers && triggers.value.items) {
            yaml.visit(yamlDoc, {
                Pair(_, pair) {
                    const pairTyped = pair as yaml.Pair<{value: string}, {items: any[]}>
                    if (added) {
                        return yaml.visit.BREAK;
                    }
                    if (pairTyped.key.value === "triggers") {
                        pairTyped.value?.items.push(newTriggerNode);
                        added = true;
                        return pair;
                    }
                }
            })
        } else {
            if (triggers) {
                yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(triggers), 1)
            }
            const triggersSeq = new yaml.YAMLSeq();
            triggersSeq.items.push(newTriggerNode);
            const newTriggers = new yaml.Pair(new yaml.Scalar("triggers"), triggersSeq);
            yamlDoc.contents.items.push(newTriggers as any);
        }
        return this.cleanMetadata(yamlDoc.toString(TOSTRING_OPTIONS));
    },
    insertError(source: string, errorTask: string) {
        const yamlDoc = yamlParseDocument(source)
        const newErrorNode = yamlDoc.createNode(yaml.parseDocument(errorTask));
        const errors: any = yamlDoc.contents.items.find((item: any) => item.key.value === "errors");
        if (errors && errors.value.items) {
            (yamlDoc.contents.items[yamlDoc.contents.items.indexOf(errors)] as any).value.items.push(newErrorNode);
        } else {
            if (errors) {
                yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(errors), 1)
            }
            const errorsSeq = new yaml.YAMLSeq();
            errorsSeq.items.push(newErrorNode);
            const newErrors = new yaml.Pair(new yaml.Scalar("errors"), errorsSeq);
            yamlDoc.contents.items.push(newErrors as any);
        }
        return this.cleanMetadata(yamlDoc.toString(TOSTRING_OPTIONS));
    },
    insertFinally(source: string, finallyTask: string) {
        const yamlDoc = yamlParseDocument(source);
        const newFinallyNode = yamlDoc.createNode(yaml.parseDocument(finallyTask));
        const items = yamlDoc.contents.items.find((item: any) => item.key.value === "finally");
        if (items && items.value.items) {
            yamlDoc.contents.items[yamlDoc.contents.items.indexOf(items)].value.items.push(newFinallyNode);
        } else {
            if (items) {
                yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(items), 1)
            }
            const finallySeq = new yaml.YAMLSeq();
            finallySeq.items.push(newFinallyNode);
            const newFinally = new yaml.Pair(new yaml.Scalar("finally"), finallySeq);
            yamlDoc.contents.items.push(newFinally);
        }
        return this.cleanMetadata(yamlDoc.toString(TOSTRING_OPTIONS));
    },
    insertErrorInFlowable(source: string, errorTask: string, flowableTask: string) {
        const yamlDoc = yaml.parseDocument(source);
        const newErrorNode = yamlDoc.createNode(yaml.parseDocument(errorTask));
        let added = false;
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (added) {
                    return yaml.visit.BREAK;
                }
                if (map.get("id") === flowableTask) {
                    if (map.items.find((item: any) => item.key.value === "errors")) {
                        (map.items.find((item: any) => item.key.value === "errors") as any).value.items.push(newErrorNode);
                    } else {
                        const errorsSeq = new yaml.YAMLSeq();
                        errorsSeq.items.push(newErrorNode);
                        const errors = new yaml.Pair(new yaml.Scalar("errors"), errorsSeq);
                        map.items.push(errors);
                    }
                    added = true;
                    return map;
                }
            }
        })
        return yamlDoc.toString(TOSTRING_OPTIONS);
    },
    deleteTask(source: string, taskId: string, section: string) {
        const inSection = section === SECTIONS.TASKS ? ["tasks", "errors"] : ["triggers"];
        const yamlDoc = yaml.parseDocument(source);
        yaml.visit(yamlDoc, {
            Pair(_, pair: any) {
                if (inSection.includes(pair.key.value)) {
                    yaml.visit(pair.value, {
                        Map(_, map) {
                            if (map.get("id") === taskId) {
                                return yaml.visit.REMOVE;
                            }
                        }
                    })
                }
            }
        })
        // delete empty sections
        yaml.visit(yamlDoc, {
            Pair(_, pair) {
                if (isSeq(pair.value) && pair.value.items.length === 0) {
                    return yaml.visit.REMOVE;
                }
            }
        })
        return yamlDoc.toString(TOSTRING_OPTIONS);
    },

    getFirstTask(source: string) {
        const parse = this.parse(source) as any;

        return parse && parse.tasks && parse.tasks[0].id;
    },

    getLastTask(source: string) {
        const parse = this.parse(source) as any;

        return parse && parse.tasks && parse.tasks[parse.tasks.length - 1].id;
    },

    checkTaskAlreadyExist(source: string, taskYaml: string) {
        const yamlDoc = yaml.parseDocument(source);
        const parsedTask: any = this.parse(taskYaml);
        let taskExist = false;
        yaml.visit(yamlDoc, {
            Pair(_, pair: any) {
                if (pair.key.value === "tasks") {
                    yaml.visit(pair, {
                        Map(_, map) {
                            if (map.get("id") === parsedTask.id) {
                                taskExist = true;
                                return yaml.visit.BREAK;
                            }
                        }
                    })
                }
            }
        })
        return taskExist ? parsedTask.id : null;
    },
    isParentChildrenRelation(source: string, task1: string, task2: string) {
        return this.isChildrenOf(source, task2, task1) || this.isChildrenOf(source, task1, task2);
    },
    isChildrenOf(source: string, parentTask: string, childTask: string) {
        const yamlDoc = yaml.parseDocument(this.extractTask(source, parentTask)!);
        let isChildrenOf = false;
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.get("id") === childTask) {
                    isChildrenOf = true;
                    return yaml.visit.BREAK;
                }
            }
        })
        return isChildrenOf;
    },
    getChildrenTasks(source: string, taskId: string) {
        const yamlDoc = yaml.parseDocument(this.extractTask(source, taskId)!);
        const children: string[] = [];
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.get("id") !== taskId) {
                    children.push(map.get("id") as string);
                }
            }
        })
        return children;
    },
    getParentTask(source: string, taskId: string) {
        const yamlDoc = yaml.parseDocument(source);
        let parentTask = null;
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.get("id") !== taskId) {
                    yaml.visit(map, {
                        Map(_, childMap) {
                            if (childMap.get("id") === taskId) {
                                parentTask = map.get("id");
                                return yaml.visit.BREAK;
                            }
                        }
                    })
                }
            }
        })
        return parentTask;
    },
    isTaskError(source: string, taskId: string) {
        const yamlDoc = yaml.parseDocument(source);
        let isTaskError = false;
        yaml.visit(yamlDoc, {
            Pair(_, pair: any) {
                if (pair.key.value === "errors") {
                    yaml.visit(pair, {
                        Map(_, map) {
                            if (map.get("id") === taskId) {
                                isTaskError = true;
                                return yaml.visit.BREAK;
                            }
                        }
                    })
                }
            }
        })
        return isTaskError;
    },
    isTrigger(source: string, taskId: string) {
        const yamlDoc = yaml.parseDocument(source);
        let isTrigger = false;
        yaml.visit(yamlDoc, {
            Pair(_, pair: any) {
                if (pair.key.value === "triggers") {
                    yaml.visit(pair, {
                        Map(_, map) {
                            if (map.get("id") === taskId) {
                                isTrigger = true;
                                return yaml.visit.BREAK;
                            }
                        }
                    })
                }
            }
        })
        return isTrigger;
    },
    replaceIdAndNamespace(source: string, id: string, namespace: string) {
        return source.replace(/^(id\s*:\s*(["']?))\S*/m, "$1"+id+"$2").replace(/^(namespace\s*:\s*(["']?))\S*/m, "$1"+namespace+"$2")
    },
    updateMetadata(source: string, metadata: Record<string, any>) {
        // TODO: check how to keep comments
        const yamlDoc = yamlParseDocument(source);

        if (!yamlDoc.contents.items) {
            return source;
        }

        for (const property in metadata) {
            if (yamlDoc.contents.items.find((item: any) => item.key.value === property)) {
                yamlDoc.contents.items.find((item: any) => item.key.value === property).value = metadata[property];
            } else {
                yamlDoc.contents.items.push(new yaml.Pair(new yaml.Scalar(property), metadata[property]));
            }
        }
        return this.cleanMetadata(yamlDoc.toString(TOSTRING_OPTIONS));
    },
    cleanMetadata(source: string) {
        // Reorder and remove empty metadata
        const yamlDoc = yamlParseDocument(source);

        if (!yamlDoc.contents.items) {
            return source;
        }

        const order = ["id", "namespace", "description", "retry", "labels", "inputs", "variables", "tasks", "triggers", "errors", "finally", "pluginDefaults", "taskDefaults", "concurrency", "outputs", "disabled"];
        const updatedItems = [];
        for (const prop of order) {
            const item = yamlDoc.contents.items.find((e: any) => e.key.value === prop);
            if (item && (((isSeq(item.value) || isMap(item.value)) && item.value.items.length > 0) || (item.value.value !== undefined && item.value.value !== null))) {
                updatedItems.push(item);
            }
        }
        yamlDoc.contents.items = updatedItems;
        return yamlDoc.toString(TOSTRING_OPTIONS);
    },
    getMetadata(source: string) {
        const yamlDoc = yamlParseDocument(source);
        const metadata: Record<string, any> = {};
        for (const item of yamlDoc.contents.items) {
            if (item.key.value !== "tasks" && item.key.value !== "triggers" && item.key.value !== "errors") {
                metadata[item.key.value] = isMap(item.value) || isSeq(item.value) ? item.value.toJSON() : item.value.value;
            }
        }
        return metadata;
    },
    flowHaveTasks(source: string) {
        const yamlDoc = yamlParseDocument(source);

        if (!yamlDoc.contents.items) {
            return false;
        }

        const tasks = yamlDoc.contents.items.find((item: any) => item.key?.value === "tasks");
        return tasks?.value?.items?.length >= 1;
    },
    deleteMetadata(source: string, metadata: Record<string, any>) {
        const yamlDoc = yamlParseDocument(source);

        if (!yamlDoc.contents.items) {
            return source;
        }

        const item = yamlDoc.contents.items.find((e: any) => e.key.value === metadata);
        if (item) {
            yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(item), 1);
        }

        return yamlDoc.toString(TOSTRING_OPTIONS);
    },
    getChartAtPosition(source: string, position: {lineNumber: number, column: number}) {
        const yamlDoc = yaml.parseDocument(source);
        const lineCounter = new LineCounter();
        yaml.parseDocument(source, {lineCounter});
        const cursorIndex = lineCounter.lineStarts[position.lineNumber - 1] + position.column;

        let chart: any = null;
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.items) {
                    for (const item of map.items as any[]) {
                        if (item.key.value === "charts") {
                            if (item.value.items) {
                                for (const chartItem of item.value.items) {
                                    if (chartItem.range[0] <= cursorIndex && chartItem.range[1] >= cursorIndex) {
                                        chart = chartItem;
                                        return yaml.visit.BREAK;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        });

        return chart ? chart.toJSON() : null;
    },
    getAllCharts(source: string) {
        const yamlDoc = yaml.parseDocument(source);
        const charts: string[] = [];

        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.items) {
                    for (const item of map.items as any[]) {
                        if (item.key.value === "charts" && item.value.items) {
                            for (const chartItem of item.value.items) {
                                charts.push(chartItem.toJSON());
                            }
                        }
                    }
                }
            }
        });

        return charts;
    }

    ,extractIndentAndMaybeYamlKey(stringToTest: string): {indent: number, yamlKey: string | undefined, valueStartIndex: number | undefined} | undefined {
        const exec = indentAndYamlKeyCapture.exec(stringToTest);
        if (exec === null) {
            return undefined;
        }

        const [, stringBeforeValue, indent, yamlKey]: [string, string, string | undefined] = [...exec];
        return {indent: indent.length, yamlKey, valueStartIndex: yamlKey === undefined ? undefined : (exec.index + stringBeforeValue.length)};
    }

    ,getParentKeyByChildIndent(stringToSearch: string, indent: number): {key: string, valueStartIndex: number} | undefined {
        if (indent < 2) {
            return undefined;
        }

        const matches = stringToSearch.matchAll(new RegExp(`(?<! ) {${indent - 2}}(?! )${yamlKeyCapture}`, "g"));
        const lastMatch = [...matches].pop();
        if (lastMatch === undefined) {
            return undefined;
        }
        return {key: lastMatch[1], valueStartIndex: lastMatch.index + lastMatch[0].length};
    }
}
