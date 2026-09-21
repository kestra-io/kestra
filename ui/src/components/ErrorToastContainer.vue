<template>
    <KsButton
        v-if="isFlowContext"
        @click="fixWithAi"
        size="small"
    >
        <AiIcon class="me-1" />
        <span>{{ $t("fix_with_ai") }}</span>
    </KsButton>

    <KsMarkdown :content="detail" v-if="items.length === 0" />

    <ul v-else class="problem-errors">
        <li v-for="(item, index) in items" :key="index" class="font-monospace">
            <template v-if="fieldLabel(item)">
                <!-- The JSON Pointer is kept as the tooltip: it is the machine locator, and the one a
                     jump-to-line feature would resolve against the submitted document. -->
                <code :title="item.pointer">{{ fieldLabel(item) }}</code>:
            </template>
            <span>{{ fieldMessage(item) }}</span>
        </li>
    </ul>

    <footer v-if="traceId" class="problem-trace">
        <span>{{ $t("errors.trace id") }}</span>
        <KsId :value="traceId" :shrink="false" />
    </footer>
</template>

<script setup lang="ts">
    import {computed} from "vue"
    import {useRoute} from "vue-router"
    import {useI18n} from "vue-i18n"
    import AiIcon from "vue-material-design-icons/Creation.vue"
    import {useMiscStore} from "override/stores/misc"
    import type {ProblemFieldError} from "@kestra-io/kestra-sdk"
    import {problemFieldLabel, problemFieldMessage} from "../utils/problem"

    interface Props {
        /** Localized body text. */
        detail: string
        /** Field-level errors, when the problem reports several at once. */
        items: readonly ProblemFieldError[]
        /** Correlation id, present on server errors only. */
        traceId?: string
        onClose?: (() => void) | null
    }

    const props = withDefaults(defineProps<Props>(), {
        traceId: undefined,
        onClose: null,
    })

    const route = useRoute()
    const {t, te} = useI18n()
    const miscStore = useMiscStore()

    const isFlowContext = computed(() => {
        const routeName = String(route?.name ?? "")
        return routeName.startsWith("flows/update") || routeName === "flows/create"
    })

    const fieldLabel = (item: ProblemFieldError) => problemFieldLabel(item)
    const fieldMessage = (item: ProblemFieldError) => problemFieldMessage(item, t, te)

    const fixWithAi = async () => {
        const errorItems = props.items
            .map((item) => {
                const label = fieldLabel(item)
                return (label ? `${label}: ` : "") + fieldMessage(item)
            })
            .join("\n")

        const fullErrorMessage = [props.detail, errorItems].filter(Boolean).join("\n\n")
        const prompt = `Fix the following error in the flow:\n${fullErrorMessage}`

        props.onClose?.()

        const flowId = route.params?.id
        const title = flowId ? t("ai.copilot.fixThread.flow", {id: flowId}) : t("ai.copilot.fixThread.generic")
        miscStore.promptCopilot(prompt, {title, newThread: true})
    }
</script>

<style scoped lang="scss">
    .problem-errors {
        margin: var(--ks-spacing-4) 0 0;
        padding: 0;
        list-style-type: none;
    }

    .problem-errors li {
        font-size: var(--ks-font-size-sm);
        margin-top: var(--ks-spacing-2);
    }

    .problem-trace {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-2);
        margin-top: var(--ks-spacing-4);
        font-size: var(--ks-font-size-sm);
        color: var(--ks-content-secondary);
    }
</style>
