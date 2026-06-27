import { defineConfig } from 'vitepress'

export default defineConfig({
  title: "SmartSpawner",
  description: "High-performance GUI-based spawner plugin for Minecraft",
  cleanUrls: true,
  head: [
    ['link', { rel: 'icon', type: 'image/svg+xml', href: '/logo.svg' }],
    ['link', { rel: 'icon', type: 'image/png', href: '/logo.png' }],
    ['link', { rel: 'apple-touch-icon', href: '/logo.png' }],
  ],
  themeConfig: {
    nav: [
      { text: 'Home', link: '/' },
      { text: 'Download', link: '/docs/download' },
      { text: 'Docs', link: '/docs/' }
    ],

    sidebar: [
      {
        text: 'General',
        items: [
          { text: 'Welcome', link: '/docs/' },
          { text: 'Features', link: '/docs/features' },
        ]
      },
      {
        text: 'Getting Started',
        items: [
          { text: 'Download', link: '/docs/download' },
          { text: 'Installation', link: '/docs/installation' }
        ]
      },
      {
        text: 'Documentation',
        items: [
          {
            text: 'Commands & Permissions',
            collapsed: false,
            items: [
              { text: 'Commands', link: '/docs/commands' },
              { text: 'Permissions', link: '/docs/permissions' }
            ]
          },
          {
            text: 'Configuration',
            collapsed: false,
            items: [
              { text: 'Main Config', link: '/docs/configuration' },
              { text: 'Spawner Settings', link: '/docs/spawners-settings' },
              { text: 'Item Spawner Settings', link: '/docs/item-spawners-settings' },
              { text: 'GUI Layout', link: '/docs/gui-layout' }
            ]
          },
          {
            text: 'Developer API',
            collapsed: true,
            items: [
              { text: 'Getting Started', link: '/docs/developer-api/' },
              { text: 'Installation', link: '/docs/developer-api/installation' },
              { text: 'API Creation', link: '/docs/developer-api/creation' },
              { text: 'Data Access', link: '/docs/developer-api/data-access' },
              { text: 'Events', link: '/docs/developer-api/events' },
              { text: 'GUI Layout API', link: '/docs/developer-api/gui-layout' },
              { text: 'Validation', link: '/docs/developer-api/validation' },
              { text: 'Examples', link: '/docs/developer-api/examples' }
            ]
          }
        ]
      }
    ],

    socialLinks: [
      { icon: 'github', link: 'https://github.com/OpenVdra/SmartSpawner' },
      { icon: 'discord', link: 'https://discord.gg/zrnyG4CuuT' }
    ],

    search: {
      provider: 'local'
    },

    editLink: {
      pattern: 'https://github.com/OpenVdra/SmartSpawner/edit/main/docs/:path',
      text: 'Edit this page on GitHub'
    }
  }
})
