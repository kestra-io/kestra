<template>
    <div>
        <teleport to="body">
            <div v-if="isOpen" class="search-overlay" @click="closeSearch">
                <div class="search-modal" @click.stop>
                    <el-autocomplete
                        ref="searchInput"
                        class="flex-shrink-0"
                        v-model="filter"
                        @select="goTo"
                        :fetchSuggestions="search"
                        highlightFirstItem
                        popperClass="global-search-popper"
                        :placeholder="$t('jump to...')"
                        @keydown.esc="closeSearch"
                    >
                        <template #prefix>
                            <Magnify />
                        </template>
                        <template #suffix>
                            <el-button 
                                v-if="filter" 
                                class="close-button" 
                                text 
                                circle 
                                @click.stop="clearSearch"
                            >
                                <Close />
                            </el-button>
                            <span v-else class="d-none d-sm-block">
                                <kbd>ESC</kbd> to close
                            </span>
                        </template>
                        <template #default="{item}">
                            <router-link
                                :to="item.href"
                                class="d-flex gap-2"
                                @click="closeSearch"
                            >
                                <div class="d-flex gap-2 nav-item-title">
                                    <component v-if="item.icon?.element" :is="{...item.icon.element}" class="align-middle" /> {{ item.title }}
                                </div>
                                <ArrowRight class="is-justify-end" />
                            </router-link>
                        </template>
                    </el-autocomplete>
                </div>
            </div>
        </teleport>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted, nextTick} from "vue";
    import {useRouter} from "vue-router";
    import {useLeftMenu} from "override/components/useLeftMenu";
    import Magnify from "vue-material-design-icons/Magnify.vue";
    import ArrowRight from "vue-material-design-icons/ArrowRight.vue";
    import Close from "vue-material-design-icons/Close.vue";

    const router = useRouter();
    const {menu} = useLeftMenu()

    const filter = ref("");
    const isOpen = ref(false);
    const searchInput = ref<any>(null);

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
            openSearch();
        }
        if (e.key === "Escape" && isOpen.value) {
            e.preventDefault();
            closeSearch();
        }
    };

    const openSearch = () => {
        isOpen.value = true;
        nextTick(() => {
            searchInput.value?.focus();
        });
    }

    const closeSearch = () => {
        isOpen.value = false;
        filter.value = "";
    }

    const clearSearch = () => {
        filter.value = "";
        nextTick(() => {
            searchInput.value?.focus();
        });
    }

    const search = (query, cb) => {
        cb(navItems.value.filter(item => item.title.toLowerCase().includes(query.toLowerCase())));
    };

    const goTo = (item) => {
        router.push(item.href);
        closeSearch();
    };


    onMounted(() => {
        window.addEventListener("keydown", keyDown);
    });

    onUnmounted(() => {
        window.removeEventListener("keydown", keyDown);
    });
</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .search-overlay {
        position: fixed;
        top: 0;
        left: 0;
        width: 100vw;
        height: 100vh;
        background: rgba(0, 0, 0, 0.4);
        backdrop-filter: blur(3px);
        z-index: 10000;
        display: flex;
        justify-content: center;
        align-items: flex-start;
        padding-top: 15vh;
    }

    .search-modal {
        width: 600px;
        max-width: 90vw;

        :deep(.el-input) {
            font-size: var(--el-font-size-base);
            border-radius: var(--el-input-border-radius);
            
            .el-input__wrapper {
                background: var(--ks-background-card);
                box-shadow: 0 4px 12px rgba(0,0,0,0.2);
                padding: 8px 16px;
                border: 1px solid var(--el-input-border-color);
                border-radius: var(--el-input-border-radius);
                
                input {
                    height: 48px;
                    color: var(--ks-content-primary);
                }

                .close-button {
                    color: var(--ks-content-primary);
                    &:hover {
                        color: var(--ks-content-link);
                        background-color: var(--ks-border-primary);
                    }
                }
            }
        }
    }
</style>

<style lang="scss">
    .global-search-popper {
        z-index: 10001 !important;
    }
</style>
