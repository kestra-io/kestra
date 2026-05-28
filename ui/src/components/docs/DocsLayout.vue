<template>
    <div class="d-flex full-height docs-layout-container">
        <div
            v-if="mobileMenuOpen && $slots.menu"
            class="mobile-backdrop"
            @click="mobileMenuOpen = false"
        />

        <div v-if="$slots.menu" :style="{flex: collapsed ? '0 1 0px' : '0 0 306px'}" :class="[{collapsed}, {'mobile-open': mobileMenuOpen}]" class="sidebar d-flex flex-column gap-3">
            <KsButton
                v-if="isPluginsRoute"
                :class="['mobile-close-toggle']"
                @click="mobileMenuOpen = false"
                :icon="Close"
                :aria-label="'Close menu'"
                link
            />
            <div v-if="!collapsed" class="menu-slot-wrapper">
                <slot name="menu" />
            </div>
        </div>
        <div class="main-content-wrapper">
            <div v-if="$slots['secondary-header']" class="secondary-header">
                <KsButton
                    v-if="$slots.menu && isPluginsRoute"
                    :class="['mobile-menu-toggle']"
                    @click="mobileMenuOpen = !mobileMenuOpen"
                    :icon="Menu"
                    :aria-label="'Open menu'"
                    link
                />
                <slot name="secondary-header" />
            </div>
            <div class="main-container">
                <div class="content">
                    <slot name="content" />
                </div>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, watch} from "vue"
    import {useRoute} from "vue-router"
    import {useScrollMemory} from "../../composables/useScrollMemory"
    import Menu from "vue-material-design-icons/Menu.vue"
    import Close from "vue-material-design-icons/Close.vue"

    const collapsed = ref(false)
    const mobileMenuOpen = ref(false)
    const route = useRoute()
    const scrollKey = computed(() => `docs:${route.fullPath}`)

    const isPluginsRoute = computed(() => {
        return route.path.startsWith("/main/plugins") ||
            (typeof route.name === "string" && route.name.startsWith("plugins/"))
    })

    useScrollMemory(scrollKey, undefined, true)

    watch(() => route.fullPath, () => {
        mobileMenuOpen.value = false
    })

</script>

<style scoped lang="scss">
    @use 'element-plus/theme-chalk/src/mixins/mixins' as *;

    .sidebar {
        background: var(--ks-bg-surface);
        padding: 2rem;
        height: 100%;
        position: relative;
        overflow-y: auto;

        &.collapsed {
            padding: 2rem .5rem;
            background: transparent;
        }

        .toggle-btn {
            white-space:nowrap;
            font-size: var(--ks-font-size-xs);
        }

        > div > ul > li > span:first-child {
            font-size: var(--ks-font-size-xs);
        }
    }

    .menu-slot-wrapper {
            display: flex;
            flex-direction: column;
            gap: 1rem;
            flex: 1;
            min-height: 0;
            overflow: hidden;
    }

    .main-content-wrapper {
        flex: 1;
        display: flex;
        flex-direction: column;
        min-height: 0;
        height: 100%;
        overflow-y: auto;
    }

    .secondary-header {
        background-color: var(--ks-bg-surface);
        display: flex;
        align-items: center;
        min-height: 64px;
        flex-shrink: 0;
        position: sticky;
        top: 0;
        z-index: 100;

        .mobile-menu-toggle {
            display: none;
        }
    }

    .main-container {
        flex: 1;
        background-color: var(--ks-bg-surface);
        position: relative;
        min-height: 0;
        overflow-y: auto;
    }

    .content {
        margin: 0;
        padding: 1rem;
        background-color: var(--ks-bg-surface);

        h1 {
            margin-bottom: 0.5rem;
        }
    }

    .mobile-menu-toggle {
        display: none;
    }

    .mobile-close-toggle {
        display: none;
    }

    .mobile-backdrop {
        display: none;
    }


    @media (max-width: 991px) {
        .secondary-header {
            border-bottom: 1px solid var(--ks-border-default);

            .mobile-menu-toggle {
                display: flex;
                align-items: center;
                justify-content: center;
                width: 44px;
                height: 44px;
                padding: 0;
                padding-left: 1rem;
                flex-shrink: 0;
                transition: transform 0.2s ease;

                &:hover {
                    transform: scale(1.1);
                }

                &:active {
                    transform: scale(0.95);
                }

                :deep(.material-design-icon) {
                    width: 24px;
                    height: 24px;
                }
            }
        }

        .mobile-close-toggle {
            display: flex;
            align-items: center;
            justify-content: center;
            position: absolute;
            top: 1rem;
            right: 1rem;
            z-index: 1001;
            width: 44px;
            height: 44px;
            padding: 0;
            transition: transform 0.2s ease;

            &:hover {
                transform: scale(1.1);
            }

            &:active {
                transform: scale(0.95);
            }

            :deep(.material-design-icon) {
                width: 24px;
                height: 24px;
            }
        }

        .mobile-backdrop {
            display: block;
            position: fixed;
            top: 0;
            left: 0;
            right: 0;
            bottom: 0;
            background: rgba(0, 0, 0, 0.6);
            z-index: 999;
            animation: fadeIn 0.3s ease;
        }

        @keyframes fadeIn {
            from { opacity: 0; }
            to { opacity: 1; }
        }

        .sidebar {
            position: fixed;
            left: -100%;
            top: 0;
            height: 100vh;
            width: calc(100vw - 44px);
            max-width: 100vw;
            z-index: 1000;
            transition: left 0.3s ease-in-out;
            box-shadow: 2px 0 8px rgba(0, 0, 0, 0.15);
            padding: 1rem;
            padding-top: 3.5rem;
            padding-right: 0.5rem;
            display: flex;
            flex-direction: column;
            overflow: hidden;

            &.mobile-open {
                left: 0;
            }

            > div {
                flex: 1;
                overflow: hidden;
                display: flex;
                flex-direction: column;
            }
        }

        .main-container {
            width: 100%;
            padding: 0;
            overflow-y: auto;
        }

        .content {
            margin: 0;
            padding: 0.75rem;
            background-color: var(--ks-bg-surface);
        }
    }

    @media (min-width: 576px) and (max-width: 991px) {
        .sidebar {
            width: 90vw;
            max-width: 450px;
            top: 65px;
        }
    }

    @include res(md) {
        .mobile-menu-toggle {
            display: none;
        }

        .mobile-close-toggle {
            display: none;
        }

        .mobile-backdrop {
            display: none;
        }

        .sidebar {
            position: sticky;
            left: auto;
            top: 0;
            height: 100vh;
            width: auto;
            box-shadow: none;
            padding: 2rem;

            &.mobile-open {
                left: auto;
            }
        }

        .main-content-wrapper {
            overflow-y: auto;
        }

        .secondary-header {
            border-bottom: none;
        }

        .content {
            padding: 1rem;

            h1 {
                margin-bottom: 0.75rem;
            }
        }
    }

    @include res(lg) {
        .content {
            padding: 2rem;
            padding-top: 1rem;

            h1 {
                margin-bottom: 1rem;
            }
        }
    }
</style>