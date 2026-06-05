<template>
    <TopNavBar :title="routeInfo.title" />
    <DocsLayout>
        <template #menu>
            <Toc />
        </template>
        <template #content>
            <template v-if="ast?.body">
                <h1>{{ routeInfo.title }}</h1>
                <div class="markdown">
                    <MDCRenderer
                        :body="ast.body"
                        :data="ast.data"
                        :key="ast"
                        :components="componentsByName"
                    />
                </div>
            </template>
        </template>
    </DocsLayout>
</template>

<script setup lang="ts">
    import {MDCRenderer, getMDCParser} from "@kestra-io/ui-libs"
    import TopNavBar from "../layout/TopNavBar.vue"
    import {useDocStore} from "../../stores/doc"
    import DocsLayout from "./DocsLayout.vue"
    import Toc from "./Toc.vue"
    import {computed,DefineComponent,ref,watch} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"

    const components = {
        ...(import.meta.glob<{default: DefineComponent}>("../../../node_modules/@nuxtjs/mdc/dist/runtime/components/prose/*.vue", {eager: true})),
        ...(import.meta.glob<{default: DefineComponent}>("../../../node_modules/@kestra-io/ui-libs/src/components/content/*.vue", {eager: true})),
        ...(import.meta.glob<{default: DefineComponent}>("../content/*.vue", {eager: true})),
    }

    const componentsByName = Object.fromEntries(
        Object.entries(components)
            .map(([path, component]) => [path.replace(/^.*\/(.*)\.vue$/, "$1"), component.default]),
    )

    const route = useRoute()
    const {t} = useI18n()
    const docStore = useDocStore()

    const ast = ref()

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
            const parse = await getMDCParser()
            ast.value = await parse(content)
        },
        {immediate: true},
    )
</script>
