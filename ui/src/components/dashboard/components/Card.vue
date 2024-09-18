<template>
    <div class="p-4 card">
        <div class="d-flex pb-2 justify-content-between">
            <div class="d-flex align-items-center">
                <component :is="icon" class="me-2 fs-4 icons" />

                <p class="m-0 fs-6 label">
                    {{ label }}
                </p>
            </div>

            <RouterLink :to="redirect" class="d-flex align-items-center">
                <TextSearchVariant class="fs-4 icons" />
            </RouterLink>
        </div>
        <p class="m-0 fs-2 fw-bold">
            {{ value }}
        </p>
    </div>
</template>

<script setup>
    import TextSearchVariant from "vue-material-design-icons/TextSearchVariant.vue";

    defineProps({
        icon: {
            type: Object,
            required: true,
        },
        label: {
            type: String,
            required: true,
        },
        value: {
            type: [String, Number],
            required: true,
        },
        redirect: {
            type: Object,
            required: true,
        },
    });
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

.card {
    & .icons {
        color: $secondary;
    }

    & .label {
        color: $gray-700;

        html.dark & {
            color: $gray-300;
        }
    }
}
</style>
