import {computed, ref} from "vue"
import {useMiscStore} from "override/stores/misc"

const STORAGE_KEY = "kestra.versionUpgradeNotice"

export interface VersionUpgrade {
    from: string
    to: string
    at: string
}

interface PersistedState {
    // Keyed by instance uuid: one browser is often pointed at several Kestra instances, and a
    // dismissal on one of them says nothing about the others.
    dismissedVersions: Record<string, string>
}

function read(): PersistedState {
    try {
        const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null")
        if (!parsed || typeof parsed !== "object") {
            return {dismissedVersions: {}}
        }

        if (parsed.dismissedVersions && typeof parsed.dismissedVersions === "object") {
            return {dismissedVersions: {...parsed.dismissedVersions}}
        }

        // The single-slot shape written before the store became per-instance.
        if (typeof parsed.instanceUuid === "string" && typeof parsed.dismissedVersion === "string") {
            return {dismissedVersions: {[parsed.instanceUuid]: parsed.dismissedVersion}}
        }

        return {dismissedVersions: {}}
    } catch {
        return {dismissedVersions: {}}
    }
}

export function useVersionUpgradeNotice() {
    const miscStore = useMiscStore()

    const instanceUuid = computed<string | undefined>(() => miscStore.configs?.uuid)
    const notice = computed<VersionUpgrade | undefined>(() => {
        const upgrade = miscStore.configs?.versionUpgrade
        return upgrade?.from && upgrade.to && upgrade.at
            ? {from: upgrade.from, to: upgrade.to, at: upgrade.at}
            : undefined
    })

    const stored = ref<PersistedState>(read())
    // A browser that refuses to persist, or an instance that reports no uuid, still gets the banner
    // out of the way for the rest of the session.
    const dismissedInSession = ref(false)

    // Resolved against the live uuid rather than at load time, because configs arrive asynchronously:
    // state persisted for another instance says nothing about this one.
    const dismissedVersion = computed<string | undefined>(() =>
        instanceUuid.value ? stored.value.dismissedVersions[instanceUuid.value] : undefined,
    )

    const visible = computed(() =>
        Boolean(notice.value) && !dismissedInSession.value && dismissedVersion.value !== notice.value?.to,
    )

    function dismiss(): void {
        if (!notice.value) {
            return
        }

        dismissedInSession.value = true

        const uuid = instanceUuid.value
        if (!uuid) {
            return
        }

        stored.value = {
            dismissedVersions: {...stored.value.dismissedVersions, [uuid]: notice.value.to},
        }

        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(stored.value))
        } catch {
            // A browser refusing to persist is not a reason to keep the banner up for this session.
        }
    }

    return {notice, visible, dismiss}
}
