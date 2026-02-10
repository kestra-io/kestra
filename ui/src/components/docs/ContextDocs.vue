<template>
    <ContextInfoContent :title="routeInfo.title" ref="contextInfoRef">
        <template v-if="isOnline" #back-button>
            <button
                class="back-button"
                type="button"
                @click="goBack"
                :disabled="!canGoBack"
                :class="{disabled: !canGoBack}"
                :aria-label="$t('common.back')"
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
                :aria-label="$t('common.openInNewTab')"
            >
                <OpenInNew class="blank" />
            </router-link>
        </template>
        <div class="docs-controls">
            <template v-if="isOnline">
                <ContextDocsSearch />
                <DocsMenu />
                <DocsLayout>
                    <template #content>
                        <MDCRenderer v-if="ast?.body" :body="ast.body" :data="ast.data" :key="ast" :components="proseComponents" />
                    </template>
                </DocsLayout>
            </template>
            <Markdown v-else :source="OFFLINE_MD" class="m-3" />
        </div>
    </ContextInfoContent>
</template>

<script setup lang="ts">
    import {ref, watch, computed, getCurrentInstance, onUnmounted, onMounted} from "vue";
    import {useDocStore} from "../../stores/doc";
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import {MDCRenderer, getMDCParser} from "@kestra-io/ui-libs";
    import DocsLayout from "./DocsLayout.vue";
    import ContextDocsLink from "./ContextDocsLink.vue";
    import ContextChildCard from "./ContextChildCard.vue";
    import DocsMenu from "./ContextDocsMenu.vue";
    import ContextDocsSearch from "./ContextDocsSearch.vue";
    import ContextInfoContent from "../ContextInfoContent.vue";
    import ContextChildTableOfContents from "./ContextChildTableOfContents.vue";

    import {useI18n} from "vue-i18n";
    const {t} = useI18n({useScope: "global"});

    import {useNetwork} from "@vueuse/core"
    import {useScrollMemory} from "../../composables/useScrollMemory"
    const {isOnline} = useNetwork()
    
    import Markdown from "../../components/layout/Markdown.vue";
    const OFFLINE_MD = "You're seeing this because you are offline.\n\nHere's how to configure the right sidebar in Kestra to include custom links:\n\n```yaml\nkestra:\n  ee:\n    right-sidebar:\n      custom-links:\n        internal-docs:\n          title: \"Internal Docs\"\n          url: \"https://kestra.io/docs/\"\n        support-portal:\n          title: \"Support portal\"\n          url: \"https://kestra.io/support/\"\n```";

    const docStore = useDocStore();

    const contextInfoRef = ref<InstanceType<typeof ContextInfoContent> | null>(null);
    const docHistory = ref<string[]>([]);
    const currentHistoryIndex = ref(-1);
    const ast = ref<any>(undefined);

    const pageMetadata = computed(() => docStore.pageMetadata);
    const docPath = computed(() => docStore.docPath);
    
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
        docStore.docPath = docHistory.value[currentHistoryIndex.value];
    };

    function removeMDXImports(content: string): string {
        // we want to only remove lines that are not in a code block 
        // so we isolate code blocks first
        const contentArray = content.split("```");
        for(let i = 0; i < contentArray.length; i++){
            // if the index is even, it's outside a code block
            if(i % 2 === 0){
                // remove lines that start with `import` 
                // to keep compatibility with mdx files
                // without splitting and rejoining since it would 
                // create huge arrays just to destroy them right after
                contentArray[i] = contentArray[i].replaceAll(/import [\s\S]+? from ['"][\s\S]+?['"];?/g, "");
            }
        }
        return contentArray.join("```");
    }

    async function setDocPageFromResponse(response: {metadata?: any, content:string}) {
        docStore.pageMetadata = response.metadata;
        let content = response.content;
        if (!("canShare" in navigator)) {
            content = content.replaceAll(/\s*web-share\s*/g, "");
        }

        content = removeMDXImports(content);

        
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
        if(!isOnline.value) return;

        try {
            if(!docStore.docId) {
                refreshPage()
                return;
            }
            const response = await docStore.fetchDocId(docStore.docId);
            if (response) {
                await setDocPageFromResponse(response);
                // Add the default page to history
                addToHistory("docs");
            } else {
                refreshPage();
            }
        } catch {
            refreshPage();
        }
    }

    async function refreshPage(val?: string) {
        let response: {metadata?: any, content:string} | undefined = undefined;
        // if this fails to return a value, fetch the default doc
        // if nothing, fetch the home page
        if(response === undefined){
            response = await docStore.fetchResource(val || "docs");
        }

        await setDocPageFromResponse(response);
        // Always add to history, empty string for home/default page
        addToHistory(val || "docs");
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

    watch(() => docStore.docPath, async (val) => {
        if (!val?.length) {
            fetchDefaultDocFromDocIdIfPossible();
            return;
        }

        addToHistory(val);
        refreshPage(val);
    }, {immediate: true});

    const scrollableElement = computed(() => contextInfoRef.value?.contentRef ?? null)
    useScrollMemory(ref("context-panel-docs"), scrollableElement as any)
</script>

<style scoped lang="scss">

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
