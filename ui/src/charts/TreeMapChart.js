import {createTypedChart} from "vue-chartjs";
import {Tooltip} from "chart.js";
import {TreemapController} from "chartjs-chart-treemap";

const TreeMapChart = createTypedChart("treemap", {Tooltip, TreemapController});

export default TreeMapChart;