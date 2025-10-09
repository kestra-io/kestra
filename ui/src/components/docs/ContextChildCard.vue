<template>
    <div class="row row-cols-1 row-cols-xxl-2 g-3 card-group">
        <ContextDocsLink
            :href="item.path"
            class="col"
            v-for="item in navigation"
            :key="item.path"
            useRaw
        >
            <div class="card h-100">
                <div class="card-body d-flex align-items-center">
                    <span class="card-icon">
                        <img
                            :src="docStore.resourceUrl(item.icon)"
                            :alt="item.title"
                            width="50px"
                            height="50px"
                        >
                    </span>
                    <div class="overflow-hidden">
                        <h4 class="card-title">
                            {{ item.title }}
                        </h4>
                        <p class="card-text mb-0">
                            {{ item.description?.replaceAll(/\[([^\]]*)\]\([^)]*\)/g, "$1") }}
                        </p>
                    </div>
                </div>
            </div>
        </ContextDocsLink>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, onMounted} from "vue";
    import {useDocStore} from "../../stores/doc";

    import ContextDocsLink from "./ContextDocsLink.vue";

    const docStore = useDocStore();

    const props = defineProps({
        pageUrl: {
            type: String,
            default: undefined
        }
    });

    const currentPage = computed(() => {
        if (props.pageUrl) {
            return props.pageUrl.replace(/^\//, "").replace(/\/$/, "");
        } else {
            const p = docStore.docPath;
            return p ? `docs/${p.replace(/^\/?(.*?)\/?$/, "$1").replace(/^\.\//, "/")}` : "";
        }
    })


    const resourcesWithMetadata = ref<Record<string, any>>({});
    onMounted(async () => {
        resourcesWithMetadata.value = await docStore.children(currentPage.value);
    })

    const navigation = computed(() => {
        let parentMetadata: Record<string, any> = {};
        if (props.pageUrl) {
            parentMetadata = {...resourcesWithMetadata.value[currentPage.value]};
            delete parentMetadata.description;
        }

        const parentLevel = currentPage.value.split("/").length;
        return Object.entries(resourcesWithMetadata.value)
            .filter(([path]) => path.split("/").length === parentLevel + 1)
            .filter(([path]) => path !== currentPage.value)
            .map(([path, metadata]) => ({
                path: path.replace(/^docs\//, ""),
                ...parentMetadata,
                ...metadata
            }))
    });
</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .card-title {
        font-size: $font-size-xl !important;
        line-height: 1.375rem !important;
    }

    .card-text {
        font-size: $font-size-sm !important;
        line-height: 1rem !important;
    }

    .card-icon {
        img {
            max-width: unset;
            width: 48px !important;
            height: 48px !important;
        }
    }

    .row-cols-xxl-2{
        container-type: inline-size;
    }


    // only remove the media query
    // when container queries are supported
    @container (min-width:0px) {
        .row-cols-1 > *  {
            width: 100%;
        }
    }

    /* If the container is larger than 550px */
    @container (min-width: 550px) {
        .row-cols-xxl-2 > * {
            width: 50%;
        }
    }
</style>
