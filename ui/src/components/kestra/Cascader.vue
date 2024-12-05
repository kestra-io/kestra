<template>
    <el-cascader-panel :options="options">
        <template #default="{data}">
            <div v-if="isFile(data.value)">
                <VarValue :value="data.value" :execution="execution" />
            </div>
            <div v-else class="w-100 d-flex justify-content-between">
                <div class="pe-5 d-flex task label-container" :title="data.label">
                    {{ data.label }}
                </div>
                <div v-if="data.value && data.children">
                    <code>
                        {{ data.children.length }} {{ data.children.length === 1 ? t("item") : t("items") }}
                    </code>
                </div>
            </div>
        </template>
    </el-cascader-panel>
</template>

<script setup lang="ts">
    import VarValue from "../executions/VarValue.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    const isFile = (data) => typeof(data) === "string" && data.startsWith("kestra:///");

    interface Options {
        label: string;
        value: [string, number, boolean];
        children?: Options[];
    }

    defineProps<{options: Options, execution: any}>();
</script>

<style lang="scss" scoped>
.label-container{
    white-space: nowrap;
    overflow: hidden;
    text-overflow: ellipsis;
}
</style>
