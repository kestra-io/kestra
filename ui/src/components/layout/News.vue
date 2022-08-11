<template>
    <b-nav-item>
        <a class="news-link" v-b-modal="`news-modal`">
            <gift title="" />
            <CheckboxBlankCircle v-if="hasUnread" class="new" title="" />
        </a>

        <b-modal
            id="news-modal"
            :title="$t('feeds.title')"
            hide-backdrop
            hide-footer
            modal-class="right"
            size="xl"
            @show="show"
        >
            <div class="post" v-for="(feed, index) in feeds" :key="feed.id">
                <div v-if="feed.image" class="mt-2">
                    <img class="float-right" :src="feed.image" alt="">
                </div>
                <h5>
                    {{ feed.title }}
                </h5>
                <date-ago class="text-muted small" :inverted="true" :date="feed.publicationDate" format="LL" />


                <markdown class="markdown-tooltip mt-3" :source="feed.description" />

                <a class="mt-3 d-block text-right" :href="feed.href" target="_blank">{{ feed.link }} <OpenInNew /></a>

                <hr v-if="index !== feeds.length - 1" class="text-white">
            </div>
        </b-modal>
    </b-nav-item>
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
            };
        },
        mounted() {
            this.hasUnread = this.isUnread();
        },
        watch: {
            feeds() {
                this.hasUnread = this.isUnread();
            }
        },
        methods: {
            show() {
                localStorage.setItem("feeds", this.feeds[0].publicationDate)
                this.hasUnread = this.isUnread();
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
        margin-top: $spacer * 2;
        margin-bottom: $spacer * 2;
    }

    a.d-block {
        font-weight: bold;
    }
}

</style>
