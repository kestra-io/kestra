<script lang="ts" setup>
    import {ref, computed, onMounted} from "vue";
    import {useStore} from "vuex";
    import {useI18n} from "vue-i18n";
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";

    import Markdown from "./Markdown.vue";
    import DateAgo from "./DateAgo.vue";

    const store = useStore();
    const {t} = useI18n();

    const feeds = computed(() => store.state.api.feeds);

    const hasUnread = ref(false);

    const isUnread = () => {
        let storage = new Date(localStorage.getItem("feeds") ?? "");

        return (
            storage === null ||
            (feeds.value && feeds.value[0] && storage <= new Date(feeds.value[0].publicationDate))
        );
    };

    onMounted(() => {
        hasUnread.value = isUnread();
    });
</script>

<template>
    <div class="allContextNews">
        <h3>{{ t("newsTitle") }}</h3>
        <div class="post" v-for="(feed, index) in feeds" :key="feed.id">
            <div v-if="feed.image" class="mt-2">
                <img class="float-end" :src="feed.image" alt="">
            </div>
            <h5>
                {{ feed.title }}
            </h5>
            <date-ago class-name="news-date small" :inverted="true" :date="feed.publicationDate" format="LL" />

            <markdown class="markdown-tooltip mt-3" :source="feed.description" />

            <div class="d-flex w-100 justify-content-end">
                <a class="el-button el-button--primary mt-3 " :href="feed.href" target="_blank">{{ feed.link }} <OpenInNew /></a>
            </div>

            <el-divider v-if="index !== feeds.length - 1" />
        </div>
    </div>
</template>

<style lang="scss" scoped>
    .allContextNews{
        padding: var(--spacer);
    }

    .post {
        h5 {
            font-weight: bold;
            margin-bottom: 0;
        }

        img {
            max-height: 120px;
            max-width: 240px;
            padding-left: 20px;
            padding-bottom: 20px;
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
    }
</style>

<style style="scss">
.news-date {
    color: var(--bs-gray-700);
}
</style>
