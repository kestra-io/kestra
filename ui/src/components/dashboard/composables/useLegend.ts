import Utils from "../../../utils/utils";
import {cssVariable} from "@kestra-io/ui-libs";
import {getConsistentHEXColor} from "./charts";
import {ChartTypeRegistry, Plugin} from "chart.js";


const getOrCreateLegendList = (id: string, direction: "row" | "column" = "row", width: string = "100%") => {
    const legendContainer = document.getElementById(id);

    if(!legendContainer) {
        throw new Error(`Legend container with id ${id} not found`);
    }

    legendContainer.style.width = width;
    legendContainer.style.justifyItems = "end";

    let listContainer = legendContainer?.querySelector("ul");

    if (!listContainer) {
        listContainer = document.createElement("ul");
        listContainer.classList.add("w-100", "mb-3", "fw-light", "legend", direction === "row" ? "small" : "tall");
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

function defineChartPlugin<T extends keyof ChartTypeRegistry>(plugin: Plugin<T>) {
    return plugin;
}

export const barLegend = defineChartPlugin<"bar" | "pie" | "doughnut">({
    id: "barLegend",
    afterUpdate(chart, _args, options) {
        const ul = getOrCreateLegendList(options.containerID);

        while (ul.firstChild) {
            ul.firstChild.remove();
        }

        const items = chart.options.plugins?.legend?.labels?.generateLabels?.(chart) ?? [];

        items.forEach((item) => {
            const dataset = chart.data.datasets[item.datasetIndex ?? -1];

            if (
                !dataset?.data ||
                ("yAxisID" in dataset && dataset.yAxisID === "yB") ||
                dataset.data.every((val) => val === 0)
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
                const type = ("type" in chart.config) ? chart.config.type : "none";
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
            if(typeof item.fillStyle === "string") boxSpan.style.background = item.fillStyle;
            if(typeof item.strokeStyle === "string") boxSpan.style.borderColor = item.strokeStyle;
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
                    : cssVariable("--bs-gray-700") ?? "#000000";
            textContainer.style.margin = "0";
            textContainer.style.textDecoration = item.hidden
                ? "line-through"
                : "";

            if (!options.uppercase) item.text = item.text.toLowerCase();

            const text = document.createTextNode(item.text);
            textContainer.appendChild(text);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul.appendChild(li);
        });
    },
});

export const customBarLegend = defineChartPlugin<"bar">({
    id: "customBarLegend",
    afterUpdate(chart, _args, options) {
        const ul = getOrCreateLegendList(options.containerID);

        while (ul.firstChild) {
            ul.firstChild.remove();
        }

        const seenLegendLabels: string[] = [];
        const items = chart.options.plugins?.legend?.labels?.generateLabels?.(chart).filter(l => {
            if (seenLegendLabels.includes(l.text)) {
                return false;
            }

            seenLegendLabels.push(l.text);
            return true;
        });

        items?.forEach((item) => {
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
            const color = item.strokeStyle === "transparent" ? getConsistentHEXColor(Utils.getTheme(), item.text) : item.strokeStyle;
            if(typeof color === "string") boxSpan.style.background = color;
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
                    : cssVariable("--bs-gray-700") ?? "#000000";
            textContainer.style.margin = "0";
            textContainer.style.textDecoration = item.hidden
                ? "line-through"
                : "";

            const text = document.createTextNode(item.text);
            textContainer.appendChild(text);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul.appendChild(li);
        });
    },
});

const generateTotalsLegend = (isDuration: boolean) => (defineChartPlugin<"bar" | "pie" | "doughnut">({
    id: "totalsLegend",
    afterUpdate(chart, _args, options) {
        const ul = getOrCreateLegendList(options.containerID, "column", "auto");

        while (ul.firstChild) {
            ul.firstChild.remove();
        }

        const items = chart.options.plugins?.legend?.labels?.generateLabels?.(chart);

        items?.sort((a, b) => {
            const dataset = chart.data.datasets[0];

            const valueA = dataset.data[a.index ?? -1];
            const valueB = dataset.data[b.index ?? -1];

            const numberA = typeof valueA === "number" ? valueA : (valueA && valueA[0] ? valueA[0] : 0);
            const numberB = typeof valueB === "number" ? valueB : (valueB && valueB[0] ? valueB[0] : 0);

            return numberB - numberA;
        });

        items?.forEach((item) => {
            const dataset = chart.data.datasets[0];
            if (!dataset?.data || dataset.data[item.index ?? -1] === 0) return;

            const li = document.createElement("li");
            li.style.alignItems = "center";
            li.style.cursor = "pointer";
            li.style.display = "flex";
            li.style.marginBottom = "10px";
            li.style.marginLeft = "10px";
            li.style.flexDirection = "row";

            li.onclick = () => {
                const {type} = "type" in chart.config ? chart.config : {type: "none"};
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
            if(typeof item.fillStyle === "string") boxSpan.style.background = item.fillStyle;
            if(typeof item.strokeStyle === "string") boxSpan.style.borderColor = item.strokeStyle;
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
                    : cssVariable("--bs-gray-700") ?? "#000000";
            textContainer.style.margin = "0";
            textContainer.style.textDecoration = item.hidden
                ? "line-through"
                : "";
            textContainer.style.textAlign = "left";

            const executionsText = document.createElement("p");
            executionsText.style.margin = "0";
            executionsText.style.fontWeight = "bold";
            executionsText.style.fontSize = "18px";
            executionsText.style.lineHeight = "18px";
            executionsText.style.color =
                Utils.getTheme() === "dark"
                    ? "#FFFFFF"
                    : cssVariable("--bs-gray-700") ?? "#000000";
            const durationNumber = dataset.data[item.index ?? -1]
            const durationString = typeof durationNumber === "number" ? durationNumber : (durationNumber && durationNumber[0] ? durationNumber[0] : 0);
            executionsText.textContent = isDuration
                ? Utils.humanDuration(durationString)
                : durationString.toString();

            const labelText = document.createElement("p");
            labelText.style.margin = "0";
            labelText.textContent = item.text.toLowerCase();

            textContainer.appendChild(executionsText);
            textContainer.appendChild(labelText);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul.appendChild(li);
        });
    }
}));

export const totalsDurationLegend = generateTotalsLegend(true)

export const totalsLegend = generateTotalsLegend(false);
