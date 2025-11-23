<template>
    <div class="d-flex full-height docs-layout-container">
        <div 
            v-if="mobileMenuOpen && $slots.menu" 
            class="mobile-backdrop"
            @click="mobileMenuOpen = false"
        />
        
        <div v-if="$slots.menu" :style="{flex: collapsed ? '0 1 0px' : '0 0 306px'}" :class="[{collapsed}, {'mobile-open': mobileMenuOpen}]" class="sidebar d-flex flex-column gap-3">
            <el-button 
                v-if="isPluginsRoute" 
                :class="['mobile-close-toggle']"
                @click="mobileMenuOpen = false"
                :icon="Close"
                :aria-label="'Close menu'"
                link
            />
            <div v-if="!collapsed" class="d-flex flex-column gap-3">
                <slot name="menu" />
            </div>
        </div>
        <div class="main-content-wrapper">
            <div v-if="$slots['secondary-header']" class="secondary-header">
                <el-button 
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
    import {useRoute} from "vue-router";
    import {useScrollMemory} from "../../composables/useScrollMemory";
    import Menu from "vue-material-design-icons/Menu.vue";
    import Close from "vue-material-design-icons/Close.vue";

    const collapsed = ref(false);
    const mobileMenuOpen = ref(false);
    const route = useRoute();
    const scrollKey = computed(() => `docs:${route.fullPath}`);
    
    const isPluginsRoute = computed(() => {
        return route.path.startsWith("/main/plugins") ||
            (typeof route.name === "string" && route.name.startsWith("plugins/"));
    });

    useScrollMemory(scrollKey, undefined, true);

    watch(() => route.fullPath, () => {
        mobileMenuOpen.value = false;
    });

</script>

<style scoped lang="scss">
    @import "@kestra-io/ui-libs/src/scss/variables";

    .docs-layout-container {
        height: calc(100vh - 80px);
        overflow: hidden;
    }

    .sidebar {
        background: var(--ks-background-card);
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
            font-size: 12px;
        }

        > div > ul > li > span:first-child {
            font-size: 12px;
        }
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
        background-color: var(--ks-background-panel);
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
        background-color: var(--ks-background-panel);
        position: relative;
        min-height: 0;
        overflow-y: auto;
    }

    .content {
        margin: 0;
        padding: 1rem;
        background-color: var(--ks-background-panel);

        h1 {
            margin-bottom: 0.5rem;
        }

        #{--bs-link-color}: #8405FF;
        #{--bs-link-color-rgb}: to-rgb(#8405FF);

        html.dark & {
            #{--bs-link-color}: #BBBBFF;
            #{--bs-link-color-rgb}: to-rgb(#BBBBFF);
        }

        :deep(h2) {
            font-weight: 600;
            border-top: 1px solid var(--ks-border-primary);
            margin-bottom: 2rem;
            margin-top: 4.12rem;
            padding-top: 3.125rem;

            > a {
                border-left: 5px solid #9ca1de;
                font-size: 1.87rem;
                padding-left: .6rem;
            }
        }

        :deep(h3) {
            padding-top: 1.25rem;
        }

        :deep(.btn:hover span) {
            color: var(--ks-content-primary);
        }

        :deep(a[target=_blank]:after) {
            background-color: currentcolor;
            content: "";
            display: inline-block;
            height: 15px;
            margin-left: 1px;
            -webkit-mask: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' aria-hidden='true' focusable='false' x='0px' y='0px' viewBox='0 0 100 100' width='15' height='15' class='icon outbound'><path fill='currentColor' d='M18.8,85.1h56l0,0c2.2,0,4-1.8,4-4v-32h-8v28h-48v-48h28v-8h-32l0,0c-2.2,0-4,1.8-4,4v56C14.8,83.3,16.6,85.1,18.8,85.1z'></path> <polygon fill='currentColor' points='45.7,48.7 51.3,54.3 77.2,28.5 77.2,37.2 85.2,37.2 85.2,14.9 62.8,14.9 62.8,22.9 71.5,22.9'></polygon></svg>");
            mask: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' aria-hidden='true' focusable='false' x='0px' y='0px' viewBox='0 0 100 100' width='15' height='15' class='icon outbound'><path fill='currentColor' d='M18.8,85.1h56l0,0c2.2,0,4-1.8,4-4v-32h-8v28h-48v-48h28v-8h-32l0,0c-2.2,0-4,1.8-4,4v56C14.8,83.3,16.6,85.1,18.8,85.1z'></path> <polygon fill='currentColor' points='45.7,48.7 51.3,54.3 77.2,28.5 77.2,37.2 85.2,37.2 85.2,14.9 62.8,14.9 62.8,22.9 71.5,22.9'></polygon></svg>");
            vertical-align: baseline;
            width: 15px;
        }

        :deep(.code-block) {
            .language {
                color: var(--ks-content-tertiary);
            }
        }

        :deep(code) {
            white-space: break-spaces;

            &:not(.shiki code) {
                font-weight: 700;
                background: var(--ks-background-body);
                color: var(--ks-content-primary);
                border: 1px solid var(--border-killing)
            }
        }

        :deep(p > a) {
            text-decoration: underline;
        }

        :deep(blockquote) {
            border-left: 4px solid #8997bd;
            font-size: 1rem;
            padding-left: 1rem;

            > p {
                color: var(--ks-content-primary);
            }
        }

        :deep(.card-group) {
            justify-content: space-around;
        }

        :deep(.card-group > a), :deep(h2 > a), :deep(h3 > a) {
            color: var(--ks-content-primary);
        }

        :deep(li > a) {
            text-decoration: none !important;
        }

        :deep(.video-container) {
            position: relative;
            margin-top: 2rem;
            margin-bottom: -1rem;
            padding-top: 56.75%;
            overflow: hidden;
            background-color: var(--ks-background-body);
            border-radius: calc($spacer / 2);
            border: 1px solid var(--ks-border-secondary);

            iframe {
                position: absolute;
                top: 0;
                left: 0;
                margin: auto;
                width: 100%;
                height: 100%;
                max-width: 100%;
                max-height: 100%;
            }
        }

        :deep(.card) {
            --bs-card-spacer-y: 1rem;
            --bs-card-spacer-x: 1rem;
            border: 1px solid var(--ks-border-primary);
            color: var(--ks-content-primary);
            display: flex;
            flex-direction: column;
            min-width: 0;
            position: relative;
            word-wrap: break-word;
            background-clip: border-box;
            background-color: var(--ks-background-card);
            border-radius: var(--bs-border-radius-lg);

            .card-body {
                color: var(--ks-content-primary);
                flex: 1 1 auto;
                padding: 1rem;
                gap: 1rem;
            }
        }

        :deep(hr) {
            &:has(+ .card-group), &:has(+ .alert) {
                opacity: 0;
            }

            &:has(+ h2)  {
                display: none;
            }
        }

        :deep(p) {
            line-height: 1.75rem;
        }

        :deep(.material-design-icon) {
            bottom: -0.125em;
        }

        :deep(.show-button) > .material-design-icon.icon-2x {
            &, & > .material-design-icon__svg {
                height: 1em;
                width: 1em;
            }
        }

        :deep(.doc-alert) {
            padding-bottom: 1px !important;
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
        .docs-layout-container {
            height: 100vh;
        }
        
        .secondary-header {
            border-bottom: 1px solid var(--ks-border-primary);
            
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
            background-color: var(--ks-background-panel);
        }
    }

    @media (min-width: 576px) and (max-width: 991px) {
        .sidebar {
            width: 90vw;
            max-width: 450px;
            top: 65px;
        }
    }

    @include media-breakpoint-up(md) {
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
            height: auto;
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

    @include media-breakpoint-up(lg) {
        .content {
            padding: 2rem;
            padding-top: 1rem;

            h1 {
                margin-bottom: 1rem;
            }
        }
    }
</style>
