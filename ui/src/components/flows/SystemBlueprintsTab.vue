<template>
    <div class="system-blueprints-tab">
        <div class="recipe-section">
            <h2 class="section-heading">{{ $t("recipe.section_title") }}</h2>
            <p class="section-sub">{{ $t("recipe.section_subtitle") }}</p>
            <FlowRecipe :namespace="systemNamespace" @submit="handleRecipeSubmit" />
        </div>

        <div class="blueprints-section">
            <h2 class="section-heading">{{ $t("recipe.blueprints_section_title") }}</h2>
            <BlueprintsBrowser tab="community" :system="true" :embed="true" />
        </div>
    </div>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRouter, useRoute} from "vue-router"
    import {useMiscStore} from "override/stores/misc"
    import FlowRecipe from "./recipe/FlowRecipe.vue"
    import BlueprintsBrowser from "./blueprints/BlueprintsBrowser.vue"
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
        gap: var(--ks-spacing-6);
    }

    .recipe-section {
        border-bottom: 1px solid var(--ks-border-default);
        padding-bottom: var(--ks-spacing-6);
    }

    .section-heading {
        margin: 0 var(--ks-spacing-4) var(--ks-spacing-1);
        font-size: var(--ks-font-size-xl);
        font-weight: var(--ks-font-weight-semibold);
    }

    .section-sub {
        margin: 0 var(--ks-spacing-4) var(--ks-spacing-4);
        color: var(--ks-text-secondary);
        font-size: var(--ks-font-size-sm);
    }

    .blueprints-section {
        padding-bottom: var(--ks-spacing-4);
    }
</style>
