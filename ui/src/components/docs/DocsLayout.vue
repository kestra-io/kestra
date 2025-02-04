<template>
    <div class="d-flex full-height">
        <div v-if="$slots.menu" :style="{flex: collapsed ? '0 1 0px' : '0 0 306px'}" :class="{collapsed}" class="sidebar d-flex flex-column gap-3">
            <!--
            <div class="d-flex gap-2 align-items-center">
                <el-button @click="collapsed = !collapsed">
                    <chevron-right v-if="collapsed" />
                    <chevron-left v-else />
                </el-button>
                <span class="toggle-btn text-body-tertiary">{{ $t((collapsed ? 'open' : 'close') + ' sidebar').toUpperCase() }}</span>
            </div>
            -->
            <div v-if="!collapsed" class="d-flex flex-column gap-3">
                <slot name="menu" />
            </div>
        </div>
        <div class="container main-container">
            <div class="content">
                <slot name="content" />
            </div>
        </div>
    </div>
</template>

<script setup>

</script>

<script>
    export default {
        data() {
            return {
                collapsed: false,
            }
        }
    };
</script>

<style lang="scss" scoped>
    @import "@kestra-io/ui-libs/src/scss/variables";

    .sidebar {
        background: var(--ks-background-card);
        padding: 2rem;
        height: calc(100vh - 80px);
        top: 80px;
        position: sticky;
        overflow-y: auto;

        &::-webkit-scrollbar {
            width: 8px;
        }

        &::-webkit-scrollbar-track {
            background: var(--ks-background-card);
        }

        &::-webkit-scrollbar-thumb {
            background: var(--ks-button-background-primary);
            border-radius: 0px;
        }

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

    .content {
        margin: $spacer;

        h1 {
            margin-bottom: $spacer;
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
            margin: 2rem 0;
            padding-top: 35.25%;
            background-color: var(--ks-background-body);
            height: 28.351rem;
            border-radius: calc($spacer / 2);
            border: 1px solid var(--ks-border-secondary);

            @media only screen and (max-width: 1919px) {
                padding-top: 56.25%;
                height: auto;
                background-color: transparent;
            }

            iframe {
                margin: auto;
                max-width: 43.7rem;
                max-height: 24.351rem;
                position: absolute;
                top: 0;
                left: 0;
                bottom: 0;
                right: 0;
                width: 100%;
                height: 100%;
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
            border: 1px solid var(--ks-border-info);
            border-radius: 4px;
            color: var(--ks-content-info);
            background: var(--ks-background-info);
        }
    }
</style>