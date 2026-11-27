<template>
    <KsAlert
        v-if="wording"
        class="flow-draft-banner"
        banner
        type="warning"
        :title="$t(`flow_draft_banner.${wording}.title`)"
        :description="$t(`flow_draft_banner.${wording}.description`)"
        data-test="flow-draft-banner"
    >
        <template v-if="canPublish" #action>
            <KsTooltip :content="$t('flow_publish.description')">
                <KsButton type="primary" size="small" :loading="publishing" data-test="flow-draft-banner-publish" @click="publish">
                    {{ $t("publish") }}
                </KsButton>
            </KsTooltip>
        </template>
    </KsAlert>
</template>

<script setup lang="ts">
    import {computed, ref} from "vue"
    import {useFlowStore} from "../../stores/flow"
    import {useAuthStore} from "override/stores/auth"
    import resource from "../../models/resource"
    import action from "../../models/action"
    import {useToast} from "../../utils/toast"
    import {useI18n} from "vue-i18n"

    const flowStore = useFlowStore()
    const authStore = useAuthStore()
    const toast = useToast()
    const {t} = useI18n()
    const publishing = ref(false)

    const wording = computed(() => {
        const flow = flowStore.flow
        if (!flow?.draft || flow.deleted || flowStore.hasPublishedRevision === undefined) return undefined
        return flowStore.hasPublishedRevision ? "over_published" : "draft_only"
    })

    const canPublish = computed(() =>
        Boolean(flowStore.flow && authStore.user?.isAllowed(resource.FLOW, action.UPDATE, flowStore.flow.namespace)),
    )

    async function publish() {
        if (!flowStore.flow) return
        publishing.value = true
        try {
            await flowStore.publishDraft(flowStore.flow)
        } catch (error) {
            toast.requestError(error, t("flow_publish.failed"))
        } finally {
            publishing.value = false
        }
    }
</script>

<style scoped>
/* the page lays out its tabs in a column, where a full-height tab would otherwise shrink the banner */
.flow-draft-banner {
    flex-shrink: 0;
}
</style>
