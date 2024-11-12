<template>
    <empty-state
        :title="$t('dependency-view.title_no_deps')"
        :description="$t('dependency-view.desc_no_deps')"
        :image="flowImage"
    />
</template>

<script>
    import EmptyState from "../layout/EmptyState.vue";
    import flowImageDark from "../../assets/onboarding/onboarding-flow-no-dependency-dark.svg"
    import flowImageLight from "../../assets/onboarding/onboarding-flow-no-dependency-light.svg"
    export default {
        name: "NoDependencies",
        components: {
            EmptyState
        },
        computed: {
            flowImage() {
                return (localStorage.getItem("theme") || "light") === "light" ? flowImageLight : flowImageDark;
            },
        }
    };
</script>
