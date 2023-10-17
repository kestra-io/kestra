<template>
    <nav class="d-flex w-100 gap-3 top-bar" v-if="displayNavBar">
        <div class="d-flex flex-column flex-grow-1">
            <el-breadcrumb v-if="breadcrumb">
                <el-breadcrumb-item v-for="(item, x) in breadcrumb" :key="x">
                    <router-link :to="item.link">
                        {{ item.label }}
                    </router-link>
                </el-breadcrumb-item>
            </el-breadcrumb>
            <h1 class="h5 fw-semibold m-0 d-inline-flex">
                <slot name="title">
                    {{ title }}
                </slot>
            </h1>
        </div>
        <div class="d-flex side gap-2">
            <global-search />
            <slot name="additional-right" />
            <div class="d-flex fixed-buttons">
                <el-dropdown popper-class="hide-arrow overflow-hidden">
                    <el-button class="no-focus dropdown-button">
                        <HelpBox />
                    </el-button>
                    <template #dropdown>
                        <el-dropdown-menu class="bg-transparent p-0">
                            <a href="https://kestra.io/slack"
                               target="_blank"
                               class="d-flex gap-2 el-dropdown-menu__item">
                                <HelpBox class="align-middle" /> {{ $t("live help") }}
                            </a>
                            <a href="https://kestra.io/docs"
                               target="_blank"
                               class="d-flex gap-2 el-dropdown-menu__item">
                                <BookMultipleOutline class="align-middle" /> {{ $t("documentation") }}
                            </a>
                            <router-link
                                :to="{name: 'plugins/list'}"
                                class="d-flex gap-2 el-dropdown-menu__item">
                                <GoogleCirclesExtended class="align-middle" /> {{ $t("plugins.names") }}
                            </router-link>
                            <a href="https://github.com/kestra-io/kestra/issues"
                               target="_blank"
                               class="d-flex gap-2 el-dropdown-menu__item">
                                <Github class="align-middle" /> {{ $t("github issue") }}
                            </a>
                            <a href="https://kestra.io/slack"
                               target="_blank"
                               class="d-flex gap-2 el-dropdown-menu__item">
                                <Slack class="align-middle" /> {{ $t("join community") }}
                            </a>
                            <a v-if="version"
                               :href="version.url"
                               target="_blank"
                               class="el-dropdown-menu__item">
                                New version available!
                            </a>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
                <news />
                <auth />
            </div>
        </div>
    </nav>
</template>
<script>
    import {mapState} from "vuex";
    import HomeOutline from "vue-material-design-icons/HomeOutline.vue";
    import Auth from "override/components/auth/Auth.vue";
    import News from "./News.vue";
    import SearchField from "./SearchField.vue";
    import Keyboard from "vue-material-design-icons/Keyboard.vue";
    import HelpBox from "vue-material-design-icons/HelpBox.vue";
    import BookMultipleOutline from "vue-material-design-icons/BookMultipleOutline.vue";
    import GoogleCirclesExtended from "vue-material-design-icons/GoogleCirclesExtended.vue";
    import Github from "vue-material-design-icons/Github.vue";
    import Slack from "vue-material-design-icons/Slack.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import GlobalSearch from "./GlobalSearch.vue"

    export default {
        components: {
            HomeOutline,
            Auth,
            News,
            SearchField,
            Keyboard,
            HelpBox,
            BookMultipleOutline,
            GoogleCirclesExtended,
            Github,
            Slack,
            Magnify,
            GlobalSearch
        },
        props: {
            title: {
                type: String,
                default: ""
            },
            breadcrumb: {
                type: Array,
                default: undefined
            }
        },
        computed: {
            ...mapState("api", ["version"]),
            displayNavBar() {
                return this.$route?.name !== "welcome";
            }
        }
    };
</script>
<style lang="scss" scoped>
    nav {
        top: 0;
        position: sticky;
        z-index: 1000;
        padding: var(--spacer) calc(2 * var(--spacer));
        border-bottom: 1px solid var(--bs-border-color);
        background: var(--card-bg);

        h1 {
            line-height: 1.6;
            max-width: 40ch;
            flex-wrap: wrap;
            white-space: pre-wrap;
        }

        .side {
            .fixed-buttons {
                align-items: center;

                button, :deep(button), a, :deep(a) {
                    border: none;
                    font-size: var(--font-size-lg);
                    padding: calc(var(--spacer) / 4);
                }
            }

            :slotted(ul) {
                display: flex;
                list-style: none;
                padding: 0;
                margin: 0;
                gap: calc(var(--spacer) / 2);
                align-items: center;
            }
        }
    }
</style>
