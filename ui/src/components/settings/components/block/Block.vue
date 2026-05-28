<template>
    <section>
        <h1 class="heading">
            <div class="content">
                <KsPopover
                    v-if="note"
                    :content="note"
                    trigger="hover"
                    :width="400"
                    class="info"
                >
                    <template #reference>
                        <InformationOutline />
                    </template>
                </KsPopover>
                <span>{{ heading }}</span>
            </div>
            <div class="actions">
                <slot name="actions" />
            </div>
        </h1>
        <slot name="content" />
        <KsDivider v-if="!last" />
    </section>
</template>

<script setup lang="ts">
    import InformationOutline from "vue-material-design-icons/InformationOutline.vue"

    defineProps({
        heading: {type: String, default: ""},
        note: {type: String, default: undefined},
        last: {type: Boolean, default: false},
    })
</script>

<style scoped lang="scss">

section {
    margin: calc(1rem * 2);

    & > h1.heading {
        display: flex;
        align-items: center;
        margin-bottom: calc(1rem * 2);
        justify-content: space-between;
        font-size: var(--ks-font-size-xl);
        font-weight: 600;

        & .content {
            display: flex;
            align-items: center;

            & > span.kel-tooltip__trigger {
                cursor: pointer;
                margin-right: calc(1rem / 2);
            }
        }

        & .actions {
            display: flex;
            align-items: center;
            gap: calc(1rem / 2);
        }
    }
}
</style>
