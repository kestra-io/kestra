<template>
    <el-dropdown trigger="click" popper-class="hide-arrow overflow-hidden separator-m-0 global-search-popper">
        <search-field class="align-items-center" @search="search" :router="false" ref="search" placeholder="jump to...">
            <template #prefix>
                <magnify />
            </template>
            <template #suffix>
                <keyboard /><span>Ctrl/Cmd + K</span>
            </template>
        </search-field>
        <template #dropdown>
            <el-dropdown-menu class="bg-transparent p-0">
                <div v-for="(page, idx) in pages">
                    <router-link
                        :to="page.href"
                        class="d-flex gap-2 el-dropdown-menu__item">
                        <div class="d-flex gap-2 page-title">
                            <component :is="page.icon.element" class="align-middle" /> {{ page.title }}
                        </div>
                        <arrow-right />
                    </router-link>
                    <div class="el-dropdown-menu__item--divided" v-if="idx < pages.length - 1"/>
                </div>
            </el-dropdown-menu>
        </template>
    </el-dropdown>
</template>

<script>
    import GoogleCirclesExtended from "vue-material-design-icons/GoogleCirclesExtended.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import Keyboard from "vue-material-design-icons/Keyboard.vue";
    import SearchField from "./SearchField.vue";
    import LeftMenu from "override/components/LeftMenu.vue";
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue";
    import {mapGetters, mapState} from "vuex";

    export default {
        components: {
            GoogleCirclesExtended,
            SearchField,
            Magnify,
            Keyboard,
            ArrowRight
        },
        data() {
            return {
                filter: ""
            }
        },
        methods: {
            keyDown(e) {
                if((e.ctrlKey || e.metaKey) && e.key === "k") {
                    e.preventDefault();
                    this.$refs.search.$el.querySelector("input").click();
                }
            },
            routeStartWith(route) {
                return this.$router.getRoutes().filter(r => r.name.startsWith(route)).map(r => r.name);
            },
            search(query) {
                this.filter = query;
            }
        },
        mounted() {
            window.addEventListener("keydown", this.keyDown);
        },
        unmounted() {
            window.removeEventListener("keydown", this.keyDown);
        },
        computed: {
            ...mapState("auth", ["user"]),
            ...mapGetters("misc", ["configs"]),
            pages() {
                return LeftMenu.methods.generateMenu.call(this).flatMap(item => {
                    if(item.hidden) {
                        return [];
                    }
                    if(item.child) {
                        return item.child.filter(c => !c.hidden);
                    }

                    return item;
                }).filter(item => item.title.toLowerCase().includes(this.filter.toLowerCase()));
            }
        }
    };
</script>

<style>
    .global-search-popper {
        .page-title {
            width: 30ch;
        }
    }
</style>