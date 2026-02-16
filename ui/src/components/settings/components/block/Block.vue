<template>
    <section>
        <h1 class="heading">
            <div class="content">
                <el-popover
                    v-if="note"
                    :content="note"
                    trigger="hover"
                    :width="400"
                    class="info"
                >
                    <template #reference>
                        <InformationOutline />
                    </template>
                </el-popover>
                <span>{{ heading }}</span>
            </div>
            <div class="actions">
                <slot name="actions" />
            </div>
        </h1>
        <slot name="content" />
        <el-divider v-if="!last" />
    </section>
</template>

<script setup lang="ts">
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue";

    defineProps({
        heading: {type: String, default: ""},
        note: {type: String, default: undefined},
        last: {type: Boolean, default: false},
    });
</script>

<style scoped lang="scss">
@import "@kestra-io/ui-libs/src/scss/variables";

section {
    margin: calc($spacer * 2);

    & > h1.heading {
        display: flex;
        align-items: center;
        margin-bottom: calc($spacer * 2);
        justify-content: space-between;
        font-size: $font-size-xl;
        font-weight: 600;

        & .content {
            display: flex;
            align-items: center;

            & > span.el-tooltip__trigger {
                cursor: pointer;
                margin-right: calc($spacer / 2);
            }
        }

        & .actions {
            display: flex;
            align-items: center;
            gap: calc($spacer / 2);
        }
    }
}
</style>
