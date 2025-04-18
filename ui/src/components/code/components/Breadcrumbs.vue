<template>
    <el-breadcrumb class="p-4">
        <el-breadcrumb-item
            v-for="(breadcrumb, index) in breadcrumbs"
            :key="index"
            class="item"
            @click="
                (store.commit('code/removeBreadcrumb', {position: index}),
                 store.commit('code/unsetPanel', false),
                 clickBreadCrumb(index)
                )
            "
        >
            {{ breadcrumb.label }}
        </el-breadcrumb-item>
    </el-breadcrumb>
</template>

<script setup lang="ts">
    import {computed, inject, onMounted, ref, watch} from "vue";

    import {useStore} from "vuex";
    const store = useStore();

    import {useI18n} from "vue-i18n";
    import {SECTION_INJECTION_KEY, TASKID_INJECTION_KEY} from "../injectionKeys";
    const {t} = useI18n({useScope: "global"});

    const props = defineProps<{
        flow: {
            id: string;
        };
    }>();

    store.commit("code/clearBreadcrumbs");

    const breadcrumbs = computed(() => store.state.code.breadcrumbs);
    const taskId = inject(TASKID_INJECTION_KEY, ref(""));
    const taskSection = inject(SECTION_INJECTION_KEY, ref(""));

    onMounted(() => {
        store.commit("code/addBreadcrumbs", {
            breadcrumb: {
                label:
                    taskId.value === "new"
                        ? t("create_flow")
                        : props.flow.id,
            },
            position: 0,
        });
    });

    watch(
        taskId,
        (value) => {
            if (!value) return;

            store.commit("code/addBreadcrumbs", {
                breadcrumb: {
                    label:
                        value === "new"
                            ? t(`no_code.creation.${taskSection.value}`)
                            : value,
                },
                position: 1,
            });
        },
    );

    function clickBreadCrumb(index: number){
        if (index === 0 && taskId.value.length > 0) {
            taskId.value = "";
            taskSection.value = "";
        }
    }
</script>

<style scoped lang="scss">
@import "../styles/code.scss";

.item{
    cursor: pointer;
}

.item:last-child > .el-breadcrumb__inner > a {
    color: $code-primary !important;
}
</style>
