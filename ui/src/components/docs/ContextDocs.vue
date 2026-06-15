<template>
    <ContextInfoContent :title="routeInfo.title" ref="contextInfoRef">
        <template v-if="isOnline" #back-button>
            <KsButton
                class="back-button"
                nativeType="button"
                @click="goBack"
                :disabled="!canGoBack"
                :class="{disabled: !canGoBack}"
                :aria-label="$t('common.back')"
            >
                <span class="back-icon" aria-hidden="true">‹</span>
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
                <ContextDocsSearch />
                <DocsMenu />
                <DocsLayout>
                    <template #content>
                        <KsMarkdown 
                            class="markdown" 
                            :content="markdownContent" 
                            :xssProtection="false" 
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
    import DocsLayout from "./DocsLayout.vue"
    import ContextDocsLink from "./ContextDocsLink.vue"
    import ContextChildCard from "./ContextChildCard.vue"
    import DocsMenu from "./ContextDocsMenu.vue"
    import ContextDocsSearch from "./ContextDocsSearch.vue"
    import ContextInfoContent from "../ContextInfoContent.vue"
    import ContextChildTableOfContents from "./ContextChildTableOfContents.vue"

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
    import GuidesChildCard from "../content/GuidesChildCard.vue"
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
        GuidesChildCard: GuidesChildCard,
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

    function removeMDXImports(content: string): string {
        // we want to only remove lines that are not in a code block
        // so we isolate code blocks first
        const contentArray = content.split("```")
        for(let i = 0; i < contentArray.length; i++){
            // if the index is even, it's outside a code block
            if(i % 2 === 0){
                // remove lines that start with `import`
                // to keep compatibility with mdx files
                // without splitting and rejoining since it would
                // create huge arrays just to destroy them right after
                contentArray[i] = contentArray[i].replaceAll(/import [\s\S]+? from ['"][\s\S]+?['"];?/g, "")
            }
        }
        return contentArray.join("```")
    }

    function extractMultilineJSXComponents(content: string) {
        // first, find every line that start with < and a capital letter, and that doesn't end with />
        const lines = content.split("\n")
        const linesToRemove: number[] = []
        const removedComponents: Record<number, string> = {}
        let startOfBlockLine = -1
        let componentName = ""
        let insideCodeBlock = false
        let currentBlockLines: number[] = []

        for(let i = 0; i < lines.length; i++){
            if(insideCodeBlock){
                if(lines[i].match(/^```/)){
                    insideCodeBlock = false
                }
                continue
            } else {
                if(lines[i].match(/^```/)){
                    insideCodeBlock = true
                    continue
                }
            }

            if(startOfBlockLine > -1){
                // if an empty line appears, MDX will consider it a stop in the JSX
                if(lines[i].trim() === ""){
                    startOfBlockLine = -1
                    componentName = ""
                    currentBlockLines = []
                    continue
                }

                currentBlockLines.push(i)

                // if we have started a block, let's check if this line is the end of it.
                // if so, we remove it and stop the next iterations until we find a new block
                if(lines[i].match(/^\/>/)){
                    removedComponents[startOfBlockLine] = lines.slice(startOfBlockLine, i).join("\n") + `\n></${componentName}>`
                    startOfBlockLine = -1
                    componentName = ""
                    // and only once we are sure the block is closed,
                    // do we add the lines to remove
                    linesToRemove.push(...currentBlockLines)
                    currentBlockLines = []
                }
            }

            if(lines[i].match(/^<([A-Z][\w]*)\b(?![^>]*\/>).*$/)){
                componentName = lines[i].match(/^<([A-Z][\w]*)/)?.[1] ?? ""
                startOfBlockLine = i
            }
        }

        // in place of each removed block, we add a placeholder with the component name to keep track of where it was in the doc
        for(const lineIndex in removedComponents){
            lines[lineIndex] = `<!-- ${removedComponents[lineIndex]} -->`
        }
        return {
            content: lines.filter((_, i) => !linesToRemove.includes(i)).join("\n"),
            removedComponents: removedComponents,
        }
    }

    function replaceSelfClosingTagsWithOpenClose(content: string): string {
        // we want to replace every self closing tag with an open and close tag
        // to keep compatibility with mdx files that use self closing tags for custom components
        return content.replaceAll(/<([A-Z][\w]*)\b([^>]*)\/>/g, "<$1$2></$1>\n")
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
        background: var(--ks-bg-surface);
        border: 1px solid var(--ks-border-color);
        cursor: pointer;
        display: inline-flex;
        align-items: center;
        justify-content: center;
        color: var(--ks-text-primary);
        border-radius: 6px;
        width: 40px;
        height: 40px;
        transition: all 0.2s ease;
        padding: 0;
        flex-shrink: 0;

        &:hover:not(.disabled),
        &:focus:not(.disabled) {
            background: var(--ks-bg-hover);
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
        color: var(--ks-text-dim);
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
