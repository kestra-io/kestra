<template>
    <div class="card-grid">
        <router-link
            :to="{path: '/' + item.path}"
            class="card-grid-item"
            v-for="item in navigation"
            :key="item.path"
        >
            <div class="card h-100">
                <div class="card-body d-flex align-items-center">
                    <div class="overflow-hidden">
                        <h4 class="card-title">
                            {{ item.title }}
                        </h4>
                        <p class="card-text mb-0">
                            {{ item.description?.replaceAll(/\[([^\]]*)\]\([^)]*\)/g, "$1") }}
                        </p>
                    </div>
                    <ChevronRight class="card-chevron" />
                </div>
            </div>
        </router-link>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useRoute} from "vue-router"
    import {useDocStore} from "../../stores/doc"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    interface ResourceMetadata {
        title: string;
        description?: string;
        icon?: string;
        [key: string]: unknown;
    }

    const props = defineProps<{
        pageUrl?: string;
    }>()

    const route = useRoute()
    const docStore = useDocStore()

    const currentPage = computed(() => {
        const routePath = Array.isArray(route.params.path) ? route.params.path.join("/") : route.params.path
        const url = props.pageUrl ?? routePath ?? ""
        return url.replace(/^\/?(.*?)\/?$/, "$1")
    })

    const resourcesWithMetadata = ref<Record<string, ResourceMetadata>>({})
    const parentMetadata = ref<Partial<ResourceMetadata>>({})

    const parentLevel = computed(() => currentPage.value.split("/").length)

    const navigation = computed(() =>
        Object.entries(resourcesWithMetadata.value)
            .filter(([path]) => path.split("/").length === parentLevel.value + 1)
            .filter(([path]) => path !== currentPage.value)
            .map(([path, metadata]) => ({
                path,
                ...parentMetadata.value,
                ...metadata,
            })),
    );

    (async () => {
        resourcesWithMetadata.value = await docStore.children(currentPage.value)

        if (props.pageUrl) {
            parentMetadata.value = {...resourcesWithMetadata.value[currentPage.value]}
            delete parentMetadata.value.description
        }
    })()
</script>

<style scoped lang="scss">
    .card {
        transition: border-color 0.2s ease;

        &:hover {
            border-color: var(--ks-border-strong);
        }
    }

    .card-title {
        font-size: var(--ks-font-size-md) !important;
        font-weight: 700;
        line-height: 1.375rem !important;
    }

    .card-text {
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
