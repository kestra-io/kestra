<template>
    <div class="html-preview">
        <div v-if="loading">
            Loading preview…
        </div>
        <div v-else-if="error" class="text-red-600">
            {{ error }}
        </div>
        <iframe
            v-else
            :srcdoc="html!"
            sandbox="allow-scripts"
            referrerpolicy="no-referrer"
            style="width:100%;height:100%;border:0;"
        />
    </div>
</template>

<script setup lang="ts">
    import {onMounted, ref} from "vue";

    const props = defineProps<{ downloadUrl: string }>();

    const html = ref<string | null>(null);
    const error = ref<string | null>(null);
    const loading = ref(true);

    onMounted(async () => {
        try {
            const res = await fetch(props.downloadUrl, {
                credentials: "include",
                headers: {Accept: "text/plain,*/*;q=0.1"},
            });
            if (!res.ok) throw new Error(`HTTP ${res.status}`);
            html.value = await res.text();
        } catch {
            error.value = "Failed to load HTML preview.";
        } finally {
            loading.value = false;
        }
    });
</script>

<style scoped lang="scss">
.html-preview {
    width: 100%;
    height: 100%;
}
</style>
