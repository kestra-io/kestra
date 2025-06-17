<template>
    <el-select
        v-model="modelValue"
        :placeholder="$t(`no_code.select.${blockType}`)"
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
    import {BlockType} from "../code/utils/types";
    import {usePluginsStore} from "../../stores/plugins";

    const props = defineProps<{
        blockType: BlockType | "pluginDefaults";
    }>()

    const modelValue = defineModel({
        type: String,
        default: "",
    });

    const store = useStore();
    const pluginsStore = usePluginsStore();
    pluginsStore.setVuexStore(store);

    onBeforeMount(() => {
        pluginsStore.listWithSubgroup({includeDeprecated: false});
    })

    const plugins = computed(() => {
        return pluginsStore.plugins;
    })
    const icons = computed(() => {
        return pluginsStore.icons;
    })

    const taskModels = computed(() => {
        const models = new Set<any>();
        const pluginKeySection: BlockType[] =
            props.blockType === "pluginDefaults"
                ? ["tasks", "conditions", "triggers", "taskRunners"]
                : [props.blockType];

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
