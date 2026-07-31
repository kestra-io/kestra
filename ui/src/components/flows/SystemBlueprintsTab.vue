<template>
    <div class="system-blueprints-tab">
        <div class="recipe-header">
            <KsText tag="h2" class="section-heading">{{ $t("recipe.section_title") }}</KsText>
            <KsText tag="p" class="section-sub">{{ $t("recipe.section_subtitle") }}</KsText>
        </div>

        <FlowRecipe :namespace="systemNamespace" @submit="handleRecipeSubmit" />

        <div class="escape-hatches">
            <router-link
                :to="{name: 'blueprints', params: {tenant: route.params.tenant, kind: 'flow', tab: 'community'}}"
                class="blueprint-link"
                data-test="system-blueprints-link"
            >
                <div class="blueprint-link-icon">
                    <KsIcon size="base">
                        <ViewGridOutline />
                    </KsIcon>
                </div>
                <span class="blueprint-link-text">{{ $t("recipe.browse_blueprints") }}</span>
                <KsIcon size="sm" class="blueprint-link-arrow">
                    <ChevronRight />
                </KsIcon>
            </router-link>

            <router-link
                :to="{name: 'flows/create', params: {tenant: route.params.tenant}, query: {blank: 'true', namespace: systemNamespace}}"
                class="blueprint-link"
                data-test="system-blank-flow-link"
            >
                <div class="blueprint-link-icon">
                    <KsIcon size="base">
                        <Plus />
                    </KsIcon>
                </div>
                <span class="blueprint-link-text">{{ $t("recipe.start_blank") }}</span>
                <KsIcon size="sm" class="blueprint-link-arrow">
                    <ChevronRight />
                </KsIcon>
            </router-link>
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRouter, useRoute} from "vue-router"
    import {useMiscStore} from "override/stores/misc"
    import FlowRecipe from "./recipe/FlowRecipe.vue"
    import ViewGridOutline from "vue-material-design-icons/ViewGridOutline.vue"
    import Plus from "vue-material-design-icons/Plus.vue"
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue"
    import {RECIPE_PRESET_KEY} from "../../utils/storageKeys"

    const props = withDefaults(defineProps<{
        namespace?: string
    }>(), {
        namespace: undefined,
    })

    const miscStore = useMiscStore()
    const router = useRouter()
    const route = useRoute()

    const systemNamespace = computed(() => props.namespace ?? miscStore.configs?.systemNamespace ?? "system")

    const handleRecipeSubmit = ({yaml}: {id: string; namespace: string; yaml: string}) => {
        sessionStorage.setItem(RECIPE_PRESET_KEY, yaml)
        router.push({
            name: "flows/create",
            params: {tenant: route.params.tenant},
            query: {recipePreset: "true"},
        })
    }
</script>

<style scoped lang="scss">
    .system-blueprints-tab {
        display: flex;
        flex-direction: column;
    }

    .recipe-header {
        margin: 0 var(--ks-spacing-4);
    }

    .section-heading {
        margin: 0 0 var(--ks-spacing-1);
        font-size: var(--ks-font-size-xl);
        font-weight: var(--ks-font-weight-semibold);
    }

    .section-sub {
        margin: 0;
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .escape-hatches {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        margin: var(--ks-spacing-2) var(--ks-spacing-4) var(--ks-spacing-6);
    }

    .blueprint-link {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-3);
        padding: var(--ks-spacing-3) var(--ks-spacing-4);
        border: var(--ks-border-width-thin) solid var(--ks-border-default);
        border-radius: var(--ks-radius-base);
        background-color: var(--ks-bg-surface);
        color: var(--ks-text-secondary);
        text-decoration: none;
        transition: border-color var(--ks-duration-fast) var(--ks-ease-standard),
            color var(--ks-duration-fast) var(--ks-ease-standard);

        &:hover {
            border-color: var(--ks-border-strong);
            color: var(--ks-text-primary);
        }

        &:focus-visible {
            outline: var(--ks-border-width-base) solid var(--ks-border-focus);
            outline-offset: var(--ks-spacing-px);
        }
    }

    .blueprint-link-icon {
        display: flex;
        align-items: center;
        justify-content: center;
        width: var(--ks-spacing-6);
        height: var(--ks-spacing-6);
        border-radius: var(--ks-radius-sm);
        background-color: var(--ks-bg-tag);
        flex-shrink: 0;
        color: var(--ks-text-primary);
    }

    .blueprint-link-text {
        flex: 1;
        min-width: 0;
        font-size: var(--ks-font-size-sm);
    }

    .blueprint-link-arrow {
        flex-shrink: 0;
        color: var(--ks-icon-muted);
    }
</style>
