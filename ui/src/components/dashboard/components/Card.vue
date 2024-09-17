<template>
    <div class="p-4 card">
        <div class="d-flex pb-2 justify-content-between">
            <div class="d-flex">
                <component :is="icon" class="me-2 fs-4 icons" />

                <p class="m-0 fs-6 label">
                    {{ label }}
                </p>
            </div>

            <TextSearchVariant class="fs-4 icons" />
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
            type: String,
            required: true,
        },
    });
</script>

<style lang="scss" scoped>
$color: #c182ff;

.card {
    & .icons {
        color: $color;
    }

    & .label {
        color: var(--el-text-color-regular);
    }
}
</style>
