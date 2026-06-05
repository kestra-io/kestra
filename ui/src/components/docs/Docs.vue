<template>
    <TopNavBar :title="routeInfo.title" />
    <DocsLayout>
        <template #menu>
            <Toc />
        </template>
        <template #content>
            <template>
                <KsMarkdown class="markdown" :content="markdownContent" :xssProtection="false" :components="markdownComponents" />
            </template>
        </template>
    </DocsLayout>
</template>

<script setup lang="ts">
    import {computed,ref,watch} from "vue"
    import TopNavBar from "../layout/TopNavBar.vue"
    import {useDocStore} from "../../stores/doc"
    import DocsLayout from "./DocsLayout.vue"
    import Toc from "./Toc.vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import {KsMarkdown} from "@kestra-io/design-system"
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

    const path = computed(() => {
        const routePath = Array.isArray(route.params.path) ? route.params.path.join("/") : route.params.path
        return routePath?.length > 0 ? routePath.replaceAll(/(^|\/)\.\//g, "$1") : undefined
    })

    const routeInfo = computed(() => ({
        title: docStore.pageMetadata?.title ?? t("docs"),
    }))

    watch(
        () => route.params.path,
        async () => {
            const response = await docStore.fetchResource(path.value ? `/${path.value}` : "")
            docStore.pageMetadata = response.metadata
            let content = response.content
            if (!("canShare" in navigator)) {
                content = content.replaceAll(/\s*web-share\s*/g, "")
            }
            markdownContent.value = content
        },
        {immediate: true},
    )
</script>
