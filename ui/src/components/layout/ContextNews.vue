<template>
    <ContextInfoContent ref="contextInfoRef">
        <header class="newsHeader">
            <h4>{{ $t("feeds.heading") }}</h4>
            <p>{{ $t("feeds.subheading") }}</p>
        </header>
        <a
            class="post"
            :class="{lastPost: index === 0}"
            v-for="(feed, index) in feeds"
            :key="feed.id"
            :href="feed.href ?? feed.link"
            target="_blank"
            rel="noopener noreferrer"
        >
            <img v-if="feed.image" :src="feed.image" alt="">
            <div class="metaBlock">
                <KsDateAgo className="news-date" :inverted="true" :date="feed.publicationDate" format="LL" :showTooltip="false" />
                <h5>
                    {{ feed.title }}
                </h5>
            </div>
            <OpenInNew class="openInNewIcon" aria-hidden="true" />
        </a>
    </ContextInfoContent>
</template>

<script setup lang="ts">
    import {computed, onMounted, ref} from "vue"
    import {useStorage} from "@vueuse/core"
    import {useScrollMemory} from "../../composables/useScrollMemory"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    import ContextInfoContent from "../ContextInfoContent.vue"

    import {useApiStore} from "../../stores/api"

    const apiStore = useApiStore()

    const contextInfoRef = ref<InstanceType<typeof ContextInfoContent> | null>(null)
    const feeds = computed(() => apiStore.feeds)

    const lastNewsReadDate = useStorage<string | null>("feeds", null)
    onMounted(() => {
        lastNewsReadDate.value = feeds.value[0].publicationDate
    })

    const scrollableElement = computed(() => contextInfoRef.value?.contentRef || null)
    useScrollMemory(ref("context-panel-news"), scrollableElement as any)
</script>

<style scoped lang="scss">
    .newsHeader {
        padding: 2rem 2rem 0;

        h4 {
            font-size: var(--ks-font-size-md);
            font-weight: 600;
            margin: 0 0 0.25rem;
            color: var(--ks-content-primary);
        }

        p {
            font-size: var(--ks-font-size-sm);
            color: var(--ks-content-secondary);
            margin: 0;
        }
    }

    .post {
        position: relative;
        display: flex;
        gap: 0.75rem;
        align-items: flex-start;
        margin: 0 1rem;
        padding: 0.75rem;
        border: 1px solid transparent;
        border-radius: 8px;
        color: inherit;
        text-decoration: none;
        transition: background-color 0.15s ease, border-color 0.15s ease;

        & + & {
            margin-top: 0.25rem;
        }

        &:hover,
        &:focus-visible {
            background: var(--ks-bg-hover-elevated);
            border-color: var(--ks-border-focus);

            .openInNewIcon {
                opacity: 1;
            }
        }

        h5 {
            margin: 0;
            font-size: var(--ks-font-size-md);
            font-weight: 600;
            color: var(--ks-content-primary);
        }

        img {
            display: block;
            width: 100px;
            aspect-ratio: 16 / 9;
            border: 1px solid var(--ks-border-default);
            border-radius: 4px;
            object-fit: cover;
            flex-shrink: 0;
        }

        .metaBlock {
            display: flex;
            flex-direction: column;
            gap: 0.25rem;
            min-width: 0;
        }
    }

    .lastPost {
        flex-direction: column;
        gap: 0.5rem;

        img {
            width: 100%;
            aspect-ratio: auto;
            border: none;
            border-radius: var(--kel-border-radius-round);
        }
    }

    .openInNewIcon {
        position: absolute;
        top: 1rem;
        right: 1rem;
        color: var(--ks-content-primary);
        opacity: 0;
        transition: opacity 0.15s ease;
    }

    :deep(.news-date) {
        font-size: 9px;
        font-weight: 400;
        color: var(--ks-text-secondary);
    }
</style>
