<template>
    <a :href="hrefWithDefault" @click.prevent="emit('navigate', hrefWithDefault)">
        <div class="ks-subgroup-card" :class="{'is-active': isActive}">
            <div class="top-row">
                <div class="icon-content">
                    <img v-if="iconSrc" :src="iconSrc" :alt="`${text} icon`">
                </div>
                <div class="text-content">
                    <h6>{{ text }}</h6>
                    <p v-if="description" class="description">{{ description }}</p>
                </div>
            </div>
            <div class="footer">
                <hr>
                <div class="bottom-row">
                    <div class="left">
                        <p v-if="totalCount">{{ totalCount }} <span>Tasks</span></p>
                        <p v-if="blueprintsCount">{{ blueprintsCount }} <span>Blueprints</span></p>
                    </div>
                    <KsIcon name="chevron-right" />
                </div>
            </div>
        </div>
    </a>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import KsIcon from "../Basic/KsIcon.vue"
    import {slugifyPlugin} from "../../utils/plugins"

    defineOptions({name: "KsSubgroupCard"})

    const props = withDefaults(defineProps<{
        iconSrc?: string;
        text: string;
        routePath: string;
        totalCount?: number;
        description?: string;
        href?: string;
        isActive?: boolean;
        blueprintsCount?: number;
    }>(), {
        totalCount: 0,
        description: "",
        blueprintsCount: 0,
        isActive: false,
    })

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

    .ks-subgroup-card {
        width: 100%;
        height: 152px;
        border-radius: 12px;
        border: 1px solid var(--ks-border-secondary);
        padding: 1rem;
        background: var(--ks-background-card);
        display: flex;
        flex-direction: column;
        box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
        transition: border-color 0.4s ease-out, box-shadow 0.4s ease-out, transform 0.4s ease-out;

        &:hover {
            border-color: var(--ks-border-active);
            box-shadow: 0 4px 18px 0 rgba(0, 0, 0, 0.25);
            transform: scale(1.025);
        }

        &.is-active {
            border-color: var(--ks-border-active);
        }

        .top-row {
            display: flex;
            flex-direction: row;
            gap: 1rem;
            align-items: flex-start;
            margin-bottom: 1rem;
        }

        .icon-content {
            width: 60px;
            height: 60px;
            background: var(--ks-background-secondary);
            border-radius: 8px;
            border: 1px solid var(--ks-border-secondary);
            display: flex;
            align-items: center;
            justify-content: center;
            flex-shrink: 0;

            img {
                width: 45px;
                height: 45px;
            }
        }

        .text-content {
            flex: 1;
            display: flex;
            flex-direction: column;
            gap: 0.5rem;
            overflow: hidden;

            .description {
                color: var(--ks-content-secondary);
                font-size: 12px;
                line-height: 1rem;
                margin: 0;
                overflow: hidden;
                display: -webkit-box;
                line-clamp: 2;
                -webkit-line-clamp: 2;
                -webkit-box-orient: vertical;
            }
        }

        h6 {
            color: var(--ks-content-primary);
            font-size: 1rem;
            font-weight: 700;
            margin: 0;
            text-overflow: ellipsis;
            overflow: hidden;
            white-space: nowrap;
            line-height: 1.5rem;
            padding: 0.25rem 0;
        }

        hr {
            border: 1px solid var(--ks-border-primary);
            margin: 0;
        }

        .footer {
            margin-top: auto;
            display: flex;
            flex-direction: column;
        }

        .bottom-row {
            display: flex;
            justify-content: space-between;
            align-items: center;
            color: var(--ks-content-secondary);
            height: 45px;

            .left {
                display: flex;
                gap: 1rem;
                align-items: center;

                p {
                    margin: 0;
                    font-weight: 700;
                    font-size: 14px;
                }

                span {
                    color: var(--ks-content-secondary);
                    font-weight: normal;
                    font-size: 12px;
                }
            }
        }
    }
</style>
