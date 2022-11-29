<template>
    <nav v-if="topNavbar" class="top-line">
        <ul>
            <li class="top-title">
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
            </li>
        </ul>
        <ul class="ml-auto">
            <li class="pt-3">
                <Auth />
                <news />
            </li>
        </ul>
    </nav>
</template>
<script>
    import {mapState} from "vuex";
    import HomeOutline from "vue-material-design-icons/HomeOutline";
    import Auth from "override/components/auth/Auth";
    import News from "../layout/News";

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
<style lang="scss">
    @use "sass:math";
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;
    @import "../../styles/variable";

    .top-line {
        display: flex;
        min-width: 0;
        max-width: 100%;
        margin-bottom: 10px;

        ul {
            display: flex;
            list-style: none;
            margin: 0;
            flex-wrap: nowrap;
            padding: 0;
            flex-direction: row;
            padding-top: 35px;
        }

      .top-title {
            overflow: hidden;
            h1 {
                color: var(--el-primary);
                margin-bottom: math.div($headings-margin-bottom, 2);
                font-weight: bold;

                html.dark & {
                    color: var(--tertiary);
                }
            }

            .el-breadcrumb {
                border: 0;
                padding: 0;
                margin-bottom: 0;
                background: transparent;
                font-size: $font-size-sm;
                text-transform: none;
                a {
                    color: getCssVar('color', 'tertiary');

                    html.dark & {
                        color: getCssVar('color', 'secondary');
                    }
                }
            }
        }
    }


</style>
