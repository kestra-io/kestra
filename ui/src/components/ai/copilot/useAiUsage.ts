/**
 * `useAiUsage` — where a provider stands against its spend ceiling (`…/ai/usage`).
 *
 * The figure and both flags come from the server, which is also what enforces the ceiling mid-turn; computing
 * a percentage here as well would eventually disagree with the one being enforced.
 *
 * Fetched through `useClient()` rather than the generated SDK, as the rest of the Copilot client is.
 */
import {computed, ref, watch, type Ref} from "vue"
import {useClient} from "@kestra-io/kestra-sdk"
import {apiUrl} from "override/utils/route"

/** One ceiling and what has been spent against it. `maxWeight` is 0 when this axis has none. */
export interface AiUsageAxis {
    weight: number
    maxWeight: number
    remainingPercent: number
    exceeded: boolean
}

export interface AiUsageStatus {
    providerId?: string
    /** False for every provider until an operator configures a ceiling; the rest is then absent. */
    enabled: boolean
    windowStart?: string
    /**
     * When the current period ends and the ceiling starts again, absent while nothing is exhausted. A UTC
     * instant — render it through `dateUtils.dateFilter` so a user reads it in their own zone.
     */
    availableAt?: string | null
    /** Spend across every caller and every tenant of the installation, which is what the key is billed for. */
    global?: AiUsageAxis | null
    /** Spend by the calling user, likewise across every tenant they can reach. Absent where there is no user identity. */
    user?: AiUsageAxis | null
    /** The tightest axis — the only number worth showing as one figure. */
    remainingPercent: number
    warning: boolean
    exceeded: boolean
    warningThresholdPercent: number
}

export function useAiUsage(provider: Ref<string | undefined>) {
    const client = useClient()

    const status = ref<AiUsageStatus | undefined>()

    /** Reported only where an operator asked to be held to something. */
    const shown = computed(() => status.value?.enabled === true)
    const remainingPercent = computed(() => status.value?.remainingPercent ?? 100)
    const warning = computed(() => shown.value && status.value?.warning === true)
    const exceeded = computed(() => shown.value && status.value?.exceeded === true)

    /**
     * Never rejects, and never clears the last known figure on failure — this decorates a composer, and the
     * turn is still sendable whether or not the read succeeded. Hence `showMessageOnError: false` too.
     */
    async function refresh(): Promise<void> {
        try {
            const {data} = await client.get<AiUsageStatus>(`${apiUrl()}/ai/usage`, {
                params: provider.value ? {providerId: provider.value} : undefined,
                showMessageOnError: false,
            })
            status.value = data
        } catch {
            // Left as it was: the ceiling is enforced server-side regardless of whether we could read it.
        }
    }

    // Each provider carries its own ceiling, so switching them changes the figure entirely.
    watch(provider, () => refresh(), {immediate: true})

    return {status, shown, remainingPercent, warning, exceeded, refresh}
}
