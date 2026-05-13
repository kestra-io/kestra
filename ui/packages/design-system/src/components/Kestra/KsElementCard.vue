<template>
    <a :href="hrefWithDefault" @click.prevent="emit('navigate', hrefWithDefault)">
        <div class="ks-element-card">
            <div class="top-row">
                <h6 class="text-capitalize">{{ text }}</h6>
                <KsIcon name="chevron-right" />
            </div>
            <div v-if="title">
                <slot name="markdown" :content="title.replace(/ *:(?![ /])/g, ': ')" />
            </div>
            <div class="plugin-info">
                <code class="plugin-class">{{ pluginClass }}</code>
            </div>
        </div>
    </a>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import KsIcon from "../Basic/KsIcon.vue"
    import {slugifyPlugin} from "../../utils/plugins"

    defineOptions({name: "KsElementCard"})

    const props = defineProps<{
        text: string;
        routePath: string;
        pluginClass: string;
        href?: string;
        title?: string;
    }>()

    const emit = defineEmits<{(e: "navigate", url: string): void}>()

    const hrefWithDefault = computed(() =>
        props.href ?? `${props.routePath}/${slugifyPlugin(props.text)}`,
    )
</script>

<style scoped lang="scss">
    a {
        display: block;
        height: 100%;
        text-decoration: none;
    }

    .ks-element-card {
        width: 100%;
        height: 100%;
        border-radius: 12px;
        border: 1px solid var(--ks-border-secondary);
        padding: 1rem;
        background: var(--ks-background-card);
        display: grid;
        grid-template-columns: 1fr;
        grid-template-rows: auto auto 1fr;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        transition: border-color 0.4s ease-out, box-shadow 0.4s ease-out, transform 0.4s ease-out;

        &:hover {
            border-color: var(--ks-border-active);
            box-shadow: 0 4px 18px 0 rgba(0, 0, 0, 0.25);
            transform: scale(1.025);
        }

        .top-row {
            display: flex;
            flex-direction: row;
            justify-content: space-between;
            min-width: 0;
        }

        h6 {
            color: var(--ks-content-primary);
            font-size: 1rem;
            font-weight: 700;
            margin: 0;
            line-height: 1.5rem;
            padding: 0.25rem 0;
            overflow: hidden;
            text-overflow: ellipsis;
            white-space: nowrap;
            flex: 1 1 auto;
            min-width: 0;
        }

        .plugin-info {
            margin-top: 1rem;
            min-width: 50px;
            max-width: fit-content;
            background: var(--ks-background-panel);
            border-radius: 4px;
            padding: 0.5rem;
            border: 1px solid var(--ks-border-secondary);

            .plugin-class {
                color: var(--ks-content-link);
                font-size: 12px;
                text-overflow: ellipsis;
                overflow: hidden;
                white-space: nowrap;
                display: block;
            }
        }

        :deep(p) {
            color: var(--ks-content-secondary);
            font-size: 12px;
            line-height: 1rem;
            margin: 0;
            margin-top: 0.5rem;
            overflow: hidden;
            display: -webkit-box;
            -webkit-line-clamp: 2;
            line-clamp: 2;
            -webkit-box-orient: vertical;
        }
    }
</style>
