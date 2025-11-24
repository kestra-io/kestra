<template>
    <TopNavBar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb">
        <template #title>
            {{ routeInfo?.title }}
            <Badge v-if="isATestExecution" :label="$t('test-badge-text')" :tooltip="$t('test-badge-tooltip')" />
        </template>
        <template #additional-right>
            <slot name="additional-right" />
            <div class="d-flex align-items-center gap-2" v-if="(isAllowedEdit || isAllowedTrigger) && $route.params.tab !== 'audit-logs'">
                <ul class="d-none d-xl-flex align-items-center">
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
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import DotsVerticalIcon from "vue-material-design-icons/DotsVertical.vue";
    import Badge from "../global/Badge.vue";
</script>

<script>
    import {mapStores} from "pinia";

    import TriggerFlow from "../flows/TriggerFlow.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
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
            }
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