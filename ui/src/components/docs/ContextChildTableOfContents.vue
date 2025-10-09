<template>
    <ChildTableOfContents :pageUrl="pageUrl" :max="max" :renderLink="ContextDocsTOCLink" />
</template>

<script setup lang="ts">
    import {h} from "vue";
    import ChildTableOfContents from "../content/ChildTableOfContents.vue";
    import ContextDocsLink from "./ContextDocsLink.vue";

    const ContextDocsTOCLink = (link: {path: string, title: string}) => {
        return h(ContextDocsLink,
                 {
                     // remove "docs/" from the path
                     href: link.path.slice(5),
                     useRaw: true
                 },
                 () => link.title);
    }

    defineProps<{
        pageUrl?: string,
        max?: number,
    }>();
</script>
