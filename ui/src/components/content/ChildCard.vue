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
                            :src="$store.getters['doc/resourceUrl'](item.icon)"
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

<script setup>
    import {useRoute} from "vue-router";
    import {useStore} from "vuex";

    const route = useRoute();
    const store = useStore();

    const props = defineProps({
        pageUrl: {
            type: String,
            default: undefined
        }
    });

    let currentPage = null;

    if (props.pageUrl) {
        currentPage = props.pageUrl;
    } else {
        currentPage = route.path;
    }

    currentPage = currentPage.replace(/^\/?(.*?)\/?$/, "$1");

    const resourcesWithMetadata = await store.dispatch("doc/children", currentPage);
    let parentMetadata;
    if (props.pageUrl) {
        parentMetadata = {...resourcesWithMetadata[currentPage]};
        delete parentMetadata.description;
    }

    const parentLevel = currentPage.split("/").length;
    const navigation = Object.entries(resourcesWithMetadata)
        .filter(([path]) => path.split("/").length === parentLevel + 1)
        .filter(([path]) => path !== currentPage)
        .map(([path, metadata]) => ({
            path,
            ...parentMetadata,
            ...metadata
        }));
</script>

<style lang="scss" scoped>
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
