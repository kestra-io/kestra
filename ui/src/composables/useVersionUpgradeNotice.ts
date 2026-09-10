import {computed, ref} from "vue"
import {useMiscStore} from "override/stores/misc"

const STORAGE_KEY = "kestra.versionUpgradeNotice"

export interface VersionUpgrade {
    from: string
    to: string
    at: string
}

interface PersistedState {
    instanceUuid?: string
    dismissedVersion?: string
}

function read(): PersistedState {
    try {
        const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? "null")
        if (!parsed || typeof parsed !== "object") {
            return {}
        }
        return {instanceUuid: parsed.instanceUuid, dismissedVersion: parsed.dismissedVersion}
    } catch {
        return {}
    }
}

export function useVersionUpgradeNotice() {
    const miscStore = useMiscStore()

    const instanceUuid = computed<string | undefined>(() => miscStore.configs?.uuid)
    const notice = computed<VersionUpgrade | undefined>(() => miscStore.configs?.versionUpgrade)

    const stored = ref<PersistedState>(read())

    // Resolved against the live uuid rather than at load time, because configs arrive asynchronously:
    // state persisted by another instance says nothing about this one.
    const dismissedVersion = computed<string | undefined>(() => {
        const {instanceUuid: storedUuid, dismissedVersion: version} = stored.value
        if (storedUuid && instanceUuid.value && storedUuid !== instanceUuid.value) {
            return undefined
        }
        return version
    })

    const visible = computed(() => Boolean(notice.value) && dismissedVersion.value !== notice.value?.to)

    function dismiss(): void {
        if (!notice.value) {
            return
        }

        stored.value = {
            instanceUuid: instanceUuid.value ?? stored.value.instanceUuid,
            dismissedVersion: notice.value.to,
        }

        try {
            localStorage.setItem(STORAGE_KEY, JSON.stringify(stored.value))
        } catch {
            // A browser refusing to persist is not a reason to keep the banner up for this session.
        }
    }

    return {notice, visible, dismiss}
}
