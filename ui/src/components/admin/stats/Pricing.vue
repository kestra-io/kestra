<template>
    <div class="pricing">
        <div class="header">{{ $t("license") }}</div>
        <div class="bar">
            <KsText class="edition">{{ $t("open source") }}</KsText>
            <div class="actions">
                <KsButton
                    tag="a"
                    target="_blank"
                    rel="noopener noreferrer"
                    :href="PRICING_URL"
                    :icon="OpenInNew"
                >
                    {{ $t("pricing") }}
                </KsButton>
                <KsButton
                    type="primary"
                    tag="a"
                    target="_blank"
                    rel="noopener noreferrer"
                    :href="DEMO_URL"
                    :icon="FaceAgent"
                >
                    {{ $t("demos.get_a_demo_button") }}
                </KsButton>
            </div>
        </div>
    </div>
</template>

<script setup lang="ts">
    import FaceAgent from "vue-material-design-icons/FaceAgent.vue"
    import OpenInNew from "vue-material-design-icons/OpenInNew.vue"

    const PRICING_URL = "https://kestra.io/pricing"
    const DEMO_URL = "https://kestra.io/demo"
</script>

<style scoped lang="scss">
    .pricing {
        .header {
            margin-bottom: var(--ks-spacing-4);
            font-size: var(--ks-font-size-md);
            font-weight: var(--ks-font-weight-bold);
            color: var(--ks-text-primary);
        }

        .bar {
            display: flex;
            align-items: center;
            justify-content: space-between;
            gap: var(--ks-spacing-4);
            padding: var(--ks-spacing-4);
            border: 1px solid var(--ks-border-default);
            border-radius: 8px;
            background: var(--ks-bg-surface);
            box-shadow: 0px 1px 4px 0px var(--ks-shadow-element);

            :deep(svg) {
                color: var(--ks-icon-muted);
            }
        }

        .edition {
            color: var(--ks-text-primary);
        }

        .actions {
            display: flex;
            align-items: center;
        }
    }
</style>
