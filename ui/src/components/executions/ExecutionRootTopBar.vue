<template>
    <top-nav-bar :title="routeInfo?.title" :breadcrumb="routeInfo?.breadcrumb">
        <template #additional-right v-if="canDelete || isAllowedTrigger || isAllowedEdit">
            <ul id="list">
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
    import {h, ref} from "vue"
    import {ElCheckbox, ElMessageBox} from "element-plus"

    import TriggerFlow from "../flows/TriggerFlow.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import {State} from "@kestra-io/ui-libs"
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
                                return this.$store
                                    .dispatch("execution/deleteExecution", {
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
@media (max-width: 768px) {
           
       
           #list {
                display:contents;
                background-color: blue;
            }
            #list  li:first-child {
                grid-row:1;
                grid-column:1;
            } 
          
            #list  li:nth-child(2){
                grid-row:1;
                grid-column:2;
            }
            #list  li:nth-child(3){
                grid-row:1;
                grid-column:3;
            }
            #list li:nth-child(4){
                grid-row:2;
                grid-column:1;
            }   
        }

</style>