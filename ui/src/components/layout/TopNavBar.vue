<template>
    <nav v-if="topNavbar">
        <div class="top-title">
            <h1 class="text-truncate">
                {{ title }}
            </h1>
            <el-breadcrumb>
                <el-breadcrumb-item>
                    <router-link :to="{name: 'home'}">
                        <home-outline /> {{ $t('home') }}
                    </router-link>
                </el-breadcrumb-item>

                <el-breadcrumb-item v-for="(item, x) in topNavbar.breadcrumb" :to="item.link" :key="x">
                    <router-link :to="item.link">
                        {{ item.label }}
                    </router-link>
                </el-breadcrumb-item>
            </el-breadcrumb>
        </div>
        <div class="side ms-auto ps-2">
            <auth />
            <news />
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
                color: var(--el-primary);
                margin-bottom: calc(var(--spacer) * 0.5);
                font-weight: bold;

                html.dark & {
                    color: var(--tertiary);
                }
            }

            .el-breadcrumb {
                color: var(--bs-secondary);
                display: flex;
                a {
                    font-weight: normal;
                    color: var(--bs-secondary);
                    white-space: nowrap;
                }

                .el-breadcrumb__separator {
                    color: var(--bs-tertiary);
                }

                .el-breadcrumb__item {
                    display: flex;
                    flex-wrap: nowrap;
                    float: none;
                }

                .material-design-icon {
                    height: 0.75rem;
                    width: 0.75rem;
                    margin-right: calc(var(--spacer) / 2);
                }

                a {
                    cursor: pointer !important;
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
                color: var(--bs-secondary);
                background-color: transparent;
            }
        }
    }
</style>
