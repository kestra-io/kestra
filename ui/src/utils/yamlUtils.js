import JsYaml from "js-yaml";
import yaml, {Document, isMap, isPair, isSeq, LineCounter, Pair, Scalar, YAMLMap, YAMLSeq} from "yaml";
import _cloneDeep from "lodash/cloneDeep"
import {SECTIONS} from "./constants.js";

const TOSTRING_OPTIONS = {lineWidth: 0};

export default class YamlUtils {
    static stringify(value) {
        if (typeof value === "undefined") {
            return "";
        }

        if (value.deleted !== undefined) {
            delete value.deleted
        }

        return JsYaml.dump(YamlUtils._transform(_cloneDeep(value)), {
            lineWidth: -1,
            noCompatMode: true,
            quotingType: "\"",
        });
    }

    static pairsToMap(pairs) {
        const map = new YAMLMap();
        if (!isPair(pairs?.[0])) {
            return map;
        }

        pairs.forEach(pair => {
            map.add(pair);
        });
        return map;
    }

    static parse(item) {
        if (item === undefined) {
            return undefined;
        }
        return JsYaml.load(item);
    }

    static extractTask(source, taskId) {
        const yamlDoc = yaml.parseDocument(source);
        let taskNode = YamlUtils._extractTask(yamlDoc, taskId);
        return taskNode === undefined ? undefined : new yaml.Document(taskNode).toString(TOSTRING_OPTIONS);
    }

    static _extractTask(yamlDoc, taskId, callback) {
        const find = (element) => {
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
        let result = find(yamlDoc.contents)

        if (result === undefined) {
            return undefined;
        }

        if (callback) {
            return new Document(result)
        } else {
            return new Document(result);
        }
    }

    static replaceTaskInDocument(source, taskId, newContent) {
        const yamlDoc = yaml.parseDocument(source);
        const newItem = yamlDoc.createNode(yaml.parseDocument(newContent))

        YamlUtils._extractTask(yamlDoc, taskId, (oldValue) => {
            YamlUtils.replaceCommentInTask(oldValue, newItem)

            return newItem;
        })

        return yamlDoc.toString(TOSTRING_OPTIONS);
    }

    static replaceCommentInTask(oldTask, newTask) {
        for (const oldProp of oldTask.items) {
            for (const newProp of newTask.items) {
                if (oldProp.key.value === newProp.key.value && newProp.value.comment === undefined) {
                    newProp.value.comment = oldProp.value.comment
                    break;
                }
            }
        }
    }

    static _transform(value) {
        if (value instanceof Array) {
            return value.map(r => {
                return YamlUtils._transform(r);
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
            return YamlUtils.sort(value)
                .reduce((accumulator, r) => {
                    if (value[r] !== undefined) {
                        accumulator[r] = YamlUtils._transform(value[r])
                    }

                    return accumulator;
                }, Object.create({}))
        }

        return value;
    }

    static sort(value) {
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
                return YamlUtils.index(SORT_FIELDS, a) - YamlUtils.index(SORT_FIELDS, b);
            });
    }

    static index(based, value) {
        const index = based.indexOf(value);

        return index === -1 ? Number.MAX_SAFE_INTEGER : index;
    }

    static nextDelimiterIndex(content, currentIndex) {
        if (currentIndex === content.length - 1) {
            return currentIndex;
        }

        const remainingContent = content.substring(currentIndex + 1);

        const nextDelimiterMatcher = remainingContent.match(/[ .}]/);
        if (!nextDelimiterMatcher) {
            return content.length - 1;
        } else {
            return currentIndex + nextDelimiterMatcher.index;
        }
    }

    // Specify a source yaml doc, the field to extract recursively in every map of the doc and optionally a predicate to define which paths should be taken into account
    // parentPathPredicate will take a single argument which is the path of each parent property starting from the root doc (joined with ".")
    // "my.parent.task" will mean that the field was retrieved in my -> parent -> task path.
    static extractFieldFromMaps(source, fieldName, parentPathPredicate = (_, __) => true, valuePredicate = (_) => true) {
        const yamlDoc = yaml.parseDocument(source);
        const maps = [];
        yaml.visit(yamlDoc, {
            Map(_, map, parent) {
                if (parentPathPredicate(parent.filter(p => yaml.isPair(p)).map(p => p.key.value).join(".")) && map.items) {
                    for (const item of map.items) {
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
    }

    static extractMaps(source, fieldConditions) {
        if (source.match(/^\s*{{/)) {
            return [];
        }

        const yamlDoc = yaml.parseDocument(source);
        const maps = [];
        yaml.visit(yamlDoc, {
            Map(_, yamlMap) {
                if (yamlMap.items) {
                    const map = yamlMap.toJS(yamlDoc);
                    for (let [fieldName, condition] of Object.entries(fieldConditions)) {
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

                    maps.push({map, range: yamlMap.range});
                }
            }
        });

        return maps;
    }

    static extractAllTypes(source, validTypes = []) {
        return this.extractFieldFromMaps(source, "type", undefined, value => validTypes.some(t => t === value));
    }

    static getTaskType(source, position, validTypes) {
        const types = this.extractAllTypes(source, validTypes);

        const lineCounter = new LineCounter();
        yaml.parseDocument(source, {lineCounter});
        const cursorIndex = lineCounter.lineStarts[position.lineNumber - 1] + position.column;

        for(const type of types.reverse()) {
            if (cursorIndex >= type.range[0]) {
                return type.type;
            }
        }
        return null;
    }

    static insertTask(source, taskId, newTask, insertPosition) {
        const yamlDoc = yaml.parseDocument(source);
        const newTaskNode = yamlDoc.createNode(yaml.parseDocument(newTask))
        const tasksNode = yamlDoc.contents.items.find(e => e.key.value === "tasks");
        if (!tasksNode || tasksNode?.value.value === null) {
            if (tasksNode) {
                yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(tasksNode), 1)
            }
            const taskList = new YAMLSeq()
            taskList.items.push(newTaskNode)
            const tasks = new Pair(new Scalar("tasks"), taskList)
            yamlDoc.contents.items.push(tasks)
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
    }

    static insertTrigger(source, triggerTask) {
        const yamlDoc = yaml.parseDocument(source);
        const newTriggerNode = yamlDoc.createNode(yaml.parseDocument(triggerTask));
        let added = false;
        const triggers = yamlDoc.contents.items.find(item => item.key.value === "triggers");
        if (triggers && triggers.value.items) {
            yaml.visit(yamlDoc, {
                Pair(_, pair) {
                    if (added) {
                        return yaml.visit.BREAK;
                    }
                    if (pair.key.value === "triggers") {
                        pair.value.items.push(newTriggerNode);
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
            yamlDoc.contents.items.push(newTriggers);
        }
        return YamlUtils.cleanMetadata(yamlDoc.toString(TOSTRING_OPTIONS));
    }

    static insertError(source, errorTask) {
        const yamlDoc = yaml.parseDocument(source);
        const newErrorNode = yamlDoc.createNode(yaml.parseDocument(errorTask));
        const errors = yamlDoc.contents.items.find(item => item.key.value === "errors");
        if (errors && errors.value.items) {
            yamlDoc.contents.items[yamlDoc.contents.items.indexOf(errors)].value.items.push(newErrorNode);
        } else {
            if (errors) {
                yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(errors), 1)
            }
            const errorsSeq = new yaml.YAMLSeq();
            errorsSeq.items.push(newErrorNode);
            const newErrors = new yaml.Pair(new yaml.Scalar("errors"), errorsSeq);
            yamlDoc.contents.items.push(newErrors);
        }
        return YamlUtils.cleanMetadata(yamlDoc.toString(TOSTRING_OPTIONS));
    }

    static insertFinally(source, finallyTask) {
        const yamlDoc = yaml.parseDocument(source);
        const newFinallyNode = yamlDoc.createNode(yaml.parseDocument(finallyTask));
        const items = yamlDoc.contents.items.find(item => item.key.value === "finally");
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
        return YamlUtils.cleanMetadata(yamlDoc.toString(TOSTRING_OPTIONS));
    }

    static insertErrorInFlowable(source, errorTask, flowableTask) {
        const yamlDoc = yaml.parseDocument(source);
        const newErrorNode = yamlDoc.createNode(yaml.parseDocument(errorTask));
        let added = false;
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (added) {
                    return yaml.visit.BREAK;
                }
                if (map.get("id") === flowableTask) {
                    if (map.items.find(item => item.key.value === "errors")) {
                        map.items.find(item => item.key.value === "errors").value.items.push(newErrorNode);
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
    }

    static deleteTask(source, taskId, section) {
        const inSection = section === SECTIONS.TASKS ? ["tasks", "errors"] : ["triggers"];
        const yamlDoc = yaml.parseDocument(source);
        yaml.visit(yamlDoc, {
            Pair(_, pair) {
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
    }

    static getFirstTask(source) {
        let parse = YamlUtils.parse(source);

        return parse && parse.tasks && parse.tasks[0].id;
    }

    static getLastTask(source) {
        let parse = YamlUtils.parse(source);

        return parse && parse.tasks && parse.tasks[parse.tasks.length - 1].id;
    }

    static checkTaskAlreadyExist(source, taskYaml) {
        const yamlDoc = yaml.parseDocument(source);
        const parsedTask = YamlUtils.parse(taskYaml);
        let taskExist = false;
        yaml.visit(yamlDoc, {
            Pair(_, pair) {
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
    }

    static isParentChildrenRelation(source, task1, task2) {
        return YamlUtils.isChildrenOf(source, task2, task1) || YamlUtils.isChildrenOf(source, task1, task2);
    }

    static isChildrenOf(source, parentTask, childTask) {
        const yamlDoc = yaml.parseDocument(YamlUtils.extractTask(source, parentTask));
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
    }

    static getChildrenTasks(source, taskId) {
        const yamlDoc = yaml.parseDocument(YamlUtils.extractTask(source, taskId));
        const children = [];
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.get("id") !== taskId) {
                    children.push(map.get("id"));
                }
            }
        })
        return children;
    }

    static getParentTask(source, taskId) {
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
    }

    static isTaskError(source, taskId) {
        const yamlDoc = yaml.parseDocument(source);
        let isTaskError = false;
        yaml.visit(yamlDoc, {
            Pair(_, pair) {
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
    }

    static isTrigger(source, taskId) {
        const yamlDoc = yaml.parseDocument(source);
        let isTrigger = false;
        yaml.visit(yamlDoc, {
            Pair(_, pair) {
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
    }

    static replaceIdAndNamespace(source, id, namespace) {
        return source.replace(/^(id\s*:\s*(["']?))\S*/m, "$1"+id+"$2").replace(/^(namespace\s*:\s*(["']?))\S*/m, "$1"+namespace+"$2")
    }

    static updateMetadata(source, metadata) {
        // TODO: check how to keep comments
        const yamlDoc = yaml.parseDocument(source);

        if (!yamlDoc.contents.items) {
            return source;
        }

        for (const property in metadata) {
            if (yamlDoc.contents.items.find(item => item.key.value === property)) {
                yamlDoc.contents.items.find(item => item.key.value === property).value = metadata[property];
            } else {
                yamlDoc.contents.items.push(new yaml.Pair(new yaml.Scalar(property), metadata[property]));
            }
        }
        return YamlUtils.cleanMetadata(yamlDoc.toString(TOSTRING_OPTIONS));
    }

    static cleanMetadata(source) {
        // Reorder and remove empty metadata
        const yamlDoc = yaml.parseDocument(source);

        if (!yamlDoc.contents.items) {
            return source;
        }

        const order = ["id", "namespace", "description", "retry", "labels", "inputs", "variables", "tasks", "triggers", "errors", "finally", "pluginDefaults", "taskDefaults", "concurrency", "outputs", "disabled"];
        const updatedItems = [];
        for (const prop of order) {
            const item = yamlDoc.contents.items.find(e => e.key.value === prop);
            if (item && (((isSeq(item.value) || isMap(item.value)) && item.value.items.length > 0) || (item.value.value !== undefined && item.value.value !== null))) {
                updatedItems.push(item);
            }
        }
        yamlDoc.contents.items = updatedItems;
        return yamlDoc.toString(TOSTRING_OPTIONS);
    }

    static getMetadata(source) {
        const yamlDoc = yaml.parseDocument(source);
        const metadata = {};
        for (const item of yamlDoc.contents.items) {
            if (item.key.value !== "tasks" && item.key.value !== "triggers" && item.key.value !== "errors") {
                metadata[item.key.value] = isMap(item.value) || isSeq(item.value) ? item.value.toJSON() : item.value.value;
            }
        }
        return metadata;
    }

    static flowHaveTasks(source) {
        const yamlDoc = yaml.parseDocument(source);

        if (!yamlDoc.contents.items) {
            return false;
        }

        const tasks = yamlDoc.contents.items.find(item => item.key?.value === "tasks");
        return tasks?.value?.items?.length >= 1;
    }

    static deleteMetadata(source, metadata) {
        const yamlDoc = yaml.parseDocument(source);

        if (!yamlDoc.contents.items) {
            return source;
        }

        const item = yamlDoc.contents.items.find(e => e.key.value === metadata);
        if (item) {
            yamlDoc.contents.items.splice(yamlDoc.contents.items.indexOf(item), 1);
        }

        return yamlDoc.toString(TOSTRING_OPTIONS);
    }

    static getChartAtPosition(source, position) {
        const yamlDoc = yaml.parseDocument(source);
        const lineCounter = new LineCounter();
        yaml.parseDocument(source, {lineCounter});
        const cursorIndex = lineCounter.lineStarts[position.lineNumber - 1] + position.column;

        let chart = null;
        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.items) {
                    for (const item of map.items) {
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
    }

    static getAllCharts(source) {
        const yamlDoc = yaml.parseDocument(source);
        const charts = [];

        yaml.visit(yamlDoc, {
            Map(_, map) {
                if (map.items) {
                    for (const item of map.items) {
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
}
