import DefaultTheme from 'vitepress/theme'
import './style.css'

import ConfigGroup from '../components/config/ConfigGroup.vue'
import ConfigProperty from '../components/config/ConfigProperty.vue'

import BaseTable from '../components/table/BaseTable.vue'
import CommandRow from '../components/table/CommandRow.vue'
import PermCommandRow from '../components/table/PermCommandRow.vue'
import PermRow from '../components/table/PermRow.vue'

import CardGrid from '../components/card/CardGrid.vue'
import DocCard from '../components/card/DocCard.vue'
import FeatureCard from '../components/card/FeatureCard.vue'

import Contributors from '../components/home/Contributors.vue'

export default {
    extends: DefaultTheme,

    enhanceApp({ app }) {
        app.component('ConfigGroup', ConfigGroup)
        app.component('ConfigProperty', ConfigProperty)

        app.component('BaseTable', BaseTable)
        app.component('CommandRow', CommandRow)
        app.component('PermCommandRow', PermCommandRow)
        app.component('PermRow', PermRow)

        app.component('CardGrid', CardGrid)
        app.component('DocCard', DocCard)
        app.component('FeatureCard', FeatureCard)

        app.component('Contributors', Contributors)
    }
}
