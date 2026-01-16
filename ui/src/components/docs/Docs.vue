<template>
    <TopNavBar :title="routeInfo.title" />
    <DocsLayout>
        <template #menu>
            <Toc />
        </template>
        <template #content>
            <template v-if="ast?.body">
                <h1>{{ routeInfo.title }}</h1>
                <MDCRenderer :body="ast.body" :data="ast.data" :key="ast" :components="proseComponents" />
            </template>
        </template>
    </DocsLayout>
</template>

<script setup lang="ts">
    import {MDCRenderer, getMDCParser} from "@kestra-io/ui-libs";
    import TopNavBar from "../layout/TopNavBar.vue";
    import {useDocStore} from "../../stores/doc";
    import DocsLayout from "./DocsLayout.vue";
    import Toc from "./Toc.vue";
    import {computed,ref,watch,getCurrentInstance} from "vue";
    import {useRoute} from "vue-router";
    import {useI18n} from "vue-i18n";

    const route = useRoute();
    const {t} = useI18n();
    const docStore = useDocStore();

    const ast = ref();
    const proseComponents = Object.fromEntries(
        Object.keys(getCurrentInstance()?.appContext.components ?? {})
            .filter(name => name.startsWith("Prose"))
            .map(name => name.substring(5).replaceAll(/(.)([A-Z])/g, "$1-$2").toLowerCase())
            .map(name => [name, "prose-" + name])
    );

    const path = computed(() => {
        const routePath = Array.isArray(route.params.path) ? route.params.path.join("/") : route.params.path;
        return routePath?.length > 0 ? routePath.replaceAll(/(^|\/)\.\//g, "$1") : undefined;
    });

    const routeInfo = computed(() => ({
        title: docStore.pageMetadata?.title ?? t("docs"),
    }));

    watch(
        () => route.params.path,
        async () => {
            const response = await docStore.fetchResource(`docs${path.value ? `/${path.value}` : ""}`);
            docStore.pageMetadata = response.metadata;
            let content = response.content;
            if (!("canShare" in navigator)) {
                content = content.replaceAll(/\s*web-share\s*/g, "");
            }
            const parse = await getMDCParser();
            ast.value = await parse(content);
        },
        {immediate: true}
    );
</script>
