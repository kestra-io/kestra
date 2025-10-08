<template>
    <div class="row row-cols-1 row-cols-xxl-2 g-3 card-group">
        <router-link
            :to="{path: '/' + item.path}"
            class="col"
            v-for="item in navigation"
            :key="item.path"
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
        </router-link>
    </div>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue";
    import {useRoute} from "vue-router";
    import {useDocStore} from "../../stores/doc";

    interface ResourceMetadata {
        title: string;
        description?: string;
        icon?: string;
        [key: string]: unknown;
    }

    const props = defineProps<{
        pageUrl?: string;
    }>();

    const route = useRoute();
    const docStore = useDocStore();

    const currentPage = computed(() => {
        const url = props.pageUrl ?? route.path;
        return url.replace(/^\/?(.*?)\/?$/, "$1");
    });

    const resourcesWithMetadata = ref<Record<string, ResourceMetadata>>({});
    const parentMetadata = ref<Partial<ResourceMetadata>>({});

    const parentLevel = computed(() => currentPage.value.split("/").length);

    const navigation = computed(() =>
        Object.entries(resourcesWithMetadata.value)
            .filter(([path]) => path.split("/").length === parentLevel.value + 1)
            .filter(([path]) => path !== currentPage.value)
            .map(([path, metadata]) => ({
                path,
                ...parentMetadata.value,
                ...metadata
            }))
    );

    (async () => {
        resourcesWithMetadata.value = await docStore.children(currentPage.value);

        if (props.pageUrl) {
            parentMetadata.value = {...resourcesWithMetadata.value[currentPage.value]};
            delete parentMetadata.value.description;
        }
    })();
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
</style>
