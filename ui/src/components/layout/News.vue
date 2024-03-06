<template>
    <el-button class="news-link" @click="show">
        <bell title="" />
        <CheckboxBlankCircle v-if="hasUnread" class="new" title="" />
    </el-button>

    <drawer v-if="isOpen" v-model="isOpen" :title="$t('feeds.title')">
        <div class="post" v-for="(feed, index) in feeds" :key="feed.id">
            <div v-if="feed.image" class="mt-2">
                <img class="float-end" :src="feed.image" alt="">
            </div>
            <h5>
                {{ feed.title }}
            </h5>
            <date-ago class-name="text-muted small" :inverted="true" :date="feed.publicationDate" format="LL" />

            <markdown class="markdown-tooltip mt-3" :source="feed.description" />

            <div class="text-end">
                <a class="el-button el-button--primary mt-3 " :href="feed.href" target="_blank">{{ feed.link }} <OpenInNew /></a>
            </div>

            <el-divider v-if="index !== feeds.length - 1" />
        </div>
    </drawer>
</template>

<script>
    import {mapState} from "vuex";
    import Bell from "vue-material-design-icons/Bell.vue";
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue";
    import CheckboxBlankCircle from "vue-material-design-icons/CheckboxBlankCircle.vue";
    import Markdown from "./Markdown.vue";
    import DateAgo from "./DateAgo.vue";
    import Drawer from "../Drawer.vue";

    export default {
        components: {
            Bell,
            OpenInNew,
            CheckboxBlankCircle,
            Markdown,
            DateAgo,
            Drawer
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
            show(event) {
                event.preventDefault();

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
    .new {
        font-size: calc(var(--font-size-sm) * 0.7) !important;
        color: var(--el-color-error);
        position: absolute;
        margin-left: 10px;
        margin-top: -12px;

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
