<template>
    <b-navbar v-if="topNavbar" :class="menuCollapsed" class="top-line">
        <b-navbar-nav class="top-title">
            <b-nav-text>
                <h1 class="text-truncate">
                    {{ title }}
                </h1>
                <b-breadcrumb>
                    <b-breadcrumb-item>
                        <router-link :to="{name: 'home'}">
                            <home-outline /> {{ $t('home') }}
                        </router-link>
                    </b-breadcrumb-item>
                    <b-breadcrumb-item v-for="(item, x) in topNavbar.breadcrumb" :to="item.link" :text="item.label" :key="x" />
                </b-breadcrumb>
            </b-nav-text>
        </b-navbar-nav>
        <Auth />
    </b-navbar>
</template>
<script>
    import {mapState} from "vuex";
    import HomeOutline from "vue-material-design-icons/HomeOutline";
    import Auth from "override/components/auth/Auth";

    export default {
        components: {
            HomeOutline,
            Auth,
        },
        props: {
            menuCollapsed : {
                type: String,
                required: true
            }
        },
        computed: {
            ...mapState("layout", ["topNavbar"]),
            title() {
                return this.topNavbar.title;
            },
            breadcrumb() {
                return this.topNavbar.breadcrumb.join(" > ");
            }
        }
    };
</script>
<style lang="scss" scoped>
@import "../../styles/variable";

.menu-collapsed {
    transition: all 0.3s ease;
    left: 50px;
    width: calc(100% - 50px);

}
.menu-not-collapsed {
    transition: all 0.3s;
    left: $menu-width;
    width: calc(100% - #{$menu-width});

    @media (min-width: map-get($grid-breakpoints, "lg")) {
        &.navbar {
            padding-left: 55px;
            padding-right: 40px;
        }
    }

}

.top-line {
    display: flex;
    min-width: 0;
    max-width: 100%;
}

.top-title {
    padding-top: 35px;
    overflow: hidden;
    .navbar-text {
        h1 {
            color: var(--primary);
            margin-bottom: $headings-margin-bottom / 2;
            font-weight: bold;

            .theme-dark & {
                color: var(--tertiary);
            }
        }

        ol.breadcrumb {
            border: 0;
            padding: 0;
            margin-bottom: 0;
            background: transparent;
            font-size: $font-size-sm;
            text-transform: none;
            a {
                color: var(--tertiary);

                .theme-dark & {
                    color: var(--secondary);
                }
            }
        }
    }
}

.navbar-expand .navbar-nav .navbar-text {
    padding: 0
}

</style>
