import DefaultTheme from 'vitepress/theme'
import './style.css'
import Layout from './Layout.vue'

import ConfigGroup from '../components/config/ConfigGroup.vue'
import ConfigProperty from '../components/config/ConfigProperty.vue'

import BaseTable from '../components/table/BaseTable.vue'
import CommandRow from '../components/table/CommandRow.vue'
import PermCommandRow from '../components/table/PermCommandRow.vue'
import PermRow from '../components/table/PermRow.vue'

import CardGrid from '../components/card/CardGrid.vue'
import DocCard from '../components/card/DocCard.vue'
import FeatureCard from '../components/card/FeatureCard.vue'
import FeatureMediaCard from '../components/card/FeatureMediaCard.vue'
import PluginGrid from '../components/card/PluginGrid.vue'

import Contributors from '../components/home/Contributors.vue'
import UsageStats from '../components/home/UsageStats.vue'
import LucideIcon from '../components/icon/LucideIcon.vue'
import LanguageDropdown from '../components/nav/LanguageDropdown.vue'

export default {
    extends: DefaultTheme,
    Layout,

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
        app.component('FeatureMediaCard', FeatureMediaCard)
        app.component('PluginGrid', PluginGrid)

        app.component('Contributors', Contributors)
        app.component('UsageStats', UsageStats)
        app.component('LucideIcon', LucideIcon)
        app.component('LanguageDropdown', LanguageDropdown)
    }
}
