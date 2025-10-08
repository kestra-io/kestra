<template>
    <div>
        <el-autocomplete
            ref="searchInput"
            class="flex-shrink-0"
            v-model="filter"
            @select="goTo"
            :fetchSuggestions="search"
            highlightFirstItem
            popperClass="global-search-popper"
            :placeholder="$t('jump to...')"
        >
            <template #prefix>
                <Magnify />
            </template>
            <template #suffix>
                <Keyboard title="Ctrl/Cmd + K" />
                <span class="d-none d-xl-block">Ctrl/Cmd + K</span>
            </template>
            <template #default="{item}">
                <router-link
                    :to="item.href"
                    class="d-flex gap-2"
                >
                    <div class="d-flex gap-2 nav-item-title">
                        <component v-if="item.icon?.element" :is="{...item.icon.element}" class="align-middle" /> {{ item.title }}
                    </div>
                    <ArrowRight class="is-justify-end" />
                </router-link>
            </template>
        </el-autocomplete>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted} from "vue";
    import {useRouter} from "vue-router";
    import {useLeftMenu} from "override/components/useLeftMenu";
    import Keyboard from "vue-material-design-icons/Keyboard.vue";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue";

    const router = useRouter();
    const {menu} = useLeftMenu()

    const filter = ref("");

    const navItems = computed(() => {
        return menu.value.flatMap(item => {
            if(item.hidden) {
                return [];
            }
            if(item.child) {
                return item.child.filter(c => !c.hidden).map(c => {
                    if (!c.icon?.element) {
                        c.icon = item.icon;
                    }

                    return c;
                });
            }

            return item;
        }).filter(item => item.href);
    });

    const keyDown = (e: KeyboardEvent) => {
        if ((e.ctrlKey || e.metaKey) && !e.shiftKey && e.key === "k") {
            e.preventDefault();
            searchInput.value.focus();
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

<style scoped lang="scss">
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
