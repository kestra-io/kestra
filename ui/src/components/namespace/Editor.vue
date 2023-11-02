<template>
    <top-nav-bar :title="routeInfo.title">
        <template #additional-right>
            <namespace-select
                class="fit-content"
                data-type="flow"
                :value="namespace"
                @update:model-value="namespaceUpdate"
                allow-create
            />
            <trigger-flow
                :disabled="!flow"
                :flow-id="flow"
                :namespace="namespace"
            />
        </template>
    </top-nav-bar>
    <iframe
        style="visibility:hidden;"
        onload="this.style.visibility = 'visible';"
        v-if="namespace"
        class="vscode-editor"
        :src="vscodeIndexUrl"
        ref="vscodeIde"
    />
    <div v-else class="m-3 mw-100">
        <el-alert type="info" :closable="false">
            {{ $t("namespace choice") }}
        </el-alert>
    </div>
</template>

<script setup>
    import NamespaceSelect from "./NamespaceSelect.vue";
    import TopNavBar from "../layout/TopNavBar.vue";
    import TriggerFlow from "../flows/TriggerFlow.vue";
</script>

<script>
    import RouteContext from "../../mixins/routeContext";
    import RestoreUrl from "../../mixins/restoreUrl";
    import {apiUrl} from "override/utils/route";

    export default {
        mixins: [RouteContext, RestoreUrl],
        methods: {
            namespaceUpdate(namespace) {
                this.$router.push({
                    params: {
                        namespace
                    }
                });
            },
            handleTabsDirty(tabs) {
                // Add tabs not saved
                this.tabsNotSaved = this.tabsNotSaved.concat(tabs.dirty)
                // Removed tabs closed
                this.tabsNotSaved = this.tabsNotSaved.filter(e => !tabs.closed.includes(e))
                this.$store.dispatch("core/isUnsaved", this.tabsNotSaved.length > 0);
            }
        },
        data() {
            return {
                flow: null,
                tabsNotSaved: []
            }
        },
        created() {
            const namespace = localStorage.getItem("defaultNamespace");
            if (namespace) {
                this.namespaceUpdate(namespace);
            }
        },
        mounted() {
            window.addEventListener("message", (event) => {
                const message = event.data;
                if (message.type === "kestra.tabFileChanged") {
                    const path = `/${this.namespace}/_flows/`;
                    if (message.filePath.path.startsWith(path)) {
                        this.flow = message.filePath.path.split(path)[1].replace(".yml", "");
                    } else {
                        this.flow = null;
                    }
                } else if (message.type === "kestra.tabsChanged") {
                    this.handleTabsDirty(message.tabs);
                }
            });
        },
        computed: {
            routeInfo() {
                return {
                    title: this.$t("editor")
                };
            },
            theme() {
                return localStorage.getItem("theme") || "light";
            },
            vscodeIndexUrl() {
                const uiSubpath = KESTRA_UI_PATH === "./" ? "/" : KESTRA_UI_PATH;
                return `${uiSubpath}vscode.html?KESTRA_UI_PATH=${uiSubpath}&KESTRA_API_URL=${apiUrl(this.$store)}&THEME=${this.theme}&namespace=${this.namespace}`;
            },
            namespace() {
                return this.$route.params.namespace;
            }
        }
    }
</script>

<style lang="scss">
    .fit-content {
        width: fit-content;
    }
    .vscode-editor {
        height: calc(100vh - 64.8px);
        width: 100%;
    }
</style>