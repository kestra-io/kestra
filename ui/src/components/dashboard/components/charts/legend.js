import Utils from "../../../../utils/utils.js";
import {cssVariable} from "@kestra-io/ui-libs/src/utils/global";

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

            const text = document.createTextNode(item.text.toLowerCase());
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

        const maxVisibleItems = 4; // 최대 보이는 라벨 개수
        let hiddenItems = [];

        const updateLegend = (showAll = false) => {
            ul.innerHTML = ""; // 기존 내용 초기화
            hiddenItems = [];

            items.forEach((item, index) => {
                const dataset = chart.data.datasets[0];
                if (!dataset?.data || dataset.data[item.index] === 0) return;

                if (!showAll && index >= maxVisibleItems) {
                    hiddenItems.push(item);
                    return;
                }

                const li = createLegendItem(chart, dataset, item);
                ul.appendChild(li);
            });

            if (!showAll && hiddenItems.length > 0) {
                const seeMoreButton = document.createElement("li");
                seeMoreButton.style.alignItems = "center";
                seeMoreButton.style.cursor = "pointer";
                seeMoreButton.style.display = "flex";
                seeMoreButton.style.marginBottom = "10px";
                seeMoreButton.style.marginLeft = "10px";
                seeMoreButton.style.flexDirection = "row";
                seeMoreButton.style.color = "#007bff";
                seeMoreButton.style.fontWeight = "bold";

                seeMoreButton.textContent = "+ View More";

                seeMoreButton.onclick = () => {
                    updateLegend(true); // 모든 라벨 표시
                };

                ul.appendChild(seeMoreButton);
            } else if (showAll) {
                const dropButton = document.createElement("li");
                dropButton.style.alignItems = "center";
                dropButton.style.cursor = "pointer";
                dropButton.style.display = "flex";
                dropButton.style.marginBottom = "10px";
                dropButton.style.marginLeft = "10px";
                dropButton.style.flexDirection = "row";
                dropButton.style.color = "#ff4d4d";
                dropButton.style.fontWeight = "bold";

                dropButton.textContent = "- View Less";

                dropButton.onclick = () => {
                    updateLegend(false); // 4개만 표시
                };

                ul.appendChild(dropButton);
            }
        };

        updateLegend(); // 초기화
    },
};

function createLegendItem(chart, dataset, item) {
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
                !chart.isDatasetVisible(item.datasetIndex)
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
    textContainer.style.textDecoration = item.hidden ? "line-through" : "";
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

    return li;
}

