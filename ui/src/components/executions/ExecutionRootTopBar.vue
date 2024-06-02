<template>
    <top-nav-bar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb">
        <template #additional-right v-if="canDelete || isAllowedTrigger || isAllowedEdit">
            <ul>
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
                <li v-if="isAllowedTrigger">
                    <trigger-flow type="primary" :flow-id="$route.params.flowId" :namespace="$route.params.namespace" />
                </li>
            </ul>
        </template>
    </top-nav-bar>
</template>

<script setup>
    import Api from "vue-material-design-icons/Api.vue";
    import Delete from "vue-material-design-icons/Delete.vue";
    import Pencil from "vue-material-design-icons/Pencil.vue";
</script>

<script>
    import TriggerFlow from "../flows/TriggerFlow.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import State from "../../utils/state";
    import {apiUrl} from "override/utils/route";
    import {mapState} from "vuex";

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
            ...mapState("execution", ["execution"]),
            ...mapState("auth", ["user"]),
            finalApiUrl() {
                return apiUrl(this.$store);
            },
            canDelete() {
                return this.user && this.execution && this.user.isAllowed(permission.EXECUTION, action.DELETE, this.execution.namespace);
            },
            isAllowedEdit() {
                return this.user && this.execution && this.user.isAllowed(permission.FLOW, action.UPDATE, this.execution.namespace);
            },
            isAllowedTrigger() {
                return this.user && this.execution && this.user.isAllowed(permission.EXECUTION, action.CREATE, this.execution.namespace);
            },
        },
        methods: {
            editFlow() {
                this.$router.push({
                    name: "flows/update", params: {
                        namespace: this.$route.params.namespace,
                        id: this.$route.params.flowId,
                        tab: "editor",
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

                    this.$toast()
                        .confirm(message, () => {
                            return this.$store
                                .dispatch("execution/deleteExecution", item)
                                .then(() => {
                                    return this.$router.push({
                                        name: "executions/list",
                                        tenant: this.$route.params.tenant
                                    });
                                })
                                .then(() => {
                                    this.$toast().deleted(item.id);
                                })
                        });
                }
            },
        }
    };
</script>
<style>
</style>