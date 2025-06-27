<template>
    <el-select
        v-model="modelValue"
        :placeholder="t(`no_code.select.${blockType}`)"
        filterable
    >
        <el-option
            v-for="item in taskModels.sort()"
            :key="item"
            :label="item"
            :value="item"
        >
            <span class="options">
                <task-icon :cls="item" :only-icon="true" :icons="pluginsStore.icons" />
                <span>
                    {{ item }}
                </span>
            </span>
        </el-option>

        <template #prefix>
            <task-icon v-if="modelValue" :cls="modelValue" :only-icon="true" :icons="pluginsStore.icons" />
        </template>
    </el-select>
</template>

<script setup lang="ts">
    import {computed, inject} from "vue";
    import {useI18n} from "vue-i18n";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";
    import {BlockType} from "../code/utils/types";
    import {removeRefPrefix, usePluginsStore} from "../../stores/plugins";
    import {PARENT_PATH_INJECTION_KEY} from "../code/injectionKeys";

    const pluginsStore = usePluginsStore();

    defineProps<{
        blockType: BlockType | "pluginDefaults";
    }>()

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");

    // - recursively get each item in the path
    function getNextPathItem(def: any, path: (string | number)[], index:number): {
        properties?: any
        items?: any;
        type?: string;
    } {

        const nextKey = path.shift();
        if(nextKey === undefined) {
            return def;
        }

        if(typeof nextKey === "number") {
            // if the next key is a number, we are in an array
            // so we need to check if the items are defined in an anyOf
            const downstreamProperty = path.at(0)

            if(def.items?.anyOf && typeof downstreamProperty === "string") {
                const anyOf = def.items.anyOf.map(pluginsStore.resolveRef)

                const anyOfFiltered = anyOf
                    .filter((item: any) => item.properties?.[downstreamProperty]);

                // take the first schema that has the downstream property
                return getNextPathItem(anyOfFiltered[0], path, index+1);
            }

            return def.items.type === "object" ? getNextPathItem(def.items, path, index+1) : def.items;
        }

        if(!def?.properties?.[nextKey]) {
            return def;
        }

        return getNextPathItem(def?.properties[nextKey], path, index+1);
    }

    // FIXME: field definition needs to be injected when we open the tab
    // resolution of type is much easier in context
    const fieldDefinition = computed(() => {

        if(!pluginsStore.flowRootProperties){
            return {};
        }

        const pathArray = YAML_UTILS.parsePath(parentPath)

        // - finally extract the definition
        const lastDef = getNextPathItem(pluginsStore.flowRootSchema, pathArray, 0);

        // - if in an array with multiple anyOf, resolve the type will be harder
        return lastDef?.type === "array" ? lastDef.items : lastDef ?? {};
    })

    const taskModels = computed(() => {
        // what if the fieldDefinition is not an array?
        // what if its items are defined in an allOf?
        // what if the refs are one level deeper?
        const allRefs = fieldDefinition.value?.anyOf?.map((item: any) => {
            if(item.allOf){
                // if the item is an allOf, we need to find the first item that has a $ref
                const refItem = item.allOf.find((d: any) => d.$ref);
                if(refItem?.$ref) {
                    return removeRefPrefix(refItem.$ref);
                }
            }
            return removeRefPrefix(item.$ref);
        }) || [];

        return allRefs.reduce((acc: string[], item: string) => {
            const def = pluginsStore.flowDefinitions?.[item]
            if(!def) {
                return acc;
            }
            if(def.$deprecated === true) {
                return acc;
            }

            const consolidatedType = def.allOf
                ? def.allOf.find((d: any) => d.properties?.type).type
                : def.properties?.type;

            if(consolidatedType?.const){
                acc.push(consolidatedType?.const);
            }
            return acc
        }, []).sort();
    })

    const {t} = useI18n();

    const modelValue = defineModel({
        type: String,
        default: "",
    });
</script>

<style lang="scss" scoped>
    :deep(div.wrapper) {
        display: inline-block;
        width: 20px;
        height: 20px;
        margin-right: 1rem;
    }

    :deep(.el-input__prefix-inner) {
        .wrapper {
            top: 0;
            margin-right: 0;
        }
    }

    :deep(.el-select__suffix) {
        display: flex !important;
    }
</style>
