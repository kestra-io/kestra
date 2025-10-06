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

<script setup lang="ts">
    import {useRoute} from "vue-router";
    import {useDocStore} from "../../stores/doc";

    interface ReleaseMetadata {
        release: string;
        title: string;
        description?: string;
    }

    interface ResourcesWithMetadata {
        [key: string]: ReleaseMetadata;
    }

    const props = withDefaults(defineProps<{
        pageUrl?: string;
    }>(), {
        pageUrl: undefined
    });

    const docStore = useDocStore();
    const route = useRoute();

    let currentPage: string;

    if (props.pageUrl) {
        currentPage = props.pageUrl;
    } else {
        currentPage = route.path;
    }

    currentPage = currentPage.endsWith("/") ? currentPage.slice(0, -1) : currentPage;

    const resourcesWithMetadata = await docStore.children(currentPage) as ResourcesWithMetadata;

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
