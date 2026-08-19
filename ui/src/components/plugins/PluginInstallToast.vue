<template>
    <div class="plugin-install-toast">
        <template v-if="job">
            <div
                v-if="job.status === 'SUCCEEDED'"
                class="status-line status-line-success"
            >
                <KsIcon name="check-circle" />
                <span>{{ $t("plugins.autoInstall.succeeded", artifactCount) }}</span>
            </div>
            <div
                v-else-if="job.status === 'FAILED'"
                class="status-line status-line-error"
            >
                <KsIcon name="alert-circle" />
                <span>{{ job.error ?? $t("plugins.autoInstall.failed") }}</span>
            </div>
            <template v-else>
                <div
                    v-for="artifact in job.artifacts"
                    :key="artifact.artifactId"
                    class="artifact-row"
                >
                    <KsText size="small" truncated class="artifact-label">
                        {{ artifact.artifactId }}
                    </KsText>
                    <KsProgress
                        :percentage="artifactPercentage(artifact)"
                        :stroke-width="6"
                        class="artifact-progress"
                    />
                    <KsText size="small" class="artifact-bytes">
                        {{ artifactBytesLabel(artifact) }}
                    </KsText>
                </div>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted} from "vue"
    import {useI18n} from "vue-i18n"
    import {usePluginsStore, type ArtifactProgress, type PluginArtifact, type PluginInstallJob} from "../../stores/plugins"

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

    function escapeRegExp(value: string): string {
        return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")
    }

    function progressFor(artifact: PluginArtifact): ArtifactProgress | undefined {
        const progress = job.value?.progress ?? {}
        // Match the artifact's own file segment ("…/plugin-aws-1.2.3.jar") — a plain includes()
        // would also match "plugin-aws" against a "plugin-aws-s3" resource.
        const pattern = new RegExp(`(^|/)${escapeRegExp(artifact.artifactId)}-\\d`)
        const key = Object.keys(progress).find(k => pattern.test(k))
        return key ? progress[key] : undefined
    }

    function artifactPercentage(artifact: PluginArtifact): number {
        const p = progressFor(artifact)
        if (!p || p.total <= 0) return 0
        return Math.round((p.transferred / p.total) * 100)
    }

    function artifactBytesLabel(artifact: PluginArtifact): string {
        const p = progressFor(artifact)
        if (!p) return ""
        return t("plugins.autoInstall.progress", {
            transferred: humanBytes(p.transferred),
            total: p.total > 0 ? humanBytes(p.total) : "?",
        })
    }

    function humanBytes(bytes: number): string {
        if (bytes < 1024) return `${bytes} B`
        if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
        return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
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
</style>
