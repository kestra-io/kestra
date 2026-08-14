/**
 * `useAiUsage` — where a provider stands against its spend ceiling (`…/ai/usage`).
 *
 * Read so a user learns of an exhausted allowance before spending a turn on it rather than from the
 * refusal. The figure and both flags come from the server: the same type answers this endpoint and
 * enforces the ceiling mid-turn, and a percentage computed on both sides is one that eventually
 * disagrees with the one being enforced.
 *
 * Fetched through the `useClient()` facade rather than the generated SDK, as the rest of the Copilot
 * client is — the AI endpoints differ per edition and the generated surface lags them.
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
    global?: AiUsageAxis | null
    /** Absent in OSS, which has no user identity in the agent path. */
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
     * Never rejects, and never clears what was last known on failure: this decorates a composer, and an
     * endpoint that is briefly unreachable must not empty the figure a user is reading, nor raise an
     * error over a turn they can still send.
     */
    async function refresh(): Promise<void> {
        try {
            // `showMessageOnError` off: a failed read of a decoration is not worth a toast over a turn the
            // user can still send.
            const {data} = await client.get<AiUsageStatus>(`${apiUrl()}/ai/usage`, {
                params: provider.value ? {providerId: provider.value} : undefined,
                showMessageOnError: false,
            })
            status.value = data
        } catch {
            // Left as it was: the ceiling is enforced server-side regardless of whether we could read it.
        }
    }

    // A provider carries its own ceiling, so switching them changes the figure entirely.
    watch(provider, () => refresh(), {immediate: true})

    return {status, shown, remainingPercent, warning, exceeded, refresh}
}
