<template>
    <el-breadcrumb class="p-4">
        <el-breadcrumb-item
            v-for="(breadcrumb, index) in breadcrumbs"
            :key="index"
        >
            {{ breadcrumb?.label }}
        </el-breadcrumb-item>
    </el-breadcrumb>
</template>

<script setup lang="ts">
    import {computed, inject, onMounted, ref} from "vue";
    import * as YAML_UTILS from "@kestra-io/ui-libs/flow-yaml-utils";

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    import {
        BREADCRUMB_INJECTION_KEY, CREATING_TASK_INJECTION_KEY,
        FLOW_INJECTION_KEY,
        BLOCKTYPE_INJECT_KEY, REF_PATH_INJECTION_KEY,
        PARENT_PATH_INJECTION_KEY
    } from "../injectionKeys";
    const {t} = useI18n({useScope: "global"});

    const breadcrumbs = inject(BREADCRUMB_INJECTION_KEY, ref([]));
    const flowYaml = inject(FLOW_INJECTION_KEY, ref(""));
    const refPath = inject(REF_PATH_INJECTION_KEY, undefined);
    const taskCreation = inject(CREATING_TASK_INJECTION_KEY, ref(false));
    const blockType = inject(BLOCKTYPE_INJECT_KEY, undefined);
    const parentPath = inject(PARENT_PATH_INJECTION_KEY, "");

    const flow = computed(() => {
        return YAML_UTILS.parse(flowYaml.value);
    });

    onMounted(() => {
        breadcrumbs.value[0] = {
            label: store.state.flow.isCreating
                ? t("create_flow")
                : flow.value.id,
        }

        const index = parentPath ? 2 : 1;
        if(parentPath){
            breadcrumbs.value[1] = {
                label: parentPath,
            }
        }
        if(taskCreation.value || (refPath?.length && refPath.length > 0)){
            breadcrumbs.value[index] = {
                label: taskCreation.value
                    ? t(`no_code.creation.${blockType}`)
                    : refPath ?? ""
            }
        }
    });
</script>

<style scoped lang="scss">
@import "../styles/code.scss";

.clickable{
    cursor: pointer;
}

.item:last-child > .el-breadcrumb__inner > a {
    color: $code-primary !important;
}
</style>
