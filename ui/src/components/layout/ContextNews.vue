<template>
    <context-info-content :title="t('feeds.title')">
        <div class="post" :class="{lastPost: index === 0, expanded: expanded[feed.id]}" v-for="(feed, index) in feeds" :key="feed.id">
            <div v-if="feed.image" class="mr-2">
                <img :src="feed.image" alt="">
            </div>
            <div class="metaBlock">
                <h5>
                    {{ feed.title }}
                </h5>
                <date-ago class-name="news-date small" :inverted="true" :date="feed.publicationDate" format="LL" />
            </div>

            <markdown class="markdown-tooltip mt-3 postParagraph" :source="feed.description" />

            <div class="newsButtonBar">
                <el-button
                    style="flex:1"
                    @click="expanded[feed.id] = !expanded[feed.id]"
                >
                    <MenuDown class="expandIcon" />
                    {{ expanded[feed.id] ? t("showLess") : t("showMore") }}
                </el-button>
                <el-button
                    v-if="feed.href"
                    :title="t('open in new tab')"
                    tag="a"
                    type="primary"
                    target="_blank"
                    :href="feed.href"
                >
                    <OpenInNew :title="feed.link" />
                </el-button>
            </div>

            <el-divider v-if="index !== feeds.length - 1" />
        </div>
    </context-info-content>
</template>

<script lang="ts" setup>
    import {computed, onMounted, reactive} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import {useStorage} from "@vueuse/core"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import MenuDown from "vue-material-design-icons/MenuDown.vue";

    import Markdown from "./Markdown.vue";
    import DateAgo from "./DateAgo.vue";
    import ContextInfoContent from "../ContextInfoContent.vue";

    const store = useStore();
    const {t} = useI18n({useScope: "global"});

    const feeds = computed(() => store.state.api.feeds);

    const expanded = reactive({});

    const lastNewsReadDate = useStorage<string | null>("feeds", null)
    onMounted(() => {
        lastNewsReadDate.value = feeds.value[0].publicationDate;
    });
</script>

<style lang="scss" scoped>
    .post {
        padding: 1rem;

        h5 {
            margin-bottom: 0;
            font-size: var(--font-size-lg);
        }

        img {
            max-height: 6rem;
            max-width: 10rem;
            margin-right: 1rem;
            float: left;
            border-radius: var(--border-radius-lg);
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
            border-top-color: var(--bs-gray-700);
            margin-top: .5rem;
            margin-bottom: .5rem;
        }

        .small {
            font-size:  var(--font-size-sm);
            opacity: 0.7;
        }

        a.el-button {
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
            -webkit-line-clamp: 6;
            line-clamp: 6;
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
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        overflow: hidden;
        line-height: 1.6;
        .expanded & {
            -webkit-line-clamp: unset;
        }
    }

    .newsButtonBar {
        display: flex;
        margin-top: 1rem;
    }

    :deep(.news-date) {
        color: var(--bs-gray-700);
    }
</style>
