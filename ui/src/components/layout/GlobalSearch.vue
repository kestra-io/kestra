<template>
    <div data-component="FILENAME_PLACEHOLDER">
        <el-autocomplete
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

<script setup>
    import {ref, computed, onMounted, onUnmounted} from "vue";
    import {useRouter} from "vue-router";
    import {useLeftMenu} from "override/components/useLeftMenu";
    import Keyboard from "vue-material-design-icons/Keyboard.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue";

    const router = useRouter();
    const {generateMenu} = useLeftMenu()

    const filter = ref("");

    const navItems = computed(() => {
        return generateMenu().flatMap(item => {
            if(item.hidden) {
                return [];
            }
            if(item.child) {
                return item.child.filter(c => !c.hidden);
            }

            return item;
        }).filter(item => item.href);
    });

    const keyDown = (e) => {
        if ((e.ctrlKey || e.metaKey) && e.key === "k") {
            e.preventDefault();
            searchInput.value.click();
        }
    };

    const search = (query, cb) => {
        cb(navItems.value.filter(item => item.title.toLowerCase().includes(query.toLowerCase())));
    };

    const goTo = (item) => {
        router.push(item.href);
    };

    const searchInput = ref(null);

    onMounted(() => {
        window.addEventListener("keydown", keyDown);
    });

    onUnmounted(() => {
        window.removeEventListener("keydown", keyDown);
    });
</script>

<style lang="scss" scoped>
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;
    div {
        :deep(.el-input) {
            font-size: var(--font-size-sm);

            .el-input__wrapper {
                background: transparent;
            }

            @include res(sm) {
                max-width: 135px;
            }

            @include res(lg) {
                max-width: 250px;
            }
        }
    }
</style>
