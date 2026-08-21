<template>
    <div class="plugin-install-toast">
        <template v-if="job">
            <div
                v-if="job.status === 'SUCCEEDED'"
                class="status-line status-line-success"
            >
                <KsIcon><CheckCircle /></KsIcon>
                <span>{{ $t("plugins.autoInstall.succeeded", artifactCount) }}</span>
            </div>
            <template v-else-if="job.status === 'FAILED'">
                <div class="status-line status-line-error">
                    <KsIcon><AlertCircle /></KsIcon>
                    <span>{{ $t("plugins.autoInstall.failed") }}</span>
                </div>
                <KsText size="small">
                    {{ $t("plugins.autoInstall.failedHint") }}
                </KsText>
                <KsText v-if="job.error" size="small" class="error-detail">
                    {{ job.error }}
                </KsText>
            </template>
            <template v-else>
                <div
                    v-for="artifact in job.artifacts"
                    :key="artifact.artifactId"
                    class="artifact-row"
                >
                    <KsText size="small" truncated class="artifact-label">
                        {{ displayName(artifact) }}
                    </KsText>
                    <KsProgress
                        :percentage="artifactPercentage(job.progress ?? {}, artifact)"
                        :stroke-width="6"
                        class="artifact-progress"
                    />
                    <KsText size="small" class="artifact-bytes">
                        {{ artifactBytesLabel(artifact) }}
                    </KsText>
                </div>
            </template>
        </template>
        <template v-else>
            <KsText size="small">{{ $t("plugins.autoInstall.preparing") }}</KsText>
            <KsSkeleton animated :rows="1" />
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted} from "vue"
    import {useI18n} from "vue-i18n"
    import CheckCircle from "vue-material-design-icons/CheckCircle.vue"
    import AlertCircle from "vue-material-design-icons/AlertCircle.vue"
    import {usePluginsStore, type PluginArtifact, type PluginInstallJob} from "../../stores/plugins"
    import {artifactPercentage, humanBytes, progressFor} from "./pluginInstallProgress"

    const props = defineProps<{
        jobId: string;
        onSuccess?: () => void;
        onFailure?: () => void;
    }>()

    const POLL_INTERVAL_MS = 500
    // A single failed poll can be transient (network blip, server hiccup) — only treat the job as
    // lost after several consecutive failures.
    const MAX_CONSECUTIVE_POLL_FAILURES = 10

    const {t} = useI18n()
    const pluginsStore = usePluginsStore()

    const job = ref<PluginInstallJob | null>(null)
    let pollTimer: ReturnType<typeof setInterval> | null = null
    let consecutivePollFailures = 0

    const artifactCount = computed(() => job.value?.artifacts.length ?? 0)

    function displayName(artifact: PluginArtifact): string {
        // The plugin list's name IS the Maven artifactId, so its human title can stand in for it.
        return pluginsStore.findPluginByName(artifact.artifactId)?.title ?? artifact.artifactId
    }

    function artifactBytesLabel(artifact: PluginArtifact): string {
        const p = progressFor(job.value?.progress ?? {}, artifact)
        if (!p) return ""
        return t("plugins.autoInstall.progress", {
            transferred: humanBytes(p.transferred),
            total: p.total > 0 ? humanBytes(p.total) : "?",
        })
    }

    async function poll() {
        const latest = await pluginsStore.getInstallJob(props.jobId)
        if (latest) {
            consecutivePollFailures = 0
            job.value = latest
            if (latest.status === "SUCCEEDED") {
                stopPolling()
                props.onSuccess?.()
            } else if (latest.status === "FAILED") {
                stopPolling()
                props.onFailure?.()
            }
            return
        }

        consecutivePollFailures++
        if (consecutivePollFailures >= MAX_CONSECUTIVE_POLL_FAILURES) {
            stopPolling()
            props.onFailure?.()
        }
    }

    function stopPolling() {
        if (pollTimer !== null) {
            clearInterval(pollTimer)
            pollTimer = null
        }
    }

    function isTerminal(j: PluginInstallJob | null): boolean {
        return j?.status === "SUCCEEDED" || j?.status === "FAILED"
    }

    onMounted(async () => {
        if (!pluginsStore.plugins) {
            pluginsStore.list()
        }
        await poll()
        if (!isTerminal(job.value) && pollTimer === null && consecutivePollFailures < MAX_CONSECUTIVE_POLL_FAILURES) {
            pollTimer = setInterval(poll, POLL_INTERVAL_MS)
        }
    })

    onUnmounted(() => {
        stopPolling()
    })
</script>

<style scoped lang="scss">
    .plugin-install-toast {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-2);
        min-width: 16rem;
    }

    .artifact-row {
        display: flex;
        flex-direction: column;
        gap: var(--ks-spacing-1);
    }

    .artifact-bytes {
        text-align: right;
    }

    .artifact-progress {
        width: 100%;
    }

    .status-line {
        display: flex;
        align-items: center;
        gap: var(--ks-spacing-1);
    }

    .status-line-success {
        color: var(--ks-text-success);
    }

    .status-line-error {
        color: var(--ks-text-error);
    }

    .error-detail {
        color: var(--ks-text-muted);
    }
</style>
