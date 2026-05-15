<template>
    <div class="plugin-install-toast">
        <template v-if="job">
            <div
                v-if="job.status === 'SUCCEEDED'"
                class="status-line status-line--success"
            >
                <KsIcon name="check-circle" />
                <span>{{ t("plugins.autoInstall.succeeded", artifactCount) }}</span>
            </div>
            <div
                v-else-if="job.status === 'FAILED'"
                class="status-line status-line--error"
            >
                <KsIcon name="alert-circle" />
                <span>{{ job.error ?? t("plugins.autoInstall.failed") }}</span>
            </div>
            <template v-else>
                <div
                    v-for="artifact in job.artifacts"
                    :key="artifact.artifactId"
                    class="artifact-row"
                >
                    <span class="artifact-label">{{ artifact.artifactId }}</span>
                    <KsProgress
                        :percentage="artifactPercentage(artifact)"
                        :stroke-width="6"
                        class="artifact-progress"
                    />
                    <span class="artifact-bytes">{{ artifactBytesLabel(artifact) }}</span>
                </div>
            </template>
        </template>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted, onUnmounted} from "vue"
    import {useI18n} from "vue-i18n"
    import {usePluginsStore, type PluginArtifact, type PluginInstallJob} from "../../stores/plugins"

    const props = defineProps<{
        jobId: string;
        onSuccess?: () => void;
    }>()

    const {t} = useI18n()
    const pluginsStore = usePluginsStore()

    const job = ref<PluginInstallJob | null>(null)
    let pollTimer: ReturnType<typeof setInterval> | null = null

    const artifactCount = computed(() => job.value?.artifacts.length ?? 0)

    function artifactPercentage(artifact: PluginArtifact): number {
        const key = Object.keys(job.value?.progress ?? {}).find(k => k.includes(artifact.artifactId))
        if (!key) return 0
        const p = job.value!.progress[key]
        if (!p || p.total <= 0) return 0
        return Math.round((p.transferred / p.total) * 100)
    }

    function artifactBytesLabel(artifact: PluginArtifact): string {
        const key = Object.keys(job.value?.progress ?? {}).find(k => k.includes(artifact.artifactId))
        if (!key) return ""
        const p = job.value!.progress[key]
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
            job.value = latest
        }
        if (latest?.status === "SUCCEEDED") {
            stopPolling()
            props.onSuccess?.()
        } else if (latest?.status === "FAILED" || latest === null) {
            stopPolling()
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
        if (job.value && !isTerminal(job.value)) {
            pollTimer = setInterval(poll, 500)
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
        gap: 0.5rem;
        min-width: 260px;
    }

    .artifact-row {
        display: flex;
        flex-direction: column;
        gap: 0.2rem;
    }

    .artifact-label {
        font-size: 0.8rem;
        color: var(--ks-content-secondary);
        overflow: hidden;
        text-overflow: ellipsis;
        white-space: nowrap;
    }

    .artifact-bytes {
        font-size: 0.75rem;
        color: var(--ks-content-secondary);
        text-align: right;
    }

    .artifact-progress {
        width: 100%;
    }

    .status-line {
        display: flex;
        align-items: center;
        gap: 0.4rem;

        &--success {
            color: var(--ks-content-success);
        }

        &--error {
            color: var(--ks-content-error);
        }
    }
</style>
