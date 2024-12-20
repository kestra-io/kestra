<template>
    <div class="wrapper">
        <template v-for="(cssVariables, theme) in cssVariablesThemes" :key="theme">
            <h2>
                {{ theme }}
            </h2>
            <div
                class="var-theme"
            >
                <ShowCaseColorsSample
                    v-for="(color, index) in cssVariables"
                    :key="index"
                    :color="color"
                />
            </div>
        </template>
    </div>
</template>

<script lang="ts" setup>
    import cssVariablesThemes from "./css-variables.json";
    import ShowCaseColorsSample from "./ShowCaseColorsSample.vue";
</script>

<style scoped>
    .wrapper {
        padding: 1rem;
        background-color: var(--ks-background-body);
    }
    .var-theme {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(350px, 1fr));
        gap: 1rem;
        margin-bottom: 3rem;
    }
</style>
