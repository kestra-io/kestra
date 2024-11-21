<script lang="ts" setup>
    import {computed, onMounted, reactive} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import {useStorage} from "@vueuse/core"

    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import MenuDown from "vue-material-design-icons/MenuDown.vue";

    import Markdown from "./Markdown.vue";
    import DateAgo from "./DateAgo.vue";

    const store = useStore();
    const {t} = useI18n();

    const feeds = computed(() => store.state.api.feeds);

    const expanded = reactive({});

    const lastNewsReadDate = useStorage<string | null>("feeds", null)
    onMounted(() => {
        lastNewsReadDate.value = feeds.value[0].publicationDate;
    });
</script>

<template>
    <div class="allContextNews">
        <h2 class="newsTitle">
            {{ t("feeds.title") }}
        </h2>
        <el-divider style="margin: var(--spacer) 0;" />
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
    </div>
</template>

<style lang="scss" scoped>
    .allContextNews{
        padding: var(--spacer);
    }

    .newsTitle{
        font-size: 18px;
    }

    .post {
        h5 {
            font-weight: medium;
            margin-bottom: 0;
            font-size: 17px;
            line-height: 28px;
        }

        img {
            max-height: 90px;
            max-width: 180px;
            margin-right: 20px;
            float: left;
            border-radius: 10px;
        }

        .metaBlock {
            display: flex;
            flex-direction: column;
            vertical-align: middle;
            justify-content: center;
            gap: 4px;
            min-height: 90px;
        }

        hr {
            border-top-color: var(--bs-gray-700);
            margin-top: calc(var(--spacer) * 2);
            margin-bottom: calc(var(--spacer) * 2);
        }

        .small {
            font-size:  var(--font-size-sm);
            opacity: 0.7;
        }

        a.el-button {
            font-weight: bold;
        }

        .expandIcon {
            margin-right:var(--spacer);
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
            margin-bottom: var(--spacer)
        }
    }

    .postParagraph {
        display: -webkit-box;
        -webkit-box-orient: vertical;
        -webkit-line-clamp: 2;
        line-clamp: 2;
        overflow: hidden;
        padding-bottom: 2px;
        line-height: 24px;
        .expanded & {
            -webkit-line-clamp: unset;
        }
    }

    .newsButtonBar {
        display: flex;
        margin-top: var(--spacer);
    }
</style>

<style style="scss">
.news-date {
    color: var(--bs-gray-700);
}
</style>
