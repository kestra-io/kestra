<template>
    <context-info-content :title="routeInfo.title">
        <template #back-button>
            <button 
                class="back-button" 
                type="button"
                @click="goBack" 
                :disabled="!canGoBack"
                :class="{disabled: !canGoBack}"
                :aria-label="t('common.back')"
            >
                <span class="back-icon" aria-hidden="true">‹</span>
            </button>
        </template>
        <template #header>
            <router-link
                :to="{
                    name: 'docs/view',
                    params:{
                        path:docPath
                    }
                }"
                target="_blank"
                :aria-label="t('common.openInNewTab')"
            >
                <OpenInNew class="blank" />
            </router-link>
        </template>
        <div ref="docWrapper" class="docs-controls">
            <context-docs-search />
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
    import {ref, watch, computed, getCurrentInstance, onUnmounted, onMounted, nextTick} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import {MDCRenderer, getMDCParser} from "@kestra-io/ui-libs";
    import DocsLayout from "./DocsLayout.vue";
    import ContextDocsLink from "./ContextDocsLink.vue";
    import ContextChildCard from "./ContextChildCard.vue";
    import DocsMenu from "./ContextDocsMenu.vue";
    import ContextDocsSearch from "./ContextDocsSearch.vue";
    import ContextInfoContent from "../ContextInfoContent.vue";
    import ContextChildTableOfContents from "./ContextChildTableOfContents.vue";

    const store = useStore();
    const {t} = useI18n({useScope: "global"});

    const docWrapper = ref<HTMLDivElement | null>(null);
    const docHistory = ref<string[]>([]);
    const currentHistoryIndex = ref(-1);
    const ast = ref<any>(undefined);

    const pageMetadata = computed(() => store.getters["doc/pageMetadata"]);
    const docPath = computed(() => store.getters["doc/docPath"]);
    const routeInfo = computed(() => ({
        title: pageMetadata.value?.title ?? t("docs"),
    }));
    const canGoBack = computed(() => docHistory.value.length > 1 && currentHistoryIndex.value > 0);


    const addToHistory = (path: string) => {
        // Always store the path, even empty ones
        const pathToAdd = path || "";
        
        if (docHistory.value.length === 0) {
            docHistory.value = [pathToAdd];
            currentHistoryIndex.value = 0;
            return;
        }
        
        if (pathToAdd !== docHistory.value[currentHistoryIndex.value]) {
            docHistory.value = docHistory.value.slice(0, currentHistoryIndex.value + 1);
            docHistory.value.push(pathToAdd);
            currentHistoryIndex.value = docHistory.value.length - 1;
        }
    };

    const goBack = () => {
        if (!canGoBack.value) return;
        currentHistoryIndex.value--;
        store.commit("doc/setDocPath", docHistory.value[currentHistoryIndex.value]);
    };

    async function setDocPageFromResponse(response){
        await store.commit("doc/setPageMetadata", response.metadata);
        let content = response.content;
        if (!("canShare" in navigator)) {
            content = content.replaceAll(/\s*web-share\s*/g, "");
        }

        const parse = await getMDCParser();
        // this hack alleviates a little the parsing load of the first render on big docs
        // by only rendering the first 50 lines of the doc on opening
        // since they are the only ones visible in the beginning
        const firstLinesOfContent = content.split("---\n")[2].split("\n").slice(0, 50).join("\n") + "\nLoading the rest...\n";
        ast.value = await parse(firstLinesOfContent);
        
        setTimeout(async () => {
            ast.value = await parse(content);
        }, 50);
    }

    async function fetchDefaultDocFromDocIdIfPossible() {
        try {
            const response = await store.dispatch("doc/fetchDocId", store.state.doc.docId);
            if (response) {
                await setDocPageFromResponse(response);
                // Add the default page to history
                addToHistory("");
            } else {
                refreshPage("");
            }
        } catch {
            refreshPage("");
        }
    }

    async function refreshPage(val) {
        let response: {metadata: any, content:string} | undefined = undefined;
        // if this fails to return a value, fetch the default doc
        // if nothing, fetch the home page
        if(response === undefined){
            response = await store.dispatch("doc/fetchResource", `docs${val ?? ""}`)
        }
        if(response === undefined){
            return;
        }
        await setDocPageFromResponse(response);
        // Always add to history, empty string for home/default page
        addToHistory(val || "");
    }

    const proseComponents = Object.fromEntries([
        ...Object.keys(getCurrentInstance()?.appContext.components ?? {})
            .filter(name => name.startsWith("Prose"))
            .map(name => name.substring(5).replaceAll(/(.)([A-Z])/g, "$1-$2").toLowerCase())
            .map(name => [name, "prose-" + name]),
        ["a", ContextDocsLink],
        ["ChildCard", ContextChildCard],
        ["ChildTableOfContents", ContextChildTableOfContents]
    ]);

    onMounted(() => {
        if (!docPath.value) {
            fetchDefaultDocFromDocIdIfPossible();
        }
    });

    onUnmounted(() => {
        ast.value = undefined;
    });

    watch(() => store.getters["doc/docPath"], async (val) => {
        if (!val?.length) {
            fetchDefaultDocFromDocIdIfPossible();
            return;
        }

        addToHistory(val);
        refreshPage(val);
        nextTick(() => docWrapper.value?.scrollTo(0, 0));
    }, {immediate: true});
</script>

<style lang="scss" scoped>

    .back-button {
        background: var(--ks-background-card);
        border: 1px solid var(--ks-border-color);
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: var(--ks-content-primary);
        border-radius: 6px;
        width: 40px;
        height: 40px;
        transition: all 0.2s ease;
        padding: 0;
        flex-shrink: 0;
        
        &:hover:not(.disabled),
        &:focus:not(.disabled) {
            background: var(--ks-background-hover);
            border-color: var(--ks-primary);
            color: var(--ks-primary);
            outline: none;
        }

        &.disabled {
            cursor: not-allowed;
            opacity: 0.5;
        }
    }

    .back-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        user-select: none;
        font-size: 28px;
        line-height: 0;
        margin-top: -6px;
        width: 28px;
        height: 28px;
    }

    .blank {
        margin-left: 1rem;
        color: var(--ks-content-tertiary);
    }

    .docs-controls {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        margin-bottom: 1rem;

        > * {
            margin-bottom: 1rem;
        }
    }
</style>