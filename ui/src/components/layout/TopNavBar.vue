<template>
    <b-navbar v-if="topNavbar" :class="menuCollapsed" class="top-line" type="dark" variant="dark">
        <b-navbar-nav>
            <b-nav-text>
                <h1>{{ title }}</h1>

                <b-breadcrumb>
                    <b-breadcrumb-item>
                        <router-link :to="{name: 'home'}">
                            <home /> {{ $t('home') }}
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
    import Home from "vue-material-design-icons/Home";
    import Auth from "override/components/auth/Auth";

    export default {
        components: {
            Home,
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
}

.top-line {
    border-bottom: 4px solid $secondary;
}

.top-line {
    border-bottom: 4px solid $secondary;
}

h1 {
    color: $secondary;
    font-size: $h3-font-size;
    margin-bottom: $headings-margin-bottom / 2;
    font-weight: bold;
}

.navbar-expand .navbar-nav .navbar-text {
    padding: 0
}

ol.breadcrumb {
    border: 0;
    padding: 0;
    margin-bottom: 0;
    background: transparent;
    font-size: $font-size-xs;
    text-transform: none;
    a {
        color: $gray-500;
    }
}
</style>
