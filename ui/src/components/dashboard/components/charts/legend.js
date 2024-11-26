import Utils from "../../../../utils/utils.js";
import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";
import {getConsistentHEXColor} from "../../../../utils/charts.js";

const getOrCreateLegendList = (chart, id, direction = "row") => {
    const legendContainer = document.getElementById(id);
    let listContainer = legendContainer.querySelector("ul");

    if (!listContainer) {
        listContainer = document.createElement("ul");
        listContainer.classList.add("fw-light", "small");
        listContainer.style.display = "flex";
        listContainer.style.flexDirection = direction;
        listContainer.style.margin = 0;
        listContainer.style.padding = 0;

        legendContainer.appendChild(listContainer);
    }

    return listContainer;
};

export const barLegend = {
    id: "barLegend",
    afterUpdate(chart, args, options) {
        const ul = getOrCreateLegendList(chart, options.containerID);

        while (ul.firstChild) {
            ul.firstChild.remove();
        }

        const items = chart.options.plugins.legend.labels.generateLabels(chart);

        items.forEach((item) => {
            const dataset = chart.data.datasets[item.datasetIndex];

            if (
                !dataset?.data ||
                dataset.yAxisID === "yB" ||
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
                const {type} = chart.config;
                if (type === "pie" || type === "doughnut") {
                    chart.toggleDataVisibility(item.index);
                } else {
                    chart.setDatasetVisibility(
                        item.datasetIndex,
                        !chart.isDatasetVisible(item.datasetIndex),
                    );
                }
                chart.update();
            };

            const boxSpan = document.createElement("span");
            boxSpan.style.background = item.fillStyle;
            boxSpan.style.borderColor = item.strokeStyle;
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
            textContainer.style.margin = 0;
            textContainer.style.textDecoration = item.hidden
                ? "line-through"
                : "";
            textContainer.style.textTransform = "capitalize";

            if (!options.uppercase) item.text = item.text.toLowerCase();

            const text = document.createTextNode(item.text);
            textContainer.appendChild(text);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul.appendChild(li);
        });
    },
};

export const customBarLegend = {
    id: "customBarLegend",
    afterUpdate(chart, args, options) {
        const ul = getOrCreateLegendList(chart, options.containerID);

        while (ul.firstChild) {
            ul.firstChild.remove();
        }

        const items = chart.options.plugins.legend.labels.generateLabels(chart);

        const uniqueStatuses = new Set(
            items
                .map((item) => chart.data.datasets[item.datasetIndex]?.label)
                .filter(Boolean),
        );

        uniqueStatuses.forEach((item) => {
            const li = document.createElement("li");
            li.style.alignItems = "center";
            li.style.cursor = "pointer";
            li.style.display = "flex";
            li.style.marginLeft = "20px";
            li.style.marginTop = "10px";

            li.onclick = () => {
                chart.data.datasets.forEach((dataset, index) => {
                    if (dataset.label === item) {
                        chart.setDatasetVisibility(
                            index,
                            !chart.isDatasetVisible(index),
                        );
                    }
                });
                chart.update();
            };

            const boxSpan = document.createElement("span");
            const color = getConsistentHEXColor(item);
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
            textContainer.style.margin = 0;
            // TODO: Improve the strikethrough of clicked items
            textContainer.style.textDecoration = item.hidden
                ? "line-through"
                : "";
            textContainer.style.textTransform = "capitalize";

            const text = document.createTextNode(item);
            textContainer.appendChild(text);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul.appendChild(li);
        });
    },
};

export const totalsLegend = {
    id: "totalsLegend",
    afterUpdate(chart, args, options) {
        const ul = getOrCreateLegendList(chart, options.containerID, "column");

        while (ul.firstChild) {
            ul.firstChild.remove();
        }

        const items = chart.options.plugins.legend.labels.generateLabels(chart);

        items.sort((a, b) => {
            const dataset = chart.data.datasets[0];

            const valueA = dataset.data[a.index];
            const valueB = dataset.data[b.index];

            return valueB - valueA;
        });

        items.forEach((item) => {
            const dataset = chart.data.datasets[0];
            if (!dataset?.data || dataset.data[item.index] === 0) return;

            const li = document.createElement("li");
            li.style.alignItems = "center";
            li.style.cursor = "pointer";
            li.style.display = "flex";
            li.style.marginBottom = "10px";
            li.style.marginLeft = "10px";
            li.style.flexDirection = "row";

            li.onclick = () => {
                const {type} = chart.config;
                if (type === "pie" || type === "doughnut") {
                    chart.toggleDataVisibility(item.index);
                } else {
                    chart.setDatasetVisibility(
                        item.datasetIndex,
                        !chart.isDatasetVisible(item.datasetIndex),
                    );
                }
                chart.update();
            };

            const boxSpan = document.createElement("span");
            boxSpan.style.background = item.fillStyle;
            boxSpan.style.borderColor = item.strokeStyle;
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
            textContainer.style.margin = 0;
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
            executionsText.textContent = dataset.data[item.index];

            const labelText = document.createElement("p");
            labelText.style.margin = "0";
            labelText.textContent = item.text.toLowerCase();

            textContainer.appendChild(executionsText);
            textContainer.appendChild(labelText);

            li.appendChild(boxSpan);
            li.appendChild(textContainer);
            ul.appendChild(li);
        });
    },
};
