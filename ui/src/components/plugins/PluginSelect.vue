<template>
    <el-select
        v-model="modelValue"
        :placeholder="te(`no_code.select.${blockType}`) ? t(`no_code.select.${blockType}`) : t('no_code.select.default')"
        filterable
    >
        <el-option
            v-for="item in taskModels"
            :key="item"
            :label="item"
            :value="item"
        >
            <span class="options">
                <TaskIcon v-if="hasIcons" :cls="item" :onlyIcon="true" :icons="pluginsStore.icons" />
                <span>
                    {{ item }}
                </span>
            </span>
        </el-option>

        <template #prefix>
            <TaskIcon v-if="modelValue && hasIcons" :cls="modelValue" :onlyIcon="true" :icons="pluginsStore.icons" />
        </template>
    </el-select>
</template>

<script setup lang="ts">
    import {computed, inject, onBeforeMount, ref} from "vue";
    import {useI18n} from "vue-i18n";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import {removeRefPrefix, usePluginsStore} from "../../stores/plugins";
    import {
        FULL_SCHEMA_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY,
        SCHEMA_DEFINITIONS_INJECTION_KEY,
    } from "../no-code/injectionKeys";
    import {getValueAtJsonPath} from "../../utils/utils";

    const pluginsStore = usePluginsStore();

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");
    const fullSchema = inject(FULL_SCHEMA_INJECTION_KEY, ref<Record<string, any>>({}));
    const rootDefinitions = inject(SCHEMA_DEFINITIONS_INJECTION_KEY, ref<Record<string, any>>({}));

    const blockType = parentPath.split(".").pop() ?? "";

    const fieldDefinition = computed(() => {
        if (props.blockSchemaPath.length === 0) {
            console.error("Definition key is required for PluginSelect component");
        }
        return getValueAtJsonPath(fullSchema.value, props.blockSchemaPath);
    })

    onBeforeMount(() => {
        if (blockType === "pluginDefaults") {
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
        if (blockType === "pluginDefaults") {
            const models = new Set<string>();
            const pluginKeySection = ["tasks", "conditions", "triggers", "taskRunners"] as const;

            for (const plugin of pluginsStore.plugins || []) {
                for (const curSection of pluginKeySection) {
                    const entries = plugin[curSection];
                    if (entries) {
                        for (const {cls} of entries.filter(({deprecated}) => !deprecated)) {
                            if(cls){
                                models.add(cls);
                            }
                        }
                    }
                }
            }

            return models;
        }

        return allRefs.value.reduce((acc: Set<any>, item: string) => {
            const def = rootDefinitions.value?.[item]

            if (!def || def.$deprecated) {
                return acc;
            }

            const consolidatedType = def.allOf
                ? def.allOf.find((d: any) => d.properties?.type)?.properties.type
                : def.properties?.type;

            if (consolidatedType?.const) {
                acc.add(consolidatedType?.const);
            }
            return acc
        }, new Set<string>());
    })

    const taskModels = computed(() => Array.from(taskModelsSets.value).sort() as string[]);

    const hasIcons = computed(() => {
        return pluginsStore.icons && Object.keys(pluginsStore.icons).filter(plugin => taskModels.value.includes(plugin)).length > 0;
    });

    const {t, te} = useI18n();

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
</style>
