const getOrCreateLegendList = (chart, id) => {
    const legendContainer = document.getElementById(id);
    let listContainer = legendContainer.querySelector("ul");

    if (!listContainer) {
        listContainer = document.createElement("ul");
        listContainer.classList.add("fw-light", "small");
        listContainer.style.display = "flex";
        listContainer.style.flexDirection = "row";
        listContainer.style.margin = 0;
        listContainer.style.padding = 0;

        legendContainer.appendChild(listContainer);
    }

    return listContainer;
};

export const customLegend = {
    id: "customLegend",
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
            textContainer.style.color = item.fontColor;
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
