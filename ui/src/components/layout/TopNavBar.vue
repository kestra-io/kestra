<template>
    <nav>
        <div class="top-title" v-if="topNavbar">
            <h1 class="text-truncate">
                {{ title }}
            </h1>
            <el-breadcrumb>
                <el-breadcrumb-item>
                    <router-link :to="{name: 'home'}">
                        <home-outline /> {{ $t('home') }}
                    </router-link>
                </el-breadcrumb-item>

                <el-breadcrumb-item v-for="(item, x) in topNavbar.breadcrumb" :key="x">
                    <router-link :to="item.link">
                        {{ item.label }}
                    </router-link>
                </el-breadcrumb-item>
            </el-breadcrumb>
        </div>
        <div class="side ms-auto ps-2">
            <a v-if="version" :href="version.url"
               target="_blank"
               class="el-button el-button--small version is-text is-has-bg">
                🎉 New release v{{ version.latest }}
            </a>
            <a href="https://kestra.io/slack"
               target="_blank"
               class="el-button el-button--small is-text is-has-bg">
                Live help
            </a>
            <news />
            <auth />
        </div>
    </nav>
</template>
<script>
    import {mapState} from "vuex";
    import HomeOutline from "vue-material-design-icons/HomeOutline.vue";
    import Auth from "override/components/auth/Auth.vue";
    import News from "../layout/News.vue";

    export default {
        components: {
            HomeOutline,
            Auth,
            News,
        },
        computed: {
            ...mapState("layout", ["topNavbar"]),
            ...mapState("api", ["version"]),
            title() {
                return this.topNavbar.title;
            },
        }
    };
</script>
<style lang="scss" scoped>
    nav {
        display: flex;
        min-width: 0;
        max-width: 100%;
        padding-top: calc(var(--spacer) * 2);
        margin-bottom: calc(var(--spacer) * 2);

        .top-title {
            overflow: hidden;
            h1 {
                color: var(--bs-black);
                margin-bottom: calc(var(--spacer) * 0.5);
                font-weight: bold;

                html.dark & {
                    color: var(--bs-white);
                }
            }
        }

        .side {
            display: flex;
            flex-wrap: nowrap;
            > * {
                white-space: nowrap;
            }

            :deep(.el-button) {
                border: 0;
                padding-left: 8px;
                padding-right: 8px;
                color: var(--bs-gray-700);
                background-color: transparent;

                .material-design-icon {
                    font-size: var(--font-size-lg);
                }
            }
        }
    }
</style>
