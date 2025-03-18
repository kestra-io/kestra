<template>
    <top-nav-bar :title="routeInfo.title" :breadcrumb="routeInfo.breadcrumb" />
    <docs-layout>
        <template #menu>
            <Toc />
        </template>
        <template #content>
            <template v-if="ast?.body">
                <h1>{{ routeInfo.title }}</h1>
                <MDCRenderer :body="ast.body" :data="ast.data" :key="ast" :components="proseComponents" />
            </template>
        </template>
    </docs-layout>
</template>

<script>
    import {MDCRenderer, getMDCParser} from "@kestra-io/ui-libs";
    import TopNavBar from "../layout/TopNavBar.vue";
    import {mapGetters} from "vuex";
    import DocsLayout from "./DocsLayout.vue";
    import Toc from "./Toc.vue";
    import {getCurrentInstance} from "vue";


    export default {
        computed: {
            ...mapGetters("doc", ["pageMetadata"]),
            path() {
                let routePath = this.$route.params.path;
                return routePath && routePath.length > 0 ? routePath.replaceAll(/(^|\/)\.\//g, "$1") : undefined;
            },
            pathParts() {
                return this.path?.split("/") ?? [];
            },
            routeInfo() {
                return {
                    title: this.pageMetadata?.title ?? this.$t("docs"),
                    breadcrumb: [
                        {
                            label: this.$t("docs"),
                            link: {
                                name: "docs/view"
                            }
                        },
                        ...(this.pathParts.map((part, index) => {
                            return {
                                label: part,
                                link: {
                                    name: "docs/view",
                                    params: {
                                        path: this.pathParts.slice(0, index + 1).join("/")
                                    }
                                }
                            }
                        }))
                    ]
                };
            }
        },
        components: {DocsLayout, Toc, TopNavBar, MDCRenderer},
        data() {
            return {
                ast: undefined,
                proseComponents: Object.fromEntries(
                    Object.keys(getCurrentInstance().appContext.components).filter(componentName => componentName.startsWith("Prose"))
                        .map(name => name.substring(5).replaceAll(/(.)([A-Z])/g, "$1-$2").toLowerCase())
                        .map(name => [name, "prose-" + name])
                )
            };
        },
        watch: {
            "$route.params.path": {
                async handler() {
                    const response = await this.$store.dispatch("doc/fetchResource", `docs${this.path === undefined ? "" : `/${this.path}`}`);
                    await this.$store.commit("doc/setPageMetadata", response.metadata);
                    let content = response.content;
                    if (!("canShare" in navigator)) {
                        content = content.replaceAll(/\s*web-share\s*/g, "");
                    }
                    const parse = await getMDCParser();
                    this.ast = await parse(content);
                },
                immediate: true
            }
        }
    };
</script>
