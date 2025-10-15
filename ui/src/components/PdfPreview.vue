<template>
    <div>
        <canvas id="pdf" />

        <nav v-if="rendered">
            <el-tooltip :content="$t('page.previous')" effect="light" :showAfter="1500">
                <el-button @click="onPrevPage">
                    <ChevronLeft />
                </el-button>
            </el-tooltip>
            <span>
                {{ pageNum }}
                {{ $t("of") }}
                {{ pdfDoc?.numPages }}
            </span>
            <el-tooltip :content="$t('page.next')" effect="light" :showAfter="1500">
                <el-button @click="onNextPage">
                    <ChevronRight />
                </el-button>
            </el-tooltip>
        </nav>
    </div>
</template>

<script setup lang="ts">
    import {ref, computed, onMounted} from "vue";
    import * as pdfjs from "pdfjs-dist";
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";

    const props = defineProps({
        source: {
            type: String,
            required: true
        }
    });

    const pdfDoc = ref<pdfjs.PDFDocumentProxy | undefined>(undefined);
    const pageNum = ref(1);
    const rendered = ref(false);
    const pageRendering = ref(false);
    const pageNumPending = ref<number | undefined>(undefined);
    const scale = ref(1.5);

    const canvas = computed(() => {
        return document.getElementById("pdf") as HTMLCanvasElement;
    });

    const context = computed(() => {
        return canvas.value?.getContext("2d") as CanvasRenderingContext2D;
    });

    const getWorkerUrl = (): string => {
        return new URL(
            "pdfjs-dist/build/pdf.worker.min.mjs",
            import.meta.url
        ).toString();
    };

    const renderPage = (pageNum: number): void => {
        pageRendering.value = true;

        pdfDoc.value?.getPage(pageNum).then((page) => {
            const viewport = page.getViewport({scale: scale.value});
            if (canvas.value) {
                canvas.value.height = viewport.height;
                canvas.value.width = viewport.width;

                const renderContext = {
                    canvasContext: context.value,
                    viewport: viewport,
                    canvas: canvas.value
                };
                const renderTask = page.render(renderContext);

                renderTask.promise.then(() => {
                    rendered.value = true;
                    pageRendering.value = false;

                    if (pageNumPending.value !== undefined) {
                        renderPage(pageNumPending.value);
                        pageNumPending.value = undefined;
                    }
                });
            }
        });
    };

    const initRender = (): void => {
        pdfjs.getDocument({data: atob(props.source)}).promise.then((pdf) => {
            pdfDoc.value = pdf;
            renderPage(pageNum.value);
        }, () => {
            // PDF loading error
            console.error("Failed to render PDF");
        });
    };

    const queueRenderPage = (pageNum: number): void => {
        if (pageRendering.value) {
            pageNumPending.value = pageNum;
        } else {
            renderPage(pageNum);
        }
    };

    const onPrevPage = (): void => {
        if (pageNum.value <= 1) {
            return;
        }
        pageNum.value--;
        queueRenderPage(pageNum.value);
    };

    const onNextPage = (): void => {
        if (pdfDoc.value && pageNum.value >= pdfDoc.value.numPages) {
            return;
        }
        pageNum.value++;
        queueRenderPage(pageNum.value);
    };

    onMounted(() => {
        pdfjs.GlobalWorkerOptions.workerSrc = getWorkerUrl();
        initRender();
    });
</script>

<style scoped lang="scss">
    nav {
        display: flex;
        gap: 1rem;
        align-items: center;
        justify-content: center;
        margin-top: 0.5em;
    }
</style>
