<template>
    <a class="news-link" @click="show">
        <gift title="" />
        <CheckboxBlankCircle v-if="hasUnread" class="new" title="" />
    </a>

    <el-drawer size="50%" v-if="isOpen" v-model="isOpen" destroy-on-close :append-to-body="true" :title="$t('feeds.title')">
        <div class="post" v-for="(feed, index) in feeds" :key="feed.id">
            <div v-if="feed.image" class="mt-2">
                <img class="float-right" :src="feed.image" alt="">
            </div>
            <h5>
                {{ feed.title }}
            </h5>
            <date-ago class="text-muted small" :inverted="true" :date="feed.publicationDate" format="LL" />


            <markdown class="markdown-tooltip mt-3" :source="feed.description" />

            <div class="text-right">
                <a class="el-button el-button--primary mt-3 d-inline-block text-right" :href="feed.href" target="_blank">{{ feed.link }} <OpenInNew /></a>
            </div>

            <el-divider v-if="index !== feeds.length - 1" />
        </div>
    </el-drawer>
</template>

<script>
    import {mapState} from "vuex";
    import Gift from "vue-material-design-icons/Gift";
    import OpenInNew from "vue-material-design-icons/OpenInNew";
    import CheckboxBlankCircle from "vue-material-design-icons/CheckboxBlankCircle";
    import Markdown from "./Markdown";
    import DateAgo from "./DateAgo";

    export default {
        components: {
            Gift,
            OpenInNew,
            CheckboxBlankCircle,
            Markdown,
            DateAgo
        },
        data() {
            return {
                hasUnread: false,
                isOpen: false,
            };
        },
        mounted() {
            this.hasUnread = this.isUnread();
        },
        watch: {
            feeds: {
                handler() {
                    this.hasUnread = this.isUnread();
                },
                deep: true
            },
        },
        methods: {
            show() {
                localStorage.setItem("feeds", this.feeds[0].publicationDate)
                this.hasUnread = this.isUnread();
                this.isOpen = !this.isOpen;
            },
            isUnread() {
                let storage = localStorage.getItem("feeds");
                return (
                    storage === null ||
                    (this.feeds && this.feeds[0] && this.$moment(storage).isBefore(this.feeds[0].publicationDate))
                );
            },
        },
        computed: {
            ...mapState("misc", ["configs"]),
            ...mapState("api", ["feeds"]),
        }
    };
</script>

<style lang="scss" scoped>
@use 'element-plus/theme-chalk/src/mixins/function' as *;
@import "../../styles/variable";

.news-link {
    font-size: $font-size-lg;
    color: var(--gray-600);
}

.new {
    font-size: $font-size-xs;
    color: $red;
    position: absolute;
    margin-left: -8px;

    animation-name: grow;
    animation-duration: 1.5s;
    animation-iteration-count: infinite;
    animation-timing-function: ease-in;
}

@keyframes grow {
    0% {
        transform: scale(0.8);
    }
    50%  {
        transform: scale(1.2);
    }
    100% {
        transform: scale(0.8);
    }
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
        border-top-color: var(--gray-700);
        margin-top: calc(getCssVar('spacer') * 2);
        margin-bottom: calc(getCssVar('spacer') * 2);
    }


    a.el-button {
        font-weight: bold;
    }
}

</style>
