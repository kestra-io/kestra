<template>
    <nav data-component="FILENAME_PLACEHOLDER" class="d-flex w-100 gap-3 top-bar" v-if="displayNavBar">
        <div class="d-flex flex-column flex-grow-1 flex-shrink-1 overflow-hidden top-title">
            <el-breadcrumb v-if="breadcrumb">
                <el-breadcrumb-item v-for="(item, x) in breadcrumb" :key="x">
                    <router-link :to="item.link">
                        {{ item.label }}
                    </router-link>
                </el-breadcrumb-item>
            </el-breadcrumb>
            <h1 class="h5 fw-semibold m-0 d-inline-fle">
                <slot name="title">
                    {{ title }}
                </slot>
            </h1>
        </div>
        <div class="d-flex side gap-2 flex-shrink-0">
            <div class="d-none d-lg-flex align-items-center">
                <global-search class="trigger-flow-guided-step" />
            </div>
            <slot name="additional-right" />
            <div class="d-flex fixed-buttons">
                <el-dropdown popper-class="">
                    <el-button class="no-focus dropdown-button">
                        <HelpBox />
                    </el-button>
                    <template #dropdown>
                        <el-dropdown-menu>
                            <a
                                href="https://kestra.io/slack?utm_source=app&utm_content=top-nav-bar"
                                target="_blank"
                                class="d-flex gap-2 el-dropdown-menu__item"
                            >
                                <HelpBox class="align-middle" /> {{ $t("live help") }}
                            </a>
                            <a
                                v-if="tourEnabled"
                                @click="restartGuidedTour"
                                class="d-flex gap-2 el-dropdown-menu__item"
                            >
                                <ProgressQuestion class="align-middle" /> {{ $t('Reset guided tour') }}
                            </a>

                            <a
                                href="https://kestra.io/docs?utm_source=app&utm_content=top-nav-bar"
                                target="_blank"
                                class="d-flex gap-2 el-dropdown-menu__item"
                            >
                                <BookMultipleOutline class="align-middle" /> {{ $t("documentation.documentation") }}
                            </a>
                            <a
                                href="https://github.com/kestra-io/kestra/issues"
                                target="_blank"
                                class="d-flex gap-2 el-dropdown-menu__item"
                            >
                                <Github class="align-middle" /> {{ $t("documentation.github") }}
                            </a>
                            <a
                                href="https://kestra.io/slack?utm_source=app&utm_content=top-nav-bar"
                                target="_blank"
                                class="d-flex gap-2 el-dropdown-menu__item"
                            >
                                <Slack class="align-middle" /> {{ $t("join community") }}
                            </a>
                            <a
                                href="https://kestra.io/demo?utm_source=app&utm_content=top-nav-bar"
                                target="_blank"
                                class="d-flex gap-2 el-dropdown-menu__item"
                            >
                                <EmailHeartOutline class="align-middle" /> {{ $t("reach us") }}
                            </a>
                            <a
                                v-if="version"
                                :href="version.url"
                                target="_blank"
                                class="d-flex gap-2 el-dropdown-menu__item"
                            >
                                <Update class="align-middle text-danger" /> <span class="text-danger">{{ $t("new version", {"version": version.latest}) }}</span>
                            </a>
                        </el-dropdown-menu>
                    </template>
                </el-dropdown>
                <news />
                <impersonating />
                <auth />
            </div>
        </div>
    </nav>
</template>
<script>
    import {mapState, mapGetters} from "vuex";
    import Auth from "override/components/auth/Auth.vue";
    import Impersonating from "override/components/auth/Impersonating.vue";
    import News from "./News.vue";
    import HelpBox from "vue-material-design-icons/HelpBox.vue";
    import BookMultipleOutline from "vue-material-design-icons/BookMultipleOutline.vue";
    import Github from "vue-material-design-icons/Github.vue";
    import Slack from "vue-material-design-icons/Slack.vue";
    import EmailHeartOutline from "vue-material-design-icons/EmailHeartOutline.vue";
    import Update from "vue-material-design-icons/Update.vue";
    import ProgressQuestion from "vue-material-design-icons/ProgressQuestion.vue";
    import GlobalSearch from "./GlobalSearch.vue";

    export default {
        components: {
            Auth,
            News,
            HelpBox,
            BookMultipleOutline,
            Github,
            Slack,
            EmailHeartOutline,
            Update,
            ProgressQuestion,
            GlobalSearch,
            Impersonating
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
            ...mapState("core", ["tutorialFlows"]),
            ...mapGetters("core", ["guidedProperties"]),
            ...mapGetters("auth", ["user"]),
            displayNavBar() {
                return this.$route?.name !== "welcome";
            },
            tourEnabled(){
                // Temporary solution to not showing the tour menu item for EE
                return this.tutorialFlows?.length && !Object.keys(this.user).length
            }
        },
        methods: {
            restartGuidedTour() {
                localStorage.setItem("tourDoneOrSkip", undefined);
                this.$store.commit("core/setGuidedProperties", {tourStarted: false});

                this.$tours["guidedTour"]?.start();
            }
        }
    };
</script>,
<style lang="scss" scoped>
    nav {
        top: 0;
        position: sticky;
        z-index: 1000;
        padding: var(--spacer) calc(2 * var(--spacer));
        border-bottom: 1px solid var(--bs-border-color);
        background: var(--card-bg);

        .top-title, h1, .el-breadcrumb {
            white-space: nowrap;
            max-width: 100%;
            text-overflow: ellipsis;
            overflow: hidden;
        }

        h1 {
            line-height: 1.6;
            display: block !important;
        }

        :deep(.el-breadcrumb__item) {
            display: inline-block;
        }


        :deep(.el-breadcrumb__inner) {
            white-space: nowrap;
            max-width: 100%;
            text-overflow: ellipsis;
            overflow: hidden;
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
