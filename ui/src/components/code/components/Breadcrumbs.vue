<template>
    <el-breadcrumb class="p-4">
        <el-breadcrumb-item
            v-for="(breadcrumb, index) in breadcrumbs"
            :key="index"
            :class="{clickable: saveMode === 'button'}"
            @click="
                () => {
                    if (saveMode === 'button') {
                        breadcrumbs = breadcrumbs.slice(0, index + 1);
                        panel = null;
                        clickBreadCrumb(index)
                    }
                }
            "
        >
            {{ breadcrumb?.label }}
        </el-breadcrumb-item>
    </el-breadcrumb>
</template>

<script setup lang="ts">
    import {computed, inject, onMounted, ref, watch} from "vue";
    import {YamlUtils as YAML_UTILS} from "@kestra-io/ui-libs";

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    import {
        BREADCRUMB_INJECTION_KEY, CREATING_TASK_INJECTION_KEY,
        FLOW_INJECTION_KEY, PANEL_INJECTION_KEY,
        SECTION_INJECTION_KEY, TASKID_INJECTION_KEY,
        PARENT_TASKID_INJECTION_KEY,
        SAVEMODE_INJECTION_KEY,
        CLOSE_TASK_FUNCTION_INJECTION_KEY
    } from "../injectionKeys";
    const {t} = useI18n({useScope: "global"});

    const breadcrumbs = inject(BREADCRUMB_INJECTION_KEY, ref([]));
    const panel = inject(PANEL_INJECTION_KEY, ref());
    const flowYaml = inject(FLOW_INJECTION_KEY, ref(""));
    const taskId = inject(TASKID_INJECTION_KEY, ref(""));
    const taskCreation = inject(CREATING_TASK_INJECTION_KEY, ref(false));
    const taskSection = inject(SECTION_INJECTION_KEY, ref(""));
    const parentTaskId = inject(PARENT_TASKID_INJECTION_KEY, ref(""));
    const saveMode = inject(SAVEMODE_INJECTION_KEY, "auto");
    const closeTask = inject(CLOSE_TASK_FUNCTION_INJECTION_KEY, () => {});

    const flow = computed(() => {
        return YAML_UTILS.parse(flowYaml.value);
    });

    onMounted(() => {
        breadcrumbs.value[0] = {
            label: store.state.flow.isCreating
                ? t("create_flow")
                : flow.value.id,
        }
    });

    watch(
        [taskCreation, taskId, parentTaskId],
        ([isCreating, taskIdVal, parentTaskIdVal]) => {
            const index = parentTaskIdVal ? 2 : 1;
            if(parentTaskIdVal){
                breadcrumbs.value[1] = {
                    label: parentTaskIdVal,
                }
            }
            if(isCreating || taskIdVal.length > 0){
                breadcrumbs.value[index] = {
                    label: isCreating
                        ? t(`no_code.creation.${taskSection.value}`)
                        : taskIdVal
                }
            }

        },
        {immediate: true}
    );

    function clickBreadCrumb(breadCrumbIndex: number){
        // if it's a value, and the task has been clicked,
        // only remove it from the breadcrumbs
        // the "lastBreadcrumb.component" will be closed from the breadcrumbs
        if (breadCrumbIndex === breadcrumbs.value.length - 2) {
            const lastBreadcrumb = breadcrumbs.value[breadcrumbs.value.length - 1]
            if(lastBreadcrumb.component){
                breadcrumbs.value.pop();
                return
            }
        }

        breadcrumbs.value.splice(breadCrumbIndex + 1);
        closeTask();
    }
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
