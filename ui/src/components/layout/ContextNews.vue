<template>
    <ContextInfoContent ref="contextInfoRef" :title="$t('feeds.title')">
        <div
            class="post"
            :class="{
                lastPost: index === 0,
                expanded: expanded[feed.id]
            }"
            v-for="(feed, index) in feeds"
            :key="feed.id"
        >
            <div v-if="feed.image" class="mr-2">
                <img :src="feed.image" alt="">
            </div>
            <div class="metaBlock">
                <h5>
                    {{ feed.title }}
                </h5>
                <KsDateAgo className="news-date small" :inverted="true" :date="feed.publicationDate" format="LL" :showTooltip="false" />
            </div>
            <KsMarkdown class="markdown-tooltip postParagraph" :content="feed.description" />

            <div class="newsButtonBar">
                <KsButton
                    style="flex:1"
                    @click="expanded[feed.id] = !expanded[feed.id]"
                >
                    <MenuDown class="expandIcon" />
                    {{ expanded[feed.id] ? $t("showLess") : $t("showMore") }}
                </KsButton>
                <KsButton
                    v-if="feed.href"
                    :title="$t('open in new tab')"
                    tag="a"
                    type="primary"
                    target="_blank"
                    :href="feed.href"
                >
                    <OpenInNew :title="feed.link" />
                </KsButton>
            </div>

            <KsDivider class="mb-2" v-if="index !== feeds.length - 1" />
        </div>
    </ContextInfoContent>
</template>

<script setup lang="ts">
    import {computed, onMounted, reactive, ref} from "vue"
    import {useStorage} from "@vueuse/core"
    import {useScrollMemory} from "../../composables/useScrollMemory"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"
    import MenuDown from "vue-material-design-icons/MenuDown.vue"

    import {KsMarkdown} from "@kestra-io/design-system"
    import ContextInfoContent from "../ContextInfoContent.vue"

    import {useApiStore} from "../../stores/api"

    const apiStore = useApiStore()

    const contextInfoRef = ref<InstanceType<typeof ContextInfoContent> | null>(null)
    const feeds = computed(() => apiStore.feeds)

    const expanded = reactive<Record<string, boolean>>({})

    const lastNewsReadDate = useStorage<string | null>("feeds", null)
    onMounted(() => {
        lastNewsReadDate.value = feeds.value[0].publicationDate
    })

    const scrollableElement = computed(() => contextInfoRef.value?.contentRef || null)
    useScrollMemory(ref("context-panel-news"), scrollableElement as any)
</script>

<style scoped lang="scss">
    $post-line-height: 1.6;

    .post {
        padding: 1rem 1rem 0rem 1rem;

        h5 {
            margin-bottom: 0;
            font-size: var(--ks-font-size-lg);
        }

        img {
            max-height: 6rem;
            max-width: 10rem;
            margin-right: 1rem;
            float: left;
            border-radius: var(--kel-border-radius-round);
        }

        .metaBlock {
            display: flex;
            flex-direction: column;
            vertical-align: middle;
            justify-content: center;
            gap: 0.25rem;
            min-height: 6rem;
        }

        hr {
            border-top-color: var(--ks-border-primary);
            margin-top: .5rem;
            margin-bottom: .5rem;
        }

        .small {
            font-size:  var(--ks-font-size-sm);
            opacity: 0.7;
        }

        a.kel-button {
            font-weight: bold;
        }

        .expandIcon {
            margin-right: 1rem;
        }
    }

    .expanded .expandIcon{
        transform: rotate(180deg);
    }

    .lastPost{
        .postParagraph {
            max-height: calc(6 * #{$post-line-height}em);
        }

        img {
            display: block;
            width: 100%;
            float: none;
            max-width: none;
            max-height: none;
            margin-bottom: 1rem;
        }
    }

    .postParagraph {
        max-height: calc(4 * #{$post-line-height}em);
        overflow: hidden;
        line-height: $post-line-height;
        .expanded & {
            max-height: none;
            overflow: visible;
        }
    }

    .newsButtonBar {
        display: flex;
        margin-top: 1rem;
    }

    :deep(.news-date) {
        color: var(--ks-content-secondary);
    }
</style>
