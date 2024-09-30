<template>
    <div class="row card-group card-centered mb-2">
        <a :href="parsedUrl" class="col-12 col-md-10 mb-4" v-for="[parsedUrl, metadata] in navigation" :key="parsedUrl">
            <div class="card">
                <div class="card-body">
                    <div>
                        <h4 class="text-white">{{ metadata.release }}</h4>
                        <h4 class="card-title">{{ metadata.title }}</h4>
                    </div>
                    <p class="card-text">{{ metadata.description }}</p>
                </div>
            </div>
        </a>
    </div>
</template>

<script setup>
    import {useRoute} from "vue-router";
    import {useStore} from "vuex";

    const props = defineProps({
        pageUrl: {
            type: String,
            default: undefined
        },
    });

    const store = useStore();
    const route = useRoute();

    let currentPage;

    if (props.pageUrl) {
        currentPage = props.pageUrl;
    } else {
        currentPage = route.path;
    }

    currentPage = currentPage.endsWith("/") ? currentPage.slice(0, -1) : currentPage;

    const resourcesWithMetadata = await store.dispatch("doc/children", currentPage);

    const navigation = Object.entries(resourcesWithMetadata)
        .filter(([_, metadata]) => metadata.release !== undefined)
        .sort(([_, {release: release1}], [__, {release: release2}]) => {
            if (release1 < release2) {
                return -1;
            }
            if (release1 > release2) {
                return 1;
            }
            return 0;
        });
</script>
