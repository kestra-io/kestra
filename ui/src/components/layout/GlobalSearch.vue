<template>
    <div data-component="FILENAME_PLACEHOLDER">
        <el-autocomplete
            ref="search"
            class="flex-shrink-0"
            v-model="filter"
            @select="goTo"
            :fetch-suggestions="search"
            popper-class="global-search-popper"
            :placeholder="$t('jump to...')"
        >
            <template #prefix>
                <magnify />
            </template>
            <template #suffix>
                <keyboard title="Ctrl/Cmd + K" />
                <span class="d-none d-xl-block">Ctrl/Cmd + K</span>
            </template>
            <template #default="{item}">
                <router-link
                    :to="item.href"
                    class="d-flex gap-2"
                >
                    <div class="d-flex gap-2 nav-item-title">
                        <component :is="{...item.icon.element}" class="align-middle" /> {{ item.title }}
                    </div>
                    <arrow-right class="is-justify-end" />
                </router-link>
            </template>
        </el-autocomplete>
    </div>
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
                    this.$refs.search.inputRef.input.click();
                }
            },
            routeStartWith(route) {
                return this.$router.getRoutes().filter(r => r.name.startsWith(route)).map(r => r.name);
            },
            search(query, cb) {
                cb(this.navItems.filter(item => item.title.toLowerCase().includes(query.toLowerCase())));
            },
            goTo(item) {
                this.$router.push(item.href);
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
            navItems() {
                return LeftMenu.methods.generateMenu.call(this).flatMap(item => {
                    if(item.hidden) {
                        return [];
                    }
                    if(item.child) {
                        return item.child.filter(c => !c.hidden);
                    }

                    return item;
                });
            }
        }
    };
</script>

<style lang="scss" scoped>
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;
    div {
        :deep(.el-input) {
            font-size: var(--font-size-sm);

            @include res(sm) {
                max-width: 135px;
            }

            @include res(lg) {
                max-width: 250px;
            }
        }
    }
</style>
