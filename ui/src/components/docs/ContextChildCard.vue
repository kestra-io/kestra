<template>
    <div class="card-grid">
        <ContextDocsLink
            v-for="item in navigation"
            :key="item.path"
            :href="item.path"
            class="card-grid-item"
            useRaw
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
        </ContextDocsLink>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref, onMounted} from "vue"
    import {useDocStore} from "../../stores/doc"

    import ContextDocsLink from "./ContextDocsLink.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"

    const docStore = useDocStore()

    const props = defineProps({
        pageUrl: {
            type: String,
            default: undefined,
        },
    })

    const currentPage = computed(() => {
        if (props.pageUrl) {
            return props.pageUrl.replace(/^\//, "").replace(/\/$/, "")
        } else {
            const p = docStore.docPath
            return p ? p.replace(/^\/?(.*?)\/?$/, "$1").replace(/^\.\//, "/") : "docs"
        }
    })

    const resourcesWithMetadata = ref<Record<string, any>>({})
    onMounted(async () => {
        resourcesWithMetadata.value = await docStore.children(currentPage.value)
    })

    const navigation = computed(() => {
        let parentMetadata: Record<string, any> = {}
        if (props.pageUrl) {
            parentMetadata = {...resourcesWithMetadata.value[currentPage.value]}
            delete parentMetadata.description
        }

        const parentLevel = currentPage.value.split("/").length
        return Object.entries(resourcesWithMetadata.value)
            .filter(([path]) => path.split("/").length === parentLevel + 1)
            .filter(([path]) => path !== currentPage.value)
            .map(([path, metadata]) => ({
                path,
                ...parentMetadata,
                ...metadata,
            }))
    })
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
