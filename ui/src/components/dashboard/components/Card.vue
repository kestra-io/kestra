<template>
    <div class="p-4 card">
        <slot>
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
        </slot>
    </div>
</template>

<script setup lang="ts">
    import type {RouteLocationRaw} from "vue-router";
    import TextSearchVariant from "vue-material-design-icons/TextSearchVariant.vue";

    defineProps<{
        icon: any;
        label: string;
        tooltip?: string;
        value: string | number;
        redirect: RouteLocationRaw;
    }>();
</script>

<style lang="scss" scoped>
@import "@kestra-io/ui-libs/src/scss/variables";

.card {
    box-shadow: 0px 2px 4px 0px var(--ks-card-shadow);
    & .icons {
        color: $secondary;

        &.url {
            color: #7e719f;
        }
    }

    background: var(--ks-background-card);
    color: var(--ks-content-primary);
    border: 1px solid var(--ks-border-primary);
    border-radius: $border-radius;
}
</style>

<style lang="scss">
.dashboard-card-tooltip {
    width: 300px;
}
</style>
