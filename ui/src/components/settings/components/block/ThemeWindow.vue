<template>
    <svg class="theme-window" viewBox="0 0 124 85" fill="none" xmlns="http://www.w3.org/2000/svg">
        <g :clip-path="`url(#${clipId})`">
            <path class="tw-sidebar" d="M4 3H35.0202V79.7949H4V3Z" />
            <path class="tw-divider" d="M35.0202 3H34.3138V79.7949H35.0202H35.7265V3H35.0202Z" />
            <rect class="tw-main" x="35.0205" y="3" width="84.6478" height="76.7949" />
            <rect class="tw-bar" x="40.4268" y="9.1416" width="73.6402" height="8.19056" rx="4.09528" />
            <rect class="tw-bar" x="40.4268" y="21.8047" width="73.6402" height="2.99437" rx="1.49719" />
            <rect class="tw-bar" x="40.4268" y="27.2061" width="73.6402" height="2.99437" rx="1.49719" />
            <rect class="tw-bar" x="40.4268" y="32.6074" width="73.6402" height="2.99437" rx="1.49719" />
            <rect class="tw-panel" x="40.4268" y="48.623" width="73.6402" height="25.9016" rx="7.06344" />
            <rect class="tw-dot" x="7.83203" y="7.64453" width="3.99078" height="2.99437" rx="1.49719" />
            <rect class="tw-bar" x="7.83203" y="15.835" width="23.3563" height="2.99437" rx="1.49719" />
            <rect class="tw-bar" x="7.83203" y="21.8047" width="23.3563" height="2.99437" rx="1.49719" />
            <rect class="tw-bar" x="7.83203" y="27.7754" width="23.3563" height="2.99437" rx="1.49719" />
            <rect class="tw-bar" x="7.83203" y="33.7451" width="23.3563" height="2.99437" rx="1.49719" />
            <rect class="tw-bar" x="7.83203" y="39.7158" width="23.3563" height="2.99437" rx="1.49719" />
        </g>
        <rect class="tw-frame" x="4.35317" y="3.35317" width="114.962" height="76.0886" rx="7.64683" stroke-width="0.706344" />
        <defs>
            <clipPath :id="clipId">
                <rect x="4" y="3" width="115.668" height="76.7949" rx="8" />
            </clipPath>
        </defs>
    </svg>
</template>

<script lang="ts">
    let counter = 0
</script>

<script setup lang="ts">
    const clipId = `theme-window-clip-${counter++}`
</script>

<style scoped lang="scss">
    .theme-window {
        display: block;
        width: 100%;
        height: auto;
        filter: drop-shadow(0 1px 2px rgba(0, 0, 0, 0.08));
    }

    .tw-sidebar { fill: var(--tp-sidebar); }
    .tw-divider { fill: var(--tp-divider); }
    .tw-main { fill: var(--tp-main); }
    .tw-bar { fill: var(--tp-bar); }
    .tw-panel { fill: var(--tp-panel); }
    .tw-dot { fill: #9869f7; }
    .tw-frame { stroke: var(--tp-frame); }
</style>
