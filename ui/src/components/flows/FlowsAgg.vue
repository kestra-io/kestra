<template>
  <div>
    <div>
      <data-table @onPageChanged="loadData" striped hover bordered ref="dataTable" :total="total">
        <template v-slot:navbar>
          <namespace-selector @onNamespaceSelect="onNamespaceSelect" />
          <v-s />
          <search-field @onSearch="onSearch" :fields="searchableFields" />
        </template>
        <template v-slot:table>
          <b-table
            :no-local-sorting="true"
            @row-dblclicked="onRowDoubleClick"
            @sort-changed="onSort"
            responsive="xl"
            fixed
            striped
            bordered
            hover
            :items="flows"
            :fields="fields"
            ref="table"
          >
            <template v-slot:cell(actions)="row">
              <router-link :to="{name: 'flow', params : row.item}">
                <eye id="edit-action" />
              </router-link>
            </template>

            <template v-slot:cell(state)="row">
              <chart
                v-if="row.item.metrics"
                :dateFormat="'YYYY-MM-DD'"
                :dateInterval="dateInterval"
                :endDate="endDate"
                :startDate="startDate"
                :data="{json : row.item.metrics,
                keys: { x: 'startDate', value: ['success', 'failed', 'created', 'running'] },
                groups: [['success', 'failed', 'created', 'running']] }"
              />
              <span v-if="!row.item.metrics">{{$t('no execution yet') | cap }}</span>
            </template>

            <template v-slot:cell(duration)="row">
              <span v-if="!row.item.trend">{{$t('no execution yet') | cap }}</span>

              <trend v-if="row.item.trend" :trend="row.item.trend" />

              <div class="stats">
                <span
                  v-if="row.item.lastDayDurationStats"
                  class="value"
                >{{row.item.lastDayDurationStats.avg | humanizeDuration }}</span>
                <span v-if="row.item.lastDayDurationStats" class="title">(24h)</span>
              </div>

              <div class="stats">
                <span
                  v-if="row.item.periodDurationStats"
                  class="value"
                >{{row.item.periodDurationStats.avg | humanizeDuration }}</span>
                <span v-if="row.item.periodDurationStats" class="title">(30d)</span>
              </div>
            </template>

            <template v-slot:cell(namespace)="row">
              <a href @click.prevent="onNamespaceSelect(row.item.namespace)">{{row.item.namespace}}</a>
            </template>
          </b-table>
        </template>
      </data-table>
    </div>
    <bottom-line>
      <ul class="navbar-nav ml-auto">
        <li class="nav-item">
          <router-link :to="{name: 'flowsAdd'}">
            <b-button variant="primary">
              <plus />
              {{$t('add flow') | cap }}
            </b-button>
          </router-link>
        </li>
      </ul>
    </bottom-line>
  </div>
</template>

<script>
import { mapState } from "vuex";
import NamespaceSelector from "../namespace/Selector";
import Plus from "vue-material-design-icons/Plus";
import Eye from "vue-material-design-icons/Eye";
import BottomLine from "../layout/BottomLine";
import RouteContext from "../../mixins/routeContext";
import DataTableActions from "../../mixins/dataTableActions";
import DataTable from "../layout/DataTable";
import SearchField from "../layout/SearchField";
import Chart from "./Chart";
import queryBuilder from "../../utils/queryBuilder";
import Trend from "../Trend";

export default {
  mixins: [RouteContext, DataTableActions],
  components: {
    NamespaceSelector,
    BottomLine,
    Plus,
    Eye,
    DataTable,
    SearchField,
    Chart,
    Trend
  },
  props: {
    endDate: {
      type: Date,
      default: () => {
        return new Date();
      }
    },
    dateInterval: {
      type: Number,
      default: () => {
        return -30;
      }
    }
  },
  data() {
    return {
      dataType: "flow"
    };
  },
  computed: {
    ...mapState("execution", ["flows", "total"]),
    fields() {
      const title = title => {
        return this.$t(title).capitalize();
      };

      return [
        {
          key: "id",
          label: title("flow"),
          sortable: true
        },
        {
          key: "namespace",
          label: title("namespace"),
          sortable: true
        },
        {
          key: "state",
          label: title("execution statistics"),
          sortable: false
        },
        {
          key: "duration",
          label: title("avg duration"),
          sortable: false
        },
        {
          key: "actions",
          label: "",
          class: "row-action"
        }
      ];
    },
    startDate() {
      return this.$moment(this.endDate)
        .add(this.dateInterval, "days")
        .toDate();
    }
  },
  methods: {
    loadData() {
      this.$store.dispatch("execution/findExecutionsAgg", {
        q: this.query,
        startDate: this.startDate.toISOString(),
        size: parseInt(this.$route.query.size || 25),
        page: parseInt(this.$route.query.page || 1),
        sort: this.$route.query.sort
      });
    }
  }
};
</script>
<style lang="scss" scoped>
@import "../../styles/_variable.scss";

.stats {
  display: block;
}
.stats span.title {
  padding-left: 10px;
  color: $gray-600;
}
.stats span.value {
  color: $gray-900;
}
</style>