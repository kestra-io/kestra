import JsYaml from "js-yaml";
import yaml, {Document, LineCounter, Pair, YAMLMap, YAMLSeq} from "yaml";
import _cloneDeep from "lodash/cloneDeep"

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

    static parse(item) {
        return JsYaml.load(item);
    }

    static extractTask(source, taskId) {
        const yamlDoc = yaml.parseDocument(source);
        let taskNode = YamlUtils._extractTask(yamlDoc, taskId);
        return taskNode === undefined ? undefined : new yaml.Document(taskNode).toString(TOSTRING_OPTIONS);
    }

    static _extractTask(yamlDoc, taskId, callback) {
        const find = (element) => {
            if (element === undefined) {
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

        const keys = Object.keys(value)
            .sort()
            .sort((a, b) => {
                return YamlUtils.index(SORT_FIELDS, a) - YamlUtils.index(SORT_FIELDS, b);
            });

        return keys;
    }

    static index(based, value) {
        const index = based.indexOf(value);

        return index === -1 ? Number.MAX_SAFE_INTEGER : index;
    }

    static getTaskType(source, position) {
        const lineCounter = new LineCounter();
        const yamlDoc = yaml.parseDocument(source, {lineCounter});
        const cursorIndex = lineCounter.lineStarts[position.lineNumber-1] + position.column;
        for(const item of yamlDoc.contents.items){
            if(item.value instanceof YAMLSeq && item.key.range[0] <= cursorIndex && item.value.range[1] >= cursorIndex){
                return YamlUtils._getTaskType(item.value, cursorIndex, null)
            }
        }
    }

    static _getTaskType(element, cursorIndex, previousTaskType){
        let taskType = previousTaskType
        for(const item of element.items){
            if(item instanceof Pair){
                if(item.key.value === "type" && element.range[0] <= cursorIndex && element.range[1] >= cursorIndex){
                    taskType = item.value.value
                }
                if((item.value instanceof YAMLSeq || item.value instanceof YAMLMap) && item.value.range[0] <= cursorIndex && item.value.range[1] >= cursorIndex){
                    taskType = this._getTaskType(item.value, cursorIndex, taskType)
                }
            }
            else if(item.range[0] <= cursorIndex && item.range[1] >= cursorIndex){
                if(item.items instanceof Array){
                    taskType = this._getTaskType(item, cursorIndex)
                }
            }
        }
        return taskType
    }
}
