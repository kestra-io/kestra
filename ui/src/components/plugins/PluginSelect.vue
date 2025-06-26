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
    import {BlockType} from "../code/utils/types";
    import {usePluginsStore} from "../../stores/plugins";
    import {PARENT_PATH_INJECTION_KEY} from "../code/injectionKeys";


    const pluginsStore = usePluginsStore();

    defineProps<{
        blockType: BlockType | "pluginDefaults";
    }>()

    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");

    const fieldDefinition = computed(() => {
        if(!pluginsStore.schemaType?.flow){
            return {};
        }
        return pluginsStore.schemaType.flow.definitions[removeRefPrefix(pluginsStore.schemaType.flow.$ref)]?.properties[parentPath];
    })

    const taskModels = computed(() => {
        return fieldDefinition.value?.items?.anyOf?.map((item: any) => {
            return removeRefPrefix(item.$ref);
        }) || [];
    })

    function removeRefPrefix(ref?: string): string {
        return ref?.replace(/^#\/definitions\//, "") ?? "";
    }

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
