<template>
    <el-select
        v-model="modelValue"
        :placeholder="$te(`no_code.select.${blockType}`) ? $t(`no_code.select.${blockType}`) : $t('no_code.select.default')"
        filterable
        clearable
    >
        <el-option
            v-for="item in taskModels"
            :key="item.cls"
            :label="item.cls"
            :value="item.cls"
        >
            <span class="options">
                <TaskIcon
                    v-if="hasIcons"
                    :cls="item?.cls"
                    :onlyIcon="true"
                    :icons="pluginsStore.icons"
                />
                <div class="option-content">
                    <div class="cls">{{ item?.cls }}</div>
                    <div v-if="item?.title && item?.title !== item?.cls" class="title">
                        {{ item?.title }}
                    </div>
                </div>
            </span>
        </el-option>

        <template #prefix>
            <TaskIcon
                v-if="modelValue && hasIcons"
                :cls="modelValue"
                :onlyIcon="true"
                :icons="pluginsStore.icons"
            />
        </template>
    </el-select>
</template>

<script setup lang="ts">
    import {computed, inject, onBeforeMount, ref} from "vue";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import {removeRefPrefix, usePluginsStore} from "../../stores/plugins";
    import {
        FULL_SCHEMA_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        SCHEMA_DEFINITIONS_INJECTION_KEY
    } from "../no-code/injectionKeys";
    import {getValueAtJsonPath} from "../../utils/utils";

    const pluginsStore = usePluginsStore();

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref<Record<string, any>>({}));
    const rootDefinitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, ref<Record<string, any>>({}));

    const blockType = (parentPath.split(".").pop() ?? "").replace(/\[\d+\]$/, "");
    const isPluginBlock = ["tasks", "triggers", "conditions", "taskRunners"].includes(blockType);

    const fieldDefinition = computed(() => {
        if (props.blockSchemaPath.length === 0) {
            console.error("Definition key is required for PluginSelect component");
        }
        return getValueAtJsonPath(fullSchema.value, props.blockSchemaPath);
    })

    onBeforeMount(() => {
        if (blockType === "pluginDefaults" || isPluginBlock) {
            pluginsStore.listWithSubgroup({includeDeprecated: false});
        }
    })

    const allRefs = computed(() => fieldDefinition.value?.anyOf?.map((item: any) => {
        if (item.allOf) {
            // if the item is an allOf, we need to find the first item that has a $ref
            const refItem = item.allOf.find((d: any) => d.$ref);
            if (refItem?.$ref) {
                return removeRefPrefix(refItem.$ref);
            }
        }
        return removeRefPrefix(item.$ref);
    }) || []);

    const taskModelsSets = computed(() => {
        if (blockType === "pluginDefaults" || isPluginBlock) {
            const models = new Map<string, string>();
            const pluginKeySection = (["tasks", "conditions", "triggers", "taskRunners"] as const)
                .filter(s => blockType === "pluginDefaults" || s === blockType);

            for (const plugin of pluginsStore.plugins || []) {
                for (const curSection of pluginKeySection) {
                    const entries = plugin[curSection] as {cls: string, title?: string, deprecated?: boolean}[] | undefined;
                    if (entries) {
                        for (const {cls, title} of entries.filter(({deprecated}) => !deprecated)) {
                            if (cls) {
                                models.set(cls, title ?? cls);
                            }
                        }
                    }
                }
            }

            return models;
        }

        return allRefs.value.reduce((acc: Map<string, string>, item: string) => {
            const def = rootDefinitions.value?.[item]

            if (!def || def.$deprecated) {
                return acc;
            }

            const consolidatedType = def.allOf
                ? def.allOf.find((d: any) => d.properties?.type)?.properties.type
                : def.properties?.type;

            if (consolidatedType?.const) {
                acc.set(consolidatedType.const, def.title ?? consolidatedType.const);
            }
            return acc
        }, new Map<string, string>());
    })

    const taskModels = computed(() => {
        return (Array.from(taskModelsSets.value) as [string, string][])
            .map(([cls, title]) => ({cls, title}))
            .sort((a, b) => a.cls.localeCompare(b.cls));
    });

    const hasIcons = computed(() => {
        const models = taskModels.value.map(m => m.cls);
        return pluginsStore.icons && Object.keys(pluginsStore.icons).filter(plugin => models.includes(plugin)).length > 0;
    });

    const modelValue = defineModel({
        type: String,
        default: "",
    });

    const props = defineProps<{
        blockSchemaPath: string,
    }>()
</script>

<style scoped lang="scss">
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

    .el-select-dropdown__item {
        height: fit-content;
        line-height: normal;
        padding: 8px 12px;
    }

    .options {
        display: flex;
        align-items: center;
        gap: 0.5rem;

        :deep(.wrapper) {
            width: 2rem;
            height: 2rem;
        }

        .option-content {
            display: flex;
            flex-direction: column;
            overflow: hidden;
            gap: 0.25rem;

            .cls {
                font-weight: 600;
                line-height: 1.2;
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }

            .title {
                font-size: 0.75rem;
                color: var(--ks-content-secondary);
                line-height: 1.2;
                white-space: normal;
            }
        }
    }

</style>
