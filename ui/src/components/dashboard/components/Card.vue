<template>
    <div class="p-4 card">
        <div class="d-flex pb-2 justify-content-between">
            <div class="d-flex align-items-center">
                <el-tooltip
                    v-if="tooltip"
                    :content="tooltip"
                    popper-class="dashboard-card-tooltip"
                >
                    <component :is="icon" class="me-2 fs-4 icons" />
                </el-tooltip>
                <component v-else :is="icon" class="me-2 fs-4 icons" />

                <p class="m-0 fs-6 label">
                    {{ label }}
                </p>
            </div>

            <RouterLink :to="redirect" class="d-flex align-items-center">
                <TextSearchVariant class="fs-4 icons url" />
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
        tooltip: {
            type: String,
            default: undefined,
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

        &.url {
            color: #7e719f;
        }
    }

    & .label {
        color: $gray-700;

        html.dark & {
            color: $gray-300;
        }
    }
}
</style>

<style lang="scss">
.dashboard-card-tooltip {
    width: 300px;
}
</style>
