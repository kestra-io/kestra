<template>
    <div>
        <canvas id="pdf" />

        <nav v-if="rendered">
            <el-tooltip :content="$t('page.previous')" effect="light" :show-after="1500">
                <el-button @click="onPrevPage">
                    <chevron-left />
                </el-button>
            </el-tooltip>
            <span>
                {{ pageNum }}
                {{ $t("of") }}
                {{ pdfDoc?.numPages }}
            </span>
            <el-tooltip :content="$t('page.next')" effect="light" :show-after="1500">
                <el-button @click="onNextPage">
                    <chevron-right />
                </el-button>
            </el-tooltip>
        </nav>
    </div>
</template>

<script>
    import * as pdfjs from "pdfjs-dist";
    import ChevronLeft from "vue-material-design-icons/ChevronLeft.vue";
    import ChevronRight from "vue-material-design-icons/ChevronRight.vue";

    export default {
        components: {
            ChevronLeft,
            ChevronRight
        },
        props: {
            source: {
                type: String,
                required: true
            }
        },
        data() {
            // Can't be a reactive prop
            this.pdfDoc = undefined;

            return {
                pageNum: 1,
                rendered: false,
                pageRendering: false,
                pageNumPending: undefined,
                scale: 1.5
            }
        },
        computed: {
            canvas() {
                return document.getElementById("pdf");
            },
            context() {
                return this.canvas.getContext("2d");
            }
        },
        mounted() {
            // Provide worker location
            pdfjs.GlobalWorkerOptions.workerSrc = this.getWorkerUrl();

            // Initial/first page rendering
            this.initRender();
        },
        methods: {
            getWorkerUrl() {
                return new URL(
                    "pdfjs-dist/build/pdf.worker.min.mjs",
                    import.meta.url
                ).toString();
            },
            renderPage(pageNum) {
                this.pageRendering = true;

                // Using promise to fetch the page
                this.pdfDoc.getPage(pageNum).then((page) => {
                    const viewport = page.getViewport({scale: this.scale});
                    this.canvas.height = viewport.height;
                    this.canvas.width = viewport.width;

                    // Render PDF page into canvas context
                    const renderContext = {
                        canvasContext: this.context,
                        viewport: viewport
                    };
                    const renderTask = page.render(renderContext);

                    // Wait for rendering to finish
                    renderTask.promise.then(() => {
                        this.rendered = true;
                        this.pageRendering = false;

                        if (this.pageNumPending) {
                            // New page rendering is pending
                            this.renderPage(this.pageNumPending);
                            this.pageNumPending = undefined;
                        }
                    });
                });
            },
            initRender() {
                // Decode PDF document
                pdfjs.getDocument({data: atob(this.source)}).promise.then((pdf) => {
                    this.pdfDoc = pdf;

                    this.renderPage(this.pageNum);
                }, () => {
                    // PDF loading error
                    this.$toast().error(this.$t("failed to render pdf"));
                });
            },
            queueRenderPage(pageNum) {
                if (this.pageRendering) {
                    this.pageNumPending = pageNum;
                } else {
                    this.renderPage(pageNum);
                }
            },
            onPrevPage() {
                if (this.pageNum <= 1) {
                    return;
                }
                this.pageNum--;
                this.queueRenderPage(this.pageNum);
            },
            onNextPage() {
                if (this.pageNum >= this.pdfDoc.numPages) {
                    return;
                }
                this.pageNum++;
                this.queueRenderPage(this.pageNum);
            }
        }
    }
</script>

<style lang="scss" scoped>
    nav {
        display: flex;
        gap: 1rem;
        align-items: center;
        justify-content: center;
        margin-top: 0.5em;
    }
</style>
