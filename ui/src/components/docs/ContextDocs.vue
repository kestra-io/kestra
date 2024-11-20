
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

    const parse = useMarkdownParser();
    const store = useStore();
    const {t} = useI18n();

    const pageMetadata = computed(() => store.getters["doc/pageMetadata"]);
    const docPath = computed(() => store.getters["doc/docPath"]);
    const routeInfo = computed(() => ({
        title: pageMetadata.value?.title ?? t("docs"),
    }))

    const emit = defineEmits(["update:docPath"]);

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
            emit("update:docPath", val);
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

<template>
    <div class="docWrapper">
        <h2 class="docTitle">
            <router-link
                :to="{
                    name: 'docs/view',
                    params:{
                        path:docPath
                    }
                }"
                target="_blank"
            >
                <OpenInNew class="openInNew" />
            </router-link>
            {{ routeInfo.title }}
        </h2>
        <el-divider style="margin:0 var(--spacer);" />
        <docs-menu />
        <docs-layout>
            <template #content>
                <MDCRenderer v-if="ast?.body" :body="ast.body" :data="ast.data" :key="ast" :components="proseComponents" />
            </template>
        </docs-layout>
    </div>
</template>

<style lang="scss" scoped>
h2.docTitle {
    font-size: 18px;
    margin: var(--spacer);
    margin-right: calc(var(--spacer) * 3);

    .openInNew {
        float: right;
        margin: 2px;
        color: var(--bs-tertiary-color);
    }
}

.docWrapper {
    overflow-y: auto;
    height: 100vh;
}
</style>