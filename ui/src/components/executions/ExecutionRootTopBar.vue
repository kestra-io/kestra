<template>
    <TopNavBar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb">
        <template #title>
            {{ routeInfo?.title }}
            <Badge v-if="isATestExecution" :label="$t('test-badge-text')" :tooltip="$t('test-badge-tooltip')" />
        </template>
        <template #additional-right v-if="canDelete || isAllowedTrigger || isAllowedEdit">
            <div class="d-flex align-items-center gap-2">
                <ul class="d-none d-xl-flex align-items-center">
                    <li v-if="isAllowedEdit">
                        <a :href="`${finalApiUrl}/executions/${execution.id}`" target="_blank">
                            <el-button :icon="Api">
                                {{ $t("api") }}
                            </el-button>
                        </a>
                    </li>
                    <li v-if="canDelete">
                        <el-button :icon="Delete" @click="deleteExecution">
                            {{ $t("delete") }}
                        </el-button>
                    </li>
                    <li v-if="isAllowedEdit">
                        <el-button :icon="Pencil" @click="editFlow">
                            {{ $t("edit flow") }}
                        </el-button>
                    </li>
                </ul>
    
                <el-dropdown class="d-flex d-xl-none align-items-center">
                    <el-button>
                        <el-icon><DotsVerticalIcon /></el-icon>
                        <span class="d-none d-lg-inline-block">{{ $t("more_actions") }}</span>
                    </el-button>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <el-dropdown-item v-if="isAllowedEdit">
                                <a :href="`${finalApiUrl}/executions/${execution.id}`" target="_blank">
                                    <el-icon><Api /></el-icon>
                                    {{ $t("api") }}
                                </a>
                            </el-dropdown-item>
                            <el-dropdown-item v-if="canDelete" @click="deleteExecution">
                                <el-icon><Delete /></el-icon>
                                {{ $t("delete") }}
                            </el-dropdown-item>
                            <el-dropdown-item v-if="isAllowedEdit" @click="editFlow">
                                <el-icon><Pencil /></el-icon>
                                {{ $t("edit flow") }}
                            </el-dropdown-item>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
    
                <div v-if="isAllowedTrigger">
                    <TriggerFlow
                        type="primary"
                        :flowId="$route.params.flowId"
                        :namespace="$route.params.namespace"
                    />
                </div>
            </div>
        </template>
    </TopNavBar>
</template>

<script setup>
    import Api from "vue-material-design-icons/Api.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import DotsVerticalIcon from "vue-material-design-icons/DotsVertical.vue";
    import Badge from "../global/Badge.vue";
</script>

<script>
    import {h, ref} from "vue"
    import {ElCheckbox, ElMessageBox} from "element-plus"
    import {mapStores} from "pinia";

    import TriggerFlow from "../flows/TriggerFlow.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs"
    import {apiUrl} from "override/utils/route";
    import {useExecutionsStore} from "../../stores/executions";
    import {useAuthStore} from "override/stores/auth"

    export default {
        components: {
            TriggerFlow,
            TopNavBar
        },
        props: {
            routeInfo: {
                type: Object,
                required: true
            }
        },
        computed: {
            ...mapStores(useExecutionsStore, useAuthStore),
            execution() {
                return this.executionsStore.execution;
            },
            finalApiUrl() {
                return apiUrl();
            },
            canDelete() {
                return this.execution && this.authStore.user?.isAllowed(permission.EXECUTION, action.DELETE, this.execution.namespace);
            },
            isAllowedEdit() {
                return this.execution && this.authStore.user?.isAllowed(permission.FLOW, action.UPDATE, this.execution.namespace);
            },
            isAllowedTrigger() {
                return this.execution && this.authStore.user?.isAllowed(permission.EXECUTION, action.CREATE, this.execution.namespace);
            },
            isATestExecution() {
                return this.execution && this.execution.labels && this.execution.labels.some(label => label.key === "system.test" && label.value === "true");
            }
        },
        methods: {
            editFlow() {
                this.$router.push({
                    name: "flows/update", params: {
                        namespace: this.$route.params.namespace,
                        id: this.$route.params.flowId,
                        tab: "edit",
                        tenant: this.$route.params.tenant
                    }
                })
            },
            deleteExecution() {
                if (this.execution) {
                    const item = this.execution;

                    let message = this.$t("delete confirm", {name: item.id});
                    if (State.isRunning(this.execution.state.current)) {
                        message += this.$t("delete execution running");
                    }

                    const deleteLogs = ref(true);
                    const deleteMetrics = ref(true);
                    const deleteStorage = ref(true);

                    ElMessageBox({
                        boxType: "confirm",
                        title: this.$t("confirmation"),
                        showCancelButton: true,
                        customStyle: "min-width: 600px",
                        callback: (value) => {
                            if(value === "confirm") {
                                return this.executionsStore
                                    .deleteExecution({
                                        ...item,
                                        deleteLogs: deleteLogs.value,
                                        deleteMetrics: deleteMetrics.value,
                                        deleteStorage: deleteStorage.value
                                    })
                                    .then(() => {
                                        return this.$router.push({
                                            name: "executions/list",
                                            tenant: this.$route.params.tenant
                                        });
                                    })
                                    .then(() => {
                                        this.$toast().deleted(item.id);
                                    })
                            }
                        },
                        message: () => h("div", null, [
                            h("p", {class: "pb-3"}, [h("span", {innerHTML: message})]),
                            h(ElCheckbox, {
                                modelValue: deleteLogs.value,
                                label: this.$t("execution_deletion.logs"),
                                "onUpdate:modelValue": (val) => (deleteLogs.value = val),
                            }),
                            h(ElCheckbox, {
                                modelValue: deleteMetrics.value,
                                label: this.$t("execution_deletion.metrics"),
                                "onUpdate:modelValue": (val) => (deleteMetrics.value = val),
                            }),
                            h(ElCheckbox, {
                                modelValue: deleteStorage.value,
                                label: this.$t("execution_deletion.storage"),
                                "onUpdate:modelValue": (val) => (deleteStorage.value = val),
                            }),
                        ])
                    })

                    return;
                }
            },
        }
    };
</script>
<style>

@media (max-width: 575.98px) {
  .sm-extra-padding {
    padding: 0;
  }
}

</style>