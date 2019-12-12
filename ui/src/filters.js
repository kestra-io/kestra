import Vue from 'vue'


Vue.filter('cap', value => value ? value.toString().capitalize() : '')
Vue.filter('date', (dateString, format) => {
    const f = format ? format : 'MMMM Do YYYY, h: mm: ss'
    return Vue.moment(dateString).format(f)
})
