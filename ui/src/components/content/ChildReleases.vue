<template>
    <div class="card-group card-centered">
        <a :href="parsedUrl" class="release-card" v-for="[parsedUrl, metadata] in navigation" :key="parsedUrl">
            <div class="card">
                <div class="card-body">
                    <div>
                        <h4 class="release-version">{{ metadata.release }}</h4>
                        <h4>{{ metadata.title }}</h4>
                    </div>
                    <p>{{ metadata.description }}</p>
                </div>
            </div>
        </a>
    </div>
</template>

<script setup lang="ts">
    import {useRoute} from "vue-router"
    import {useDocStore} from "../../stores/doc"

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
        pageUrl: undefined,
    })

    const docStore = useDocStore()
    const route = useRoute()

    let currentPage: string

    if (props.pageUrl) {
        currentPage = props.pageUrl
    } else {
        currentPage = route.path
    }

    currentPage = currentPage.endsWith("/") ? currentPage.slice(0, -1) : currentPage

    const resourcesWithMetadata = await docStore.children(currentPage) as ResourcesWithMetadata

    const navigation = Object.entries(resourcesWithMetadata)
        .filter(([_, metadata]) => metadata.release !== undefined)
        .sort(([_, {release: release1}], [__, {release: release2}]) => {
            if (release1 < release2) {
                return -1
            }
            if (release1 > release2) {
                return 1
            }
            return 0
        })
</script>

<style scoped lang="scss">
    .card-group {
        margin-bottom: var(--ks-spacing-2);
    }

    .release-card {
        margin-bottom: var(--ks-spacing-5);
    }

    .release-version {
        color: var(--ks-text-primary);
    }
</style>
