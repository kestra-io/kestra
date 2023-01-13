import JsYaml from "js-yaml";
import yaml from "yaml";
import _cloneDeep from "lodash/cloneDeep"

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
        for (const [index, item] of yamlDoc.get("tasks").items.entries()) {
            if (taskId == item.get("id")) {
                // console.log(new yaml.Document(item).toString())
                const task = new yaml.Document(item).toString();
                return {task, index};
            }
        }
        return null
    }

    static replaceTaskInDocument(source, index, newContent) {
        const yamlDoc = yaml.parseDocument(source);
        const newItem = new yaml.Document(yaml.parseDocument(newContent))
        YamlUtils.replaceCommentInTask(yamlDoc.get("tasks").items[index], newItem);
        yamlDoc.get("tasks").items[index] = newItem
        return yamlDoc.toString();

    }

    // oldTask a YAMLMap, newTask a YAML document containing a YAMLMap
    static replaceCommentInTask(oldTask, newTask) {
        for (const oldProp of oldTask.items) {
            for (const newProp of newTask.contents.items) {
                if (oldProp.key.value == newProp.key.value && newProp.value.comment == undefined) {
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
}
