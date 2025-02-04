<template>
    <top-nav-bar :breadcrumb="routeInfo.breadcrumb" :title="routeInfo.title">
        <template #title>
            <template v-if="deleted">
                <Alert class="text-warning me-2" />Deleted:&nbsp;
            </template>
            <Lock v-else-if="!isAllowedEdit" class="me-2 gray-700" />
            <span :class="{'body-color': deleted}">{{ routeInfo.title }}</span>
        </template>
        <template #additional-right v-if="displayButtons()">
            <ul>
                <li v-if="deleted">
                    <el-button :icon="BackupRestore" @click="restoreFlow()">
                        {{ $t("restore") }}
                    </el-button>
                </li>
                <li v-if="isAllowedEdit && !deleted && activeTabName !== 'edit'">
                    <el-button
                        :icon="Pencil"
                        @click="editFlow"
                        :disabled="deleted"
                    >
                        {{ $t("edit flow") }}
                    </el-button>
                </li>
                <li v-if="flow && !deleted">
                    <trigger-flow
                        type="primary"
                        :disabled="flow.disabled"
                        :flow-id="flow.id"
                        :namespace="flow.namespace"
                        :flow-source="flow.source"
                    />
                </li>
            </ul>
        </template>
    </top-nav-bar>
</template>

<script setup>
    import Pencil from "vue-material-design-icons/Pencil.vue";
    import BackupRestore from "vue-material-design-icons/BackupRestore.vue";
    import Alert from "vue-material-design-icons/Alert.vue";
    import Lock from "vue-material-design-icons/Lock.vue";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import TopNavBar from "../../components/layout/TopNavBar.vue";
    import TriggerFlow from "../../components/flows/TriggerFlow.vue";
    import {mapState} from "vuex";
    import yamlUtils from "../../utils/yamlUtils";
    import permission from "../../models/permission";
    import action from "../../models/action";

    export default {
        mixins: [RouteContext],
        components: {
            TriggerFlow,
            TopNavBar,
        },
        props: {
            routeInfo: {
                type: Object,
                required: true
            },
            deleted: {
                type: Boolean,
                required: true
            },
            isAllowedEdit: {
                type: Boolean,
                required: true
            },
            activeTabName: {
                type: String,
                required: true
            }
        },
        computed: {
            ...mapState("flow", ["flow"]),
            ...mapState("auth", ["user"]),
            canExecute() {
                if (this.flow) {
                    return this.user.isAllowed(
                        permission.EXECUTION,
                        action.CREATE,
                        this.flow.namespace,
                    );
                }
                return false;
            }
        },
        methods: {
            displayButtons() {
                return this.activeTabName && this.canExecute;
            },
            editFlow() {
                this.$router.push({
                    name: "flows/update",
                    params: {
                        namespace: this.flow.namespace,
                        id: this.flow.id,
                        tab: "edit",
                        tenant: this.$route.params.tenant,
                    },
                });
            },
            restoreFlow() {
                this.$store
                    .dispatch("flow/createFlow", {
                        flow: yamlUtils.deleteMetadata(this.flow.source, "deleted"),
                    })
                    .then((response) => {
                        this.$toast().saved(response.id);
                        this.$store.dispatch("core/isUnsaved", false);
                        this.$router.go();
                    });
            }
        },
    };
</script>