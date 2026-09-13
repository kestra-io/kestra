<template>
    <TopNavBar :title="routeInfo.title" />
    <section class="full-container flush-top">
        <DocsLayout>
            <template #menu>
                <Toc />
            </template>
            <template #content>
                <KsAlert v-if="loadError === 'failed'" type="error" :closable="false">
                    {{ $t("docsPage.loadError") }}
                </KsAlert>

                <div v-else-if="loadError === 'notFound'" class="docs-not-found">
                    <KsNoData
                        :icon="FileRemoveOutline"
                        :title="$t('errors.404.title')"
                        :description="$t('docsPage.notFound')"
                    />
                    <KsButton tag="router-link" :to="docsHome" type="primary">
                        {{ $t("docsPage.backToDocs") }}
                    </KsButton>
                </div>

                <KsSkeleton v-else-if="markdownContent === undefined" animated :rows="10" />

                <KsMarkdown v-else class="markdown" :content="markdownContent" :components="markdownComponents" />
            </template>
        </DocsLayout>
    </section>
</template>

<script setup lang="ts">
    import {computed,ref,watch} from "vue"
    import type {AxiosError} from "axios"
    import TopNavBar from "../layout/TopNavBar.vue"
    import useRouteContext from "../../composables/useRouteContext"
    import {useDocStore} from "../../stores/doc"
    import DocsLayout from "./DocsLayout.vue"
    import Toc from "./Toc.vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsAlert, KsButton, KsMarkdown, KsNoData, KsSkeleton} from "@kestra-io/design-system"
    import FileRemoveOutline from "vue-material-design-icons/FileRemoveOutline.vue"
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
    import ProseA from "../content/ProseA.vue"
    import ChildTableOfContents from "../content/ChildTableOfContents.vue"
    import ChildCard from "../content/ChildCard.vue"
    import {removeMDXImports, extractMultilineJSXComponents, replaceSelfClosingTagsWithOpenClose} from "./docsUtils"

    const markdownComponents = {
        a: ProseA,
        img: ProseImg,
        BigChildCards: BigChildCards,
        CardLogos: CardLogos,
        ChildCard: ChildCard,
        ChildReleases: ChildReleases,
        ChildTableOfContents: ChildTableOfContents,
        DownloadLogoPack: DownloadLogoPack,
        GuidesChildCard: GuidesChildCard,
        HomePageButtons: HomePageButtons,
        HomePageHeader: HomePageHeader,
        PluginCount: PluginCount,
        SupportLinks: SupportLinks,
        WhatsNew: WhatsNew,
    }

    const route = useRoute()
    const {t} = useI18n()
    const docStore = useDocStore()

    const markdownContent = ref()
    const loadError = ref<"notFound" | "failed" | undefined>()

    const path = computed(() => {
        const routePath = Array.isArray(route.params.path) ? route.params.path.join("/") : route.params.path
        return routePath?.length > 0 ? routePath.replaceAll(/(^|\/)\.\//g, "$1") : undefined
    })

    const routeInfo = computed(() => ({
        title: docStore.pageMetadata?.title ?? t("docs"),
    }))

    const docsHome = computed(() => ({name: "docs/view", params: {tenant: route.params.tenant}}))

    useRouteContext(routeInfo)

    watch(
        [() => route.params.path, () => docStore.resourceUrlTemplate],
        async ([, resourceUrlTemplate]) => {
            if (!resourceUrlTemplate) return

            markdownContent.value = undefined
            docStore.pageMetadata = undefined
            loadError.value = undefined

            let response
            try {
                response = await docStore.fetchResource(path.value ? `/docs/${path.value}` : "/docs")
            } catch (error) {
                loadError.value = (error as AxiosError).response?.status === 404 ? "notFound" : "failed"
                return
            }

            docStore.pageMetadata = response.metadata
            let content = response.content
            if (!("canShare" in navigator)) {
                content = content.replaceAll(/\s*web-share\s*/g, "")
            }
            content = removeMDXImports(content)
            const {content: cleanedContent} = extractMultilineJSXComponents(content)
            markdownContent.value = replaceSelfClosingTagsWithOpenClose(cleanedContent)
        },
        {immediate: true},
    )
</script>

<style scoped lang="scss">
    .docs-not-found {
        display: flex;
        flex-direction: column;
        align-items: center;
        gap: var(--ks-spacing-4);
    }
</style>
