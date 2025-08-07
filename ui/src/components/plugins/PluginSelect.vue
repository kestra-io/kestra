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
                <task-icon v-if="hasIcons" :cls="item" :only-icon="true" :icons="pluginsStore.icons" />
                <span>
                    {{ item }}
                </span>
            </span>
        </el-option>

        <template #prefix>
            <task-icon v-if="modelValue && hasIcons" :cls="modelValue" :only-icon="true" :icons="pluginsStore.icons" />
        </template>
    </el-select>
</template>

<script setup lang="ts">
    import {computed, inject, onBeforeMount} from "vue";
    import {useI18n} from "vue-i18n";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import {removeRefPrefix, usePluginsStore} from "../../stores/plugins";
    import {
        BLOCK_SCHEMA_PATH_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY
    } from "../code/injectionKeys";
    import {getValueAtJsonPath} from "../../utils/utils";

    const pluginsStore = usePluginsStore();

    const blockSchemaPath = inject(BLOCK_SCHEMA_PATH_INJECTION_KEY, "");
    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");

    const blockType = parentPath.split(".").pop() ?? "";

    const fieldDefinition = computed(() => {
        if (blockSchemaPath.length === 0) {
            console.error("Definition key is required for PluginSelect component");
        }
        return getValueAtJsonPath(pluginsStore.flowSchema, blockSchemaPath);
    })

    onBeforeMount(() => {
        if (blockType === "pluginDefaults") {
            pluginsStore.listWithSubgroup({includeDeprecated: false});
        }
    })

    const taskModels = computed(() => {
        if (blockType === "pluginDefaults") {
            const models = new Set<any>();
            const pluginKeySection = ["tasks", "conditions", "triggers", "taskRunners"] as const;

            for (const plugin of pluginsStore.plugins || []) {
                for (const curSection of pluginKeySection) {
                    const entries = plugin[curSection];
                    if (entries) {
                        for (const {cls} of entries.filter(({deprecated}) => !deprecated)) {
                            models.add(cls);
                        }
                    }
                }
            }

            return Array.from(models);
        }
        const allRefs = fieldDefinition.value?.anyOf?.map((item: any) => {
            if (item.allOf) {
                // if the item is an allOf, we need to find the first item that has a $ref
                const refItem = item.allOf.find((d: any) => d.$ref);
                if (refItem?.$ref) {
                    return removeRefPrefix(refItem.$ref);
                }
            }
            return removeRefPrefix(item.$ref);
        }) || [];

        return allRefs.reduce((acc: string[], item: string) => {
            const def = pluginsStore.flowDefinitions?.[item]
            
            if (!def || def.$deprecated) {
                return acc;
            }

            const consolidatedType = def.allOf
                ? def.allOf.find((d: any) => d.properties?.type)?.properties.type
                : def.properties?.type;

            if (consolidatedType?.const) {
                acc.push(consolidatedType?.const);
            }
            return acc
        }, []).sort();
    })

    const hasIcons = computed(() => {
        return pluginsStore.icons && Object.keys(pluginsStore.icons).filter(plugin => taskModels.value.includes(plugin)).length > 0;
    });

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
