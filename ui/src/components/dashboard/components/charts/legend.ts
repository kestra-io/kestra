import {type LegendItem} from "chart.js";
import Utils from "../../../../utils/utils";
import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";
import {getConsistentHEXColor} from "../../../../utils/charts";
import {type STATE} from "../../../../utils/state";
import {Plugin} from "chart.js";

const getOrCreateLegendList = (_: any, id: string, direction = "row") => {
    const legendContainer = document.getElementById(id);

    if(!legendContainer) {
        return;
    }

    legendContainer.style.width = "100%";
    legendContainer.style.justifyItems = "end";

    let listContainer = legendContainer?.querySelector("ul");

    if (!listContainer) {
        listContainer = document.createElement("ul");
        listContainer.classList.add("w-100", "fw-light", "small", "legend");
        listContainer.style.display = "flex";
        listContainer.style.flexDirection = direction;
        listContainer.style.margin = "0";
        listContainer.style.padding = "0";

        listContainer.style.maxHeight = "196px"; // 4 visible items
        listContainer.style.overflow = "auto";

        legendContainer?.appendChild(listContainer);
    }

    return listContainer;
};

export const barLegend: Plugin = {
    id: "barLegend",
    afterUpdate(chart, _, options) {
        const ul = getOrCreateLegendList(chart, options.containerID);

        while (ul?.firstChild) {
            ul.firstChild.remove();
        }

        const items:LegendItem[] = chart.options.plugins?.legend?.labels?.generateLabels?.(chart) ?? [];

        items.forEach((item) => {
            const dataset = chart.data.datasets[item.datasetIndex!] as any;

            if (
                !dataset?.data ||
                dataset.yAxisID === "yB" ||
                dataset.data.every((val:number) => val === 0)
            ) {
                return;
            }

            const li = document.createElement("li");
            li.style.alignItems = "center";
            li.style.cursor = "pointer";
            li.style.display = "flex";
            li.style.marginLeft = "20px";
            li.style.marginTop = "10px";

            li.onclick = () => {
                const {type} = chart.config as any;
                if (type === "pie" || type === "doughnut") {
                    chart.toggleDataVisibility(item.index ?? -1);
                } else {
                    chart.setDatasetVisibility(
                        item.datasetIndex ?? -1,
                        !chart.isDatasetVisible(item.datasetIndex ?? -1),
                    );
                }
                chart.update();
            };

            const boxSpan = document.createElement("span");
            boxSpan.style.background = item.fillStyle?.toString() ?? "transparent";
            boxSpan.style.borderColor = item.strokeStyle?.toString() ?? "transparent";
            boxSpan.style.borderWidth = `${item.lineWidth}px`;
            boxSpan.style.height = "5px";
            boxSpan.style.width = "5px";
            boxSpan.style.borderRadius = "50%";
            boxSpan.style.display = "inline-block";
            boxSpan.style.marginRight = "10px";

            const textContainer = document.createElement("p");
            textContainer.style.color =
                Utils.getTheme() === "dark"
                    ? "#FFFFFF"
                    : cssVariable("--bs-gray-700");
            textContainer.style.margin = "0";
            textContainer.style.textDecoration = item.hidden
                ? "line-through"
                : "";
            textContainer.style.textTransform = "capitalize";

            if (!options.uppercase) item.text = item.text.toLowerCase();

            const text = document.createTextNode(item.text);
            textContainer.appendChild(text);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul?.appendChild(li);
        });
    },
};

export const customBarLegend: Plugin = {
    id: "customBarLegend",
    afterUpdate(chart, _, options) {
        const ul = getOrCreateLegendList(chart, options.containerID);

        while (ul?.firstChild) {
            ul.firstChild.remove();
        }

        const seenLegendLabels: string[] = [];
        const items = chart.options.plugins?.legend?.labels?.generateLabels?.(chart).filter(l => {
            if (seenLegendLabels.includes(l.text)) {
                return false;
            }

            seenLegendLabels.push(l.text);
            return true;
        }) ?? [];

        items.forEach((item) => {
            const li = document.createElement("li");
            li.style.alignItems = "center";
            li.style.cursor = "pointer";
            li.style.display = "flex";
            li.style.marginLeft = "20px";
            li.style.marginTop = "10px";

            li.onclick = () => {
                chart.data.datasets.forEach((dataset, index) => {
                    if (dataset.label === item.text) {
                        chart.setDatasetVisibility(
                            index,
                            !chart.isDatasetVisible(index),
                        );
                    }
                });
                chart.update();
            };

            const boxSpan = document.createElement("span");
            const color = getConsistentHEXColor(item.text as keyof typeof STATE);
            boxSpan.style.background = color;
            boxSpan.style.borderColor = "transparent";
            boxSpan.style.height = "5px";
            boxSpan.style.width = "5px";
            boxSpan.style.borderRadius = "50%";
            boxSpan.style.display = "inline-block";
            boxSpan.style.marginRight = "10px";

            const textContainer = document.createElement("p");
            textContainer.style.color =
                Utils.getTheme() === "dark"
                    ? "#FFFFFF"
                    : cssVariable("--bs-gray-700");
            textContainer.style.margin = "0";
            textContainer.style.textDecoration = item.hidden
                ? "line-through"
                : "";
            textContainer.style.textTransform = "capitalize";

            const text = document.createTextNode(item.text);
            textContainer.appendChild(text);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul?.appendChild(li);
        });
    },
};

const generateTotalsLegend = (isDuration:boolean):Plugin => ({
    id: "totalsLegend",
    afterUpdate(chart, _, options) {
        const ul = getOrCreateLegendList(chart, options.containerID, "column");

        while (ul?.firstChild) {
            ul.firstChild.remove();
        }

        const items = chart.options.plugins?.legend?.labels?.generateLabels?.(chart) ?? [];

        items.sort((a, b) => {
            if(a.index === undefined || b.index === undefined) return 0;

            const dataset = chart.data.datasets[0];

            const valueA = dataset.data[a.index] as number;
            const valueB = dataset.data[b.index] as number;

            return valueB - valueA;
        });

        items.forEach((item) => {
            const dataset = chart.data.datasets[0];
            if(item.index === undefined) return;
            if (!dataset?.data || (dataset.data[item.index] === 0)) return;

            const li = document.createElement("li");
            li.style.alignItems = "center";
            li.style.cursor = "pointer";
            li.style.display = "flex";
            li.style.marginBottom = "10px";
            li.style.marginLeft = "10px";
            li.style.flexDirection = "row";

            li.onclick = () => {
                const {type} = chart.config as any;
                if(item.index === undefined) return;
                if (type === "pie" || type === "doughnut") {
                    chart.toggleDataVisibility(item.index);
                } else {
                    chart.setDatasetVisibility(
                        item.datasetIndex ?? -1,
                        !chart.isDatasetVisible(item.datasetIndex ?? -1),
                    );
                }
                chart.update();
            };

            const boxSpan = document.createElement("span");
            boxSpan.style.background = item.fillStyle?.toString() ?? "transparent";
            boxSpan.style.borderColor = item.strokeStyle?.toString() ?? "transparent";
            boxSpan.style.borderWidth = `${item.lineWidth}px`;
            boxSpan.style.height = "10px";
            boxSpan.style.width = "10px";
            boxSpan.style.borderRadius = "50%";
            boxSpan.style.display = "inline-block";
            boxSpan.style.marginRight = "10px";

            const textContainer = document.createElement("div");
            textContainer.style.color =
                Utils.getTheme() === "dark"
                    ? "#FFFFFF"
                    : cssVariable("--bs-gray-700");
            textContainer.style.margin = "0";
            textContainer.style.textDecoration = item.hidden
                ? "line-through"
                : "";
            textContainer.style.textTransform = "capitalize";
            textContainer.style.textAlign = "left";

            const executionsText = document.createElement("p");
            executionsText.style.margin = "0";
            executionsText.style.fontWeight = "bold";
            executionsText.style.fontSize = "18px";
            executionsText.style.lineHeight = "18px";
            executionsText.style.color =
                Utils.getTheme() === "dark"
                    ? "#FFFFFF"
                    : cssVariable("--bs-gray-700");
            executionsText.textContent = isDuration ? Utils.humanDuration(dataset.data[item.index] as number) : dataset.data[item.index]?.toString() ?? null;

            const labelText = document.createElement("p");
            labelText.style.margin = "0";
            labelText.textContent = item.text.toLowerCase();

            textContainer.appendChild(executionsText);
            textContainer.appendChild(labelText);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul?.appendChild(li);
        });
    }
});

export const totalsDurationLegend = generateTotalsLegend(true)

export const totalsLegend = generateTotalsLegend(false);
