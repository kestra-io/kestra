import {computed, h, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import type {RouteLocationNormalizedLoaded} from "vue-router"
import {useI18n} from "vue-i18n"
import {KsMessageBox} from "@kestra-io/design-system"
import {useClient} from "@kestra-io/kestra-sdk"
import type {AxiosLikeConfig} from "@kestra-io/kestra-sdk"
import * as FlowsAPI from "@kestra-io/kestra-sdk/flows"
import {handled, handledIf} from "../../../utils/kestraHttp"
import {apiUrl} from "override/utils/route"
import {useAppDraftActions} from "override/components/ai/copilot/appDraftActions"
import {useMiscStore} from "override/stores/misc"
import {useTestSuiteDraftActions} from "override/components/ai/copilot/testSuiteDraftActions"
import {useFlowStore} from "../../../stores/flow"
import {routeFamily} from "../../../utils/routeFamily"
import {COPILOT_DRAFT_KEY} from "../../../utils/storageKeys"
import CopilotDraftReview from "./CopilotDraftReview.vue"
import {alertError, confirmApply, isAlreadyExists, parseArtefactYaml} from "./draftApply"
import type {ArtefactDraftEvent} from "./types"

export {parseArtefactYaml}

export function isViewingFlow(route: RouteLocationNormalizedLoaded, namespace: string, id: string): boolean {
    return routeFamily(route.name) === "flows/update"
        && String(route.params.namespace) === namespace
        && String(route.params.id) === id
}

/**
 * Open or apply an artefact drafted by the Copilot.
 * App and test suite drafts are handled by the EE `override/` actions.
 */
export function useApplyDraft() {
    const route = useRoute()
    const router = useRouter()
    const {t} = useI18n()

    const applying = ref(false)
    const appActions = useAppDraftActions()
    const testSuiteActions = useTestSuiteDraftActions()
    /** Read lazily, so rendering a draft card does not need Pinia. */
    const dashboardSupported = computed(() => useMiscStore().configs?.isCustomDashboardsEnabled !== false)

    const tenantParam = (): Record<string, string | string[]> => (
        route.params.tenant ? {tenant: route.params.tenant} : {}
    )

    function openInEditor(draft: ArtefactDraftEvent): void {
        if (draft.kind === "APP") return appActions.openInEditor(draft)
        if (draft.kind === "TEST_SUITE") return testSuiteActions.openInEditor(draft)
        if (draft.kind === "DASHBOARD") {
            router.push({name: "dashboards/create", query: {sourceYaml: draft.yaml}, params: tenantParam()})
            return
        }
        /** Through sessionStorage, not the URL: an unencoded `|` makes a refresh of that URL answer 500. */
        sessionStorage.setItem(COPILOT_DRAFT_KEY, draft.yaml)
        router.push({name: "flows/create", query: {copilotDraft: String(Date.now())}, params: tenantParam()})
    }

    /** Resolves `true` once the artefact is written, `false` when the user cancels or the write fails. */
    async function apply(draft: ArtefactDraftEvent): Promise<boolean> {
        if (draft.kind === "DASHBOARD") return applyDashboard(draft)
        if (draft.kind !== "TEST_SUITE") return applyFlow(draft)
        applying.value = true
        try {
            return await testSuiteActions.apply(draft)
        } finally {
            applying.value = false
        }
    }

    /** What a flow draft is compared with: the open editor, the saved flow, or "" for a new flow. */
    async function draftBaseSource(draft: ArtefactDraftEvent): Promise<string> {
        const {namespace, id} = parseArtefactYaml(draft.yaml)
        return namespace && id ? currentFlowSource(namespace, id) : ""
    }

    async function currentFlowSource(namespace: string, id: string): Promise<string> {
        const flowStore = useFlowStore()
        if (isViewingFlow(route, namespace, id)) return flowStore.flowYaml || ""
        try {
            const data = await flowStore.loadFlow({namespace, id, store: false})
            return data?.source ?? ""
        } catch (e: unknown) {
            if (handledIf(e, [404])) return ""
            throw e
        }
    }

    async function applyFlow(draft: ArtefactDraftEvent): Promise<boolean> {
        const {namespace, id} = parseArtefactYaml(draft.yaml)
        if (!namespace || !id) {
            await KsMessageBox.alert(
                t("ai.copilot.draft.applyNoTarget"),
                t("ai.copilot.draft.applyTitle"),
                {type: "error"},
            )
            return false
        }

        applying.value = true
        try {
            const yaml = await reviewFlowDraft(namespace, id, draft.yaml)
            if (yaml === undefined) return false

            /** A draft revision: triggers and subflows ignore it, so nothing goes live unreviewed. */
            try {
                await FlowsAPI.createFlow(
                    {body: yaml, draft: true} as Parameters<typeof FlowsAPI.createFlow>[0],
                )
            } catch (e) {
                if (!isAlreadyExists(e)) throw e
                handled(e)
                await FlowsAPI.updateFlow(
                    {namespace, id, body: yaml, draft: true} as Parameters<typeof FlowsAPI.updateFlow>[0],
                )
            }

            if (isViewingFlow(route, namespace, id)) {
                const flowStore = useFlowStore()
                const data = await flowStore.loadFlow({namespace, id})
                if (data?.source) await flowStore.loadGraph({flow: data})
            } else {
                router.push({name: "flows/update", params: {namespace, id, ...tenantParam()}})
            }
            return true
        } catch (e) {
            handled(e)
            await alertError(e, t("ai.copilot.draft.applyError"), t("ai.copilot.draft.applyTitle"))
            return false
        } finally {
            applying.value = false
        }
    }

    /** Resolves the YAML to apply, without the changes rejected in the review, or `undefined` on cancel. */
    async function reviewFlowDraft(namespace: string, id: string, yaml: string): Promise<string | undefined> {
        const before = await currentFlowSource(namespace, id)
        let effective = yaml
        return KsMessageBox({
            title: t("flow_change_review.copilot_heading"),
            message: () => h(CopilotDraftReview, {
                namespace,
                flowId: id,
                before: before || undefined,
                after: yaml,
                "onUpdate:effective": (source: string | undefined) => (effective = source ?? yaml),
            }),
            showCancelButton: true,
            confirmButtonText: t("ai.copilot.draft.apply"),
            cancelButtonText: t("cancel"),
            customStyle: {width: "min(1280px, 92vw)", maxWidth: "min(1280px, 92vw)"},
        }).then(() => effective).catch(() => undefined)
    }

    async function applyDashboard(draft: ArtefactDraftEvent): Promise<boolean> {
        const {id} = parseArtefactYaml(draft.yaml)
        const title = t("ai.copilot.draft.applyTitleDashboard")
        if (!id) {
            await KsMessageBox.alert(t("ai.copilot.draft.applyNoTarget"), title, {type: "error"})
            return false
        }
        if (!await confirmApply(t, t("ai.copilot.draft.applyConfirmDashboard", {id}), title)) return false

        applying.value = true
        try {
            /** Raw client: dashboard writes are EE-only routes, absent from the OSS SDK. */
            const yaml = {headers: {"Content-Type": "application/x-yaml"}} as AxiosLikeConfig
            try {
                await useClient().post(`${apiUrl()}/dashboards`, draft.yaml, yaml)
            } catch (e) {
                if (!isAlreadyExists(e)) throw e
                handled(e)
                await useClient().put(`${apiUrl()}/dashboards/${id}`, draft.yaml, yaml)
            }
            router.push({name: "dashboards/update", params: {dashboard: id, ...tenantParam()}})
            return true
        } catch (e) {
            handled(e)
            await alertError(e, t("ai.copilot.draft.applyErrorDashboard"), title)
            return false
        } finally {
            applying.value = false
        }
    }

    return {
        applying,
        appSupported: appActions.supported,
        dashboardSupported,
        testSuiteSupported: testSuiteActions.supported,
        openInEditor,
        apply,
        draftBaseSource,
    }
}
