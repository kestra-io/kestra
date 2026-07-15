<template>
    <ContextInfoContent :title="routeInfo.title" ref="contextInfoRef">
        <template v-if="isOnline && !isHomepage" #back-button>
            <KsButton
                class="back-button"
                nativeType="button"
                @click="goBack"
                :disabled="!canGoBack"
                :class="{disabled: !canGoBack}"
                :aria-label="$t('common.back')"
            >
                <ChevronLeft class="back-icon" aria-hidden="true" />
            </KsButton>
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
                <div class="docs-toolbar">
                    <ContextDocsSearch />
                    <DocsMenu />
                </div>
                <DocsLayout>
                    <template #content>
                        <KsMarkdown
                            class="markdown"
                            :content="markdownContent"
                            :components="markdownComponents"
                        />
                    </template>
                </DocsLayout>
            </template>
            <KsMarkdown v-else :content="OFFLINE_MD" class="m-3" />
        </div>
    </ContextInfoContent>
</template>

<script setup lang="ts">
    import {ref, watch, computed, onUnmounted, onMounted} from "vue"
    import {useDocStore} from "../../stores/doc"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue"
    import DocsLayout from "./DocsLayout.vue"
    import ContextDocsLink from "./ContextDocsLink.vue"
    import ContextChildCard from "./ContextChildCard.vue"
    import DocsMenu from "./ContextDocsMenu.vue"
    import ContextDocsSearch from "./ContextDocsSearch.vue"
    import ContextInfoContent from "../ContextInfoContent.vue"
    import ContextChildTableOfContents from "./ContextChildTableOfContents.vue"
    import {removeMDXImports, extractMultilineJSXComponents, replaceSelfClosingTagsWithOpenClose} from "./docsUtils"

    import {useI18n} from "vue-i18n"
    const {t} = useI18n({useScope: "global"})

    import {useNetwork} from "@vueuse/core"
    import {useScrollMemory} from "../../composables/useScrollMemory"
    const {isOnline} = useNetwork()

    import {KsButton, KsMarkdown} from "@kestra-io/design-system"
    import PluginCount from "./PluginCount.vue"
    import WhatsNew from "../content/WhatsNew.vue"
    import SupportLinks from "../content/SupportLinks.vue"
    import BigChildCards from "../content/BigChildCards.vue"
    import CardLogos from "../content/CardLogos.vue"
    import ChildReleases from "../content/ChildReleases.vue"
    import DownloadLogoPack from "../content/DownloadLogoPack.vue"
    import HomePageButtons from "../content/HomePageButtons.vue"
    import HomePageHeader from "../content/HomePageHeader.vue"
    import ProseImg from "../content/ProseImg.vue"

    const markdownComponents = {
        a: ContextDocsLink,
        img: ProseImg,
        BigChildCards: BigChildCards,
        CardLogos: CardLogos,
        ChildCard: ContextChildCard,
        ChildReleases: ChildReleases,
        ChildTableOfContents: ContextChildTableOfContents,
        DownloadLogoPack: DownloadLogoPack,
        GuidesChildCard: ContextChildCard,
        HomePageButtons: HomePageButtons,
        HomePageHeader: HomePageHeader,
        PluginCount: PluginCount,
        SupportLinks: SupportLinks,
        WhatsNew: WhatsNew,
    }

    const OFFLINE_MD = "You're seeing this because you are offline.\n\nHere's how to configure the right sidebar in Kestra to include custom links:\n\n```yaml\nkestra:\n  ee:\n    right-sidebar:\n      custom-links:\n        internal-docs:\n          title: \"Internal Docs\"\n          url: \"https://kestra.io/docs/\"\n        support-portal:\n          title: \"Support portal\"\n          url: \"https://kestra.io/support/\"\n```"

    const docStore = useDocStore()

    const contextInfoRef = ref<InstanceType<typeof ContextInfoContent> | null>(null)
    const docHistory = ref<string[]>([])
    const currentHistoryIndex = ref(-1)
    const markdownContent = ref<string>("")

    const pageMetadata = computed(() => docStore.pageMetadata)
    const isHomepage = computed(() => pageMetadata.value?.isHomepage === true)
    const docPath = computed(() => docStore.docPath)

    const routeInfo = computed(() => ({
        title: pageMetadata.value?.title ?? t("docs"),
    }))
    const canGoBack = computed(() => docHistory.value.length > 1 && currentHistoryIndex.value > 0)
    const addToHistory = (path: string) => {
        // Always store the path, even empty ones
        const pathToAdd = path || ""

        if (docHistory.value.length === 0) {
            docHistory.value = [pathToAdd]
            currentHistoryIndex.value = 0
            return
        }

        if (pathToAdd !== docHistory.value[currentHistoryIndex.value]) {
            docHistory.value = docHistory.value.slice(0, currentHistoryIndex.value + 1)
            docHistory.value.push(pathToAdd)
            currentHistoryIndex.value = docHistory.value.length - 1
        }
    }

    const goBack = () => {
        if (!canGoBack.value) return
        currentHistoryIndex.value--
        docStore.docPath = docHistory.value[currentHistoryIndex.value]
    }

    async function setDocPageFromResponse(response: {metadata?: any, content:string}) {
        docStore.pageMetadata = response.metadata
        let content = response.content
        if (!("canShare" in navigator)) {
            content = content.replaceAll(/\s*web-share\s*/g, "")
        }

        content = removeMDXImports(content)

        const {content: cleanedContent, removedComponents: _} = extractMultilineJSXComponents(content)

        markdownContent.value = replaceSelfClosingTagsWithOpenClose(cleanedContent)
    }

    async function fetchDefaultDocFromDocIdIfPossible() {
        if(!isOnline.value) return

        try {
            if(!docStore.docId) {
                refreshPage()
                return
            }
            const response = await docStore.fetchDocId(docStore.docId)
            if (response) {
                await setDocPageFromResponse(response)
                // Add the default page to history
                addToHistory("docs")
            } else {
                refreshPage()
            }
        } catch {
            refreshPage()
        }
    }

    async function refreshPage(val?: string) {
        let response: {metadata?: any, content:string} | undefined = undefined
        // if this fails to return a value, fetch the default doc
        // if nothing, fetch the home page
        if(response === undefined){
            response = await docStore.fetchResource(val || "docs")
        }

        await setDocPageFromResponse(response)
        // Always add to history, empty string for home/default page
        addToHistory(val || "docs")
    }

    onMounted(() => {
        if (!docPath.value) {
            fetchDefaultDocFromDocIdIfPossible()
        }
    })

    onUnmounted(() => {
        markdownContent.value = ""
    })

    watch(() => docStore.docPath, async (val) => {
        if (!val?.length) {
            fetchDefaultDocFromDocIdIfPossible()
            return
        }

        addToHistory(val)
        refreshPage(val)
    }, {immediate: true})

    const scrollableElement = computed(() => contextInfoRef.value?.contentRef ?? null)
    useScrollMemory(ref("context-panel-docs"), scrollableElement as any)
</script>

<style scoped lang="scss">

    .back-button {
        background: var(--ks-btn-secondary-bg-default);
        border: 0.5px solid var(--ks-btn-secondary-border-default);
        box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        gap: 4px;
        color: var(--ks-text-primary);
        border-radius: 8px;
        width: 30px;
        height: 32px;
        transition: all 0.2s ease;
        padding: 4px 8px;
        flex-shrink: 0;

        &:hover:not(.disabled),
        &:focus:not(.disabled) {
            background: var(--ks-bg-hover);
            border-color: var(--ks-border-strong);
            color: var(--ks-primary);
            outline: none;
        }

        &.disabled {
            cursor: not-allowed;
            opacity: 0.5;
        }
    }

    .back-icon {
        display: inline-flex;
        align-items: center;
        justify-content: center;
        font-size: var(--ks-font-size-md);
    }

    .blank {
        margin-left: 1rem;
        color: var(--ks-text-dim);
    }

    .docs-controls {
        display: flex;
        flex-direction: column;
        gap: 1rem;
        margin-bottom: 1rem;
    }

    .docs-toolbar {
        position: relative;
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 12px 28px 0;
    }

    .markdown :deep(p:first-child:not(.kel-alert *)) {
        margin-bottom: var(--ks-spacing-4);
        font-weight: bold;
        font-size: var(--ks-font-size-lg);
    }
</style>
