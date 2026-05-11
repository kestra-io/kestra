import {deepMerge} from "./ksChartUtils.ts"
import {cssVar} from "../../utils/css.ts"

export default () => {
    const axis = {
        nameLocation: "end",
        nameTextStyle: {
            color: cssVar("--ks-gray-300"),
        },
        axisLine: {
            show: true,
            lineStyle: {
                width: 0.5,
                color: cssVar("--kel-text-color-placeholder"),
            },
        },
        axisTick: {
            show: false,
            lineStyle: {
                color: cssVar("--kel-text-color-placeholder"),
            },
        },
        axisLabel: {
            show: true,
            color: cssVar("--ks-content-primary"),
        },
        splitLine: {
            show: false,
            lineStyle: {
                width: 0.2,
                color: cssVar("--kel-text-color-disabled"),
            },
        },
        splitArea: {
            show: false,
            areaStyle: {
                color: [cssVar("--ks-gray-500", 0.00), cssVar("--ks-gray-inverted-900", 0.5)],
                shadowBlur: 0,
            },
        },
    }

    return  {
        animation: false,
        color: [
            cssVar("--ks-blue-300"),
            cssVar("--ks-green-300"),
            cssVar("--ks-orange-300"),
            cssVar("--ks-purple-300"),
            cssVar("--ks-red-300"),
            cssVar("--ks-yellow-300"),
            cssVar("--ks-gray-300"),
            cssVar("--ks-blue-800"),
            cssVar("--ks-green-800"),
            cssVar("--ks-orange-800"),
            cssVar("--ks-purple-800"),
            cssVar("--ks-red-800"),
            cssVar("--ks-yellow-800"),
            cssVar("--ks-gray-800"),
        ],
        backgroundColor: "transparent",
        textStyle: {
            fontFamily: "'Public Sans', sans-serif",
            fontSize: 12,
            color: cssVar("--ks-content-primary"),
        },
        title: {
            textStyle: {
                color: cssVar("--ks-content-primary"),
            },
            subtextStyle: {
                color: cssVar("--ks-gray-400"),
            },
        },
        line: {
            lineStyle: {
                width: 1,
            },
            symbolSize: 3,
            symbol: "circle",
            smooth: true,
        },

        bar: {
            itemStyle: {
                borderWidth: 0,
            },
        },
        pie: {
            label: {
                show: false,
            },
            itemStyle: {
                borderWidth: 0,
            },
        },
        graph: {
            label: {
                show: true,
                position: "bottom",
                fontSize: 10,
                textBorderWidth: 1,
                color: cssVar("--ks-content-primary"),
                textBorderColor: cssVar("--ks-background-body"),
            },
            lineStyle: {
                color: cssVar("--kel-text-color-placeholder"),
                curveness: 0.1,
            },
            emphasis: {
                focus: "none",
                scale: 1.1,
                itemStyle: {
                    shadowBlur: 10,
                    shadowColor: "rgba(0,0,0,0.3)",
                },
            },
            edgeSymbol: ["none", "arrow"],
            edgeSymbolSize: [0, 8],
        },
        categoryAxis: deepMerge(axis, {
            nameTextStyle: {
                align: "center",
                verticalAlign: "top",
            },

        }),
        valueAxis: deepMerge(axis, {
            nameTextStyle: {
                align: "right",
            },
            axisLine: {
                show: false,
            },
            splitLine: {
                show: true,
            },
        }),
        logAxis: axis,
        timeAxis: deepMerge(axis, {
            nameTextStyle: {
                align: "center",
                verticalAlign: "top",
            },
        }),
        tooltip: {
            backgroundColor: cssVar("--kel-bg-color-overlay"),
            borderColor: cssVar("--kel-border-color"),
            borderWidth: 1,
            borderRadius: 4,
            padding: [8, 12],
            textStyle: {
                color: cssVar("--kel-text-color-primary"),
                fontSize: 12,
                fontFamily: cssVar("--kbs-font-sans-serif"),
            },
            extraCssText: "box-shadow: var(--kel-box-shadow);",
            axisPointer: {
                shadowStyle: {
                    color: cssVar("--ks-purple-900", 0.1),
                },
            },
        },
        legend: {
            textStyle: {
                color: cssVar("--ks-gray-300"),
            },
            left: "center",
            right: "auto",
            top: 0,
            bottom: 10,
            icon: "circle",
        },
    }
}
