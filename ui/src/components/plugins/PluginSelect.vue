<template>
    <el-select
        v-model="modelValue"
        :placeholder="$t(`no_code.select.${section}`)"
        filterable
    >
        <el-option
            v-for="item in taskModels.sort()"
            :key="item"
            :label="item"
            :value="item"
        >
            <span class="options">
                <task-icon :cls="item" :only-icon="true" :icons="icons" />
                <span>
                    {{ item }}
                </span>
            </span>
        </el-option>

        <template #prefix>
            <task-icon v-if="modelValue" :cls="modelValue" :only-icon="true" :icons="icons" />
        </template>
    </el-select>
</template>

<script setup lang="ts">
    import {computed, onBeforeMount} from "vue";
    import {useStore} from "vuex";
    import {TaskIcon} from "@kestra-io/ui-libs";
    import {SectionKey} from "../code/utils/types";

    /**
     * For each section, pick the members of the
     * plugin to allow to select.
     */
    const KEY_SECTIONS_MAP: Record<SectionKey, string[]> = {
        "tasks": ["tasks"],
        "triggers": ["triggers"],
        "error handlers": ["tasks"],
        "finally": ["tasks"],
        "after execution": ["tasks"],
        "plugin defaults": [
            "tasks",
            "triggers",
            "conditions",
            "taskRunners"
        ],
    }

    const props = defineProps<{
        section?: keyof typeof KEY_SECTIONS_MAP;
    }>()

    const modelValue = defineModel({
        type: String,
        default: "",
    });

    const store = useStore();

    onBeforeMount(() => {
        store.dispatch("plugin/listWithSubgroup", {includeDeprecated: false});
    })

    const plugins = computed(() => {
        return store.state.plugin.plugins;
    })
    const icons = computed(() => {
        return store.state.plugin.icons;
    })

    const taskModels = computed(() => {
        const models = new Set<string>();
        const pluginKeySection = KEY_SECTIONS_MAP[props.section || "tasks"] || ["tasks"];

        for (const plugin of plugins.value || []) {
            for (const curSection of pluginKeySection) {
                const entries = plugin[curSection];
                if (entries) {
                    for (const model of entries) {
                        models.add(model);
                    }
                }
            }
        }

        return Array.from(models);
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
