<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb">
        <template #additional-right>
            <ul v-if="$route.params.tab === 'kv'">
                <li>
                    <el-button :icon="Plus" type="primary" @click="modalAddKvVisible = true">
                        {{ $t('kv.add') }}
                    </el-button>
                </li>
            </ul>
            <ul v-if="$route.params.tab === 'flows'">
                <li>
                    <router-link :to="{name: 'flows/create'}" v-if="canCreateFlow">
                        <el-button :icon="Plus" type="primary">
                            {{ $t('create') }}
                        </el-button>
                    </router-link>
                </li>
            </ul>
        </template>
    </top-nav-bar>
    <tabs :route-name="$route.param && $route.param.id ? 'namespaces/update' : ''" :tabs="tabs" :namespace="$route.params.id" />
</template>

<script setup>
    import TopNavBar from "../layout/TopNavBar.vue";
    import Plus from "vue-material-design-icons/Plus.vue";
</script>

<script>
    import NamespaceDependenciesWrapper from "./NamespaceDependenciesWrapper.vue";
    import Tabs from "../Tabs.vue";
    import RouteContext from "../../mixins/routeContext";
    import {mapState} from "vuex";
    import permission from "../../models/permission";
    import action from "../../models/action";
    import Overview from "./Overview.vue";
    import Executions from "./Executions.vue";
    import NamespaceKV from "./NamespaceKV.vue";
    import Flows from "./Flows.vue";
    import EditorView from "../inputs/EditorView.vue";
    import BlueprintsBrowser from "../../override/components/flows/blueprints/BlueprintsBrowser.vue";
    import DemoNamespace from "../demo/Namespace.vue";

    export default {
        mixins: [RouteContext],
        components: {
            Tabs
        },
        data() {
            return {
                modalAddSecretVisible: false,
                modalInheritedSecretsVisible: false,
                modalBindingsVisible: false,
                modalAddKvVisible: false,
            }
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapState("namespace", ["dependencies"]),
            canCreateSecret() {
                return this.$route.params.id && this.user &&
                    this.user.isAllowed(permission.SECRET, action.CREATE, this.$route.params.id);
            },
            canCreateKv() {
                return this.$route.params.id;
            },
            canCreateFlow() {
                return this.user && this.user.hasAnyActionOnAnyNamespace(permission.FLOW, action.CREATE);
            },
            routeInfo() {
                return {
                    title: this.$route.params.id || this.$t("namespaces"),
                    breadcrumb: [
                        {
                            label: this.$t("namespaces"),
                            link: {
                                name: "namespaces"
                            }
                        }
                    ]
                };
            },
            tabs() {
                const tabs = [];

                if(this.$route.params.id === "system"){
                    tabs.push({
                        name: "blueprints",
                        component: BlueprintsBrowser,
                        title: this.$t("blueprints.title"),
                        props: {
                            embed: this.embed,
                            system: true,
                            tab: "community"
                        }
                    })
                }

                tabs.push(...[
                    {
                        name: undefined,
                        component: Overview,
                        title: this.$t("overview"),
                        containerClass: "full-container flex-grow-0 flex-shrink-0 flex-basis-0",
                        query: {
                            id: this.$route.query.id
                        }
                    },
                    {
                        name: "files",
                        component: EditorView,
                        title: this.$t("files"),
                        props: {
                            tab: "files",
                            isNamespace: true,
                            namespace: this.$route.params.id,
                            isReadOnly: false,
                        },
                        query: {
                            id: this.$route.query.id
                        }
                    },
                    {
                        name: "flows",
                        component: Flows,
                        title: this.$t("flows"),
                        props: {
                            tab: "flows",
                            embed: true,
                        },
                        query: {
                            id: this.$route.query.id
                        }
                    },
                    {
                        name: "executions",
                        component: Executions,
                        props: {
                            embed: false,
                        },
                        title: this.$t("executions"),
                        query: {
                            id: this.$route.query.id
                        }
                    },
                    {
                        name: "dependencies",
                        component: NamespaceDependenciesWrapper,
                        title: this.$t("dependencies"),
                        props: {
                            type: "dependencies",
                            tab: "dependencies",
                        },
                        query: {
                            id: this.$route.query.id
                        }
                    },
                    {
                        name: "kv",
                        component: NamespaceKV,
                        title: this.$t("kv.name"),
                        props: {
                            addKvModalVisible: this.modalAddKvVisible,
                        },
                        "v-on": {
                            "update:addKvModalVisible": (value) => {
                                this.modalAddKvVisible = value
                            }
                        }
                    },
                    {
                        name: "edit",
                        component: DemoNamespace,
                        title: this.$t("edit"),
                        maximize: true,
                        props: {
                            tab: "edit",
                        },
                        locked: true
                    },
                    {
                        name: "variables",
                        component: DemoNamespace,
                        title: this.$t("variables"),
                        maximize: true,
                        props: {
                            tab: "variables",
                        },
                        locked: true
                    },
                    {
                        name: "plugin-defaults",
                        component: DemoNamespace,
                        title: this.$t("plugin defaults"),
                        maximize: true,
                        props: {
                            tab: "plugin-defaults",
                        },
                        locked: true
                    },
                    {
                        name: "secrets",
                        component: DemoNamespace,
                        title: this.$t("secret.names"),
                        maximize: true,
                        props: {
                            tab: "secrets",
                        },
                        locked: true
                    },
                    {
                        name: "audit-logs",
                        component: DemoNamespace,
                        title: this.$t("auditlogs"),
                        maximize: true,
                        props: {
                            tab: "audit-logs",
                        },
                        locked: true
                    }
                ])

                return tabs;
            }
        },
        mounted () {
            this.loadItem()
        },
        methods: {
            loadItem() {
                if (this.$route.params.id) {
                    this.$store.dispatch("namespace/load",this.$route.params.id)
                }
            },
        }
    };
</script>

<style lang="scss">
section#namespaces div {
    &:has(div.namespace-form) {
        display: flex;
    }
}
</style>