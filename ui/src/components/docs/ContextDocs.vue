<template>
    <context-info-content :title="routeInfo.title">
        <template #header>
            <router-link
                :to="{
                    name: 'docs/view',
                    params:{
                        path:docPath
                    }
                }"
                target="_blank"
            >
                <OpenInNew class="blank" />
            </router-link>
        </template>
        <div ref="docWrapper">
            <docs-menu />
            <docs-layout>
                <template #content>
                    <MDCRenderer v-if="ast?.body" :body="ast.body" :data="ast.data" :key="ast" :components="proseComponents" />
                </template>
            </docs-layout>
        </div>
    </context-info-content>
</template>

<script lang="ts" setup>
    import {ref, watch, computed, getCurrentInstance,  onUnmounted, nextTick} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";

    import useMarkdownParser from "@kestra-io/ui-libs/src/composables/useMarkdownParser";
    import MDCRenderer from "@kestra-io/ui-libs/src/components/content/MDCRenderer.vue";
    import DocsLayout from "./DocsLayout.vue";
    import ContextDocsLink from "./ContextDocsLink.vue";
    import ContextChildCard from "./ContextChildCard.vue";
    import DocsMenu from "./ContextDocsMenu.vue";
    import ContextInfoContent from "../ContextInfoContent.vue";

    const parse = useMarkdownParser();
    const store = useStore();
    const {t} = useI18n();

    const docWrapper = ref<HTMLDivElement | null>(null);

    const pageMetadata = computed(() => store.getters["doc/pageMetadata"]);
    const docPath = computed(() => store.getters["doc/docPath"]);
    const routeInfo = computed(() => ({
        title: pageMetadata.value?.title ?? t("docs"),
    }))

    onUnmounted(() => {
        ast.value = undefined
        store.commit("doc/setDocPath", undefined);
    });

    const ast = ref<any>(undefined);
    const proseComponents = Object.fromEntries(
        [...Object.keys(getCurrentInstance()?.appContext.components ?? {})
            .filter(componentName => componentName.startsWith("Prose"))
            .map(name => name.substring(5).replaceAll(/(.)([A-Z])/g, "$1-$2").toLowerCase())
            .map(name => [name, "prose-" + name]), ["a", ContextDocsLink], ["ChildCard", ContextChildCard]]
    );


    watch(docPath, async (val) => {
        refreshPage(val);
        nextTick(() => {
            docWrapper.value?.scrollTo(0, 0);
        });
    }, {immediate: true});

    async function refreshPage(val) {
        const response = await store.dispatch("doc/fetchResource", `docs${val === undefined ? "" : val}`);
        await store.commit("doc/setPageMetadata", response.metadata);
        let content = response.content;
        if (!("canShare" in navigator)) {
            content = content.replaceAll(/\s*web-share\s*/g, "");
        }
        ast.value = await parse(content);
    }
</script>

<style lang="scss" scoped>
    .blank {
        margin-top: 4px;
        margin-left: var(--spacer);
        color: var(--bs-tertiary-color);
    }
</style>