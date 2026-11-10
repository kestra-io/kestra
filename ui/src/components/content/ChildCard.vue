<template>
    <div>
        <h2 v-if="title" class="card-grid-title">
            {{ title }}
        </h2>
        <div class="card-grid">
            <router-link
                :to="{path: '/' + item.path}"
                class="card-grid-item"
                v-for="item in navigation"
                :key="item.path"
            >
                <div class="card">
                    <div class="card-body">
                        <div class="card-content">
                            <h4 class="card-title">
                                {{ item.title }}
                            </h4>
                            <p class="card-text">
                                {{ item.description?.replaceAll(/\[([^\]]*)\]\([^)]*\)/g, "$1") }}
                            </p>
                        </div>
                        <ChevronRight class="card-chevron" />
                    </div>
                </div>
            </router-link>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed, inject, ref} from "vue"
    import {useDocStore} from "../../stores/doc"
    import {DOC_PATH_INJECTION_KEY} from "../docs/docsUtils"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    interface ResourceMetadata {
        title: string;
        description?: string;
        icon?: string;
        [key: string]: unknown;
    }

    const props = defineProps<{
        pageUrl?: string;
        directory?: string;
        title?: string;
        reverse?: boolean | string;
    }>()

    const docStore = useDocStore()
    const docPath = inject(DOC_PATH_INJECTION_KEY, undefined)

    const currentPage = computed(() => {
        const url = props.directory ?? props.pageUrl ?? docPath?.value ?? "docs"
        return url.replace(/^\/?(.*?)\/?$/, "$1")
    })

    // KsMarkdown passes MDX expressions through as text, so `reverse={true}` arrives as "{true}".
    const isReversed = computed(() => props.reverse === true || props.reverse === "true" || props.reverse === "{true}")

    const resourcesWithMetadata = ref<Record<string, ResourceMetadata>>({})
    const parentMetadata = ref<Partial<ResourceMetadata>>({})

    const parentLevel = computed(() => currentPage.value.split("/").length)

    const navigation = computed(() => {
        const items = Object.entries(resourcesWithMetadata.value)
            .filter(([path]) => path.split("/").length === parentLevel.value + 1)
            .filter(([path]) => path !== currentPage.value)
            .map(([path, metadata]) => ({
                path,
                ...parentMetadata.value,
                ...metadata,
            }))

        return isReversed.value ? items.toReversed() : items
    });

    (async () => {
        resourcesWithMetadata.value = await docStore.children(currentPage.value)

        if (props.pageUrl) {
            parentMetadata.value = {...resourcesWithMetadata.value[currentPage.value]}
            delete parentMetadata.value.description
        }
    })()
</script>

<style scoped>
    .card-grid-title {
        margin: var(--ks-spacing-5) 0 var(--ks-spacing-4);
        font-size: var(--ks-font-size-3xl);
        font-weight: var(--ks-font-weight-semibold);
        line-height: var(--ks-line-height-tight);
        color: var(--ks-text-primary);
    }

    .card {
        height: 100%;
        transition: border-color 0.2s ease;

        &:hover {
            border-color: var(--ks-border-strong);
        }
    }

    .card-body {
        display: flex;
        align-items: center;
    }

    .card-content {
        overflow: hidden;
    }

    .card-title {
        font-size: var(--ks-font-size-md) !important;
        font-weight: 700;
        line-height: 1.375rem !important;
    }

    .card-text {
        margin-bottom: 0;
        font-size: var(--ks-font-size-xs) !important;
        font-weight: 400;
        color: var(--ks-text-secondary);
        line-height: 1rem !important;
    }

    .card-chevron {
        display: inline-flex;
        margin-left: auto;
        flex-shrink: 0;
    }

    .card-grid {
        container-type: inline-size;
        display: flex;
        flex-wrap: wrap;
        gap: 16px;
    }

    .card-grid-item {
        display: block;
        flex: 1 1 100%;
    }

    /* two cards per row once the panel is wide enough */
    @container (min-width: 550px) {
        .card-grid-item {
            flex: 0 1 calc(50% - 8px);
        }
    }
</style>
