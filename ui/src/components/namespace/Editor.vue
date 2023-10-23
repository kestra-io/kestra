<template>
    <top-nav-bar :title="routeInfo.title">
        <template #additional-right>
            <namespace-select class="fit-content"
                data-type="flow"
                              :value="namespace"
                              @update:model-value="namespaceUpdate"/>
        </template>
    </top-nav-bar>
    <iframe
        style="visibility:hidden;"
        onload="this.style.visibility = 'visible';"
        v-if="namespace"
        class="vscode-editor"
        :src="vscodeIndexUrl"
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
            }
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