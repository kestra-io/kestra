<template>
    <b-navbar v-if="topNavbar" class="top-line" type="dark" variant="dark">
        <b-navbar-nav>
            <b-nav-item class="title" href="#">
                {{title | cap}} •
                <span v-for="(item, x) in topNavbar.breadcrumb" :key="x">
                    <router-link class="bread-item" :to="item.link || {}">
                        {{item.label | cap}}
                        <span v-if="x < topNavbar.breadcrumb.length - 1">
                            <chevron-right />
                        </span>
                    </router-link>
                </span>
            </b-nav-item>
            <!-- <b-nav-item-dropdown text="User" right>
                <b-dropdown-item href="#">Account</b-dropdown-item>
                <b-dropdown-item href="#">Settings</b-dropdown-item>
            </b-nav-item-dropdown>-->
        </b-navbar-nav>
    </b-navbar>
</template>
<script>
import { mapState } from "vuex";
import ChevronRight from "vue-material-design-icons/ChevronRight";

export default {
    components: {
        ChevronRight
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
h1 {
    font-size: 1.5em;
}
.top-line {
    left: 50px;
}
.bread-item {
    color: white;
}
</style>