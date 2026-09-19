import { defineConfig } from 'vitepress'

const REPO = 'https://github.com/OpenVdra/SmartSpawner'
const DISCORD = 'https://discord.gg/zrnyG4CuuT'
const MODRINTH = 'https://modrinth.com/plugin/smartspawner'
const MODRINTH_ICON = '<svg role="img" viewBox="0 0 24 24" xmlns="http://www.w3.org/2000/svg"><path d="M12.252.004a11.78 11.768 0 0 0-8.92 3.73 11 10.999 0 0 0-2.17 3.11 11.37 11.359 0 0 0-1.16 5.169c0 1.42.17 2.5.6 3.77.24.759.77 1.899 1.17 2.529a12.3 12.298 0 0 0 8.85 5.639c.44.05 2.54.07 2.76.02.2-.04.22.1-.26-1.7l-.36-1.37-1.01-.06a8.5 8.489 0 0 1-5.18-1.8 5.34 5.34 0 0 1-1.3-1.26c0-.05.34-.28.74-.5a37.572 37.545 0 0 1 2.88-1.629c.03 0 .5.45 1.06.98l1 .97 2.07-.43 2.06-.43 1.47-1.47c.8-.8 1.48-1.5 1.48-1.52 0-.09-.42-1.63-.46-1.7-.04-.06-.2-.03-1.02.18-.53.13-1.2.3-1.45.4l-.48.15-.53.53-.53.53-.93.1-.93.07-.52-.5a2.7 2.7 0 0 1-.96-1.7l-.13-.6.43-.57c.68-.9.68-.9 1.46-1.1.4-.1.65-.2.83-.33.13-.099.65-.579 1.14-1.069l.9-.9-.7-.7-.7-.7-1.95.54c-1.07.3-1.96.53-1.97.53-.03 0-2.23 2.48-2.63 2.97l-.29.35.28 1.03c.16.56.3 1.16.31 1.34l.03.3-.34.23c-.37.23-2.22 1.3-2.84 1.63-.36.2-.37.2-.44.1-.08-.1-.23-.6-.32-1.03-.18-.86-.17-2.75.02-3.73a8.84 8.839 0 0 1 7.9-6.93c.43-.03.77-.08.78-.1.06-.17.5-2.999.47-3.039-.01-.02-.1-.02-.2-.03Zm3.68.67c-.2 0-.3.1-.37.38-.06.23-.46 2.42-.46 2.52 0 .04.1.11.22.16a8.51 8.499 0 0 1 2.99 2 8.38 8.379 0 0 1 2.16 3.449 6.9 6.9 0 0 1 .4 2.8c0 1.07 0 1.27-.1 1.73a9.37 9.369 0 0 1-1.76 3.769c-.32.4-.98 1.06-1.37 1.38-.38.32-1.54 1.1-1.7 1.14-.1.03-.1.06-.07.26.03.18.64 2.56.7 2.78l.06.06a12.07 12.058 0 0 0 7.27-9.4c.13-.77.13-2.58 0-3.4a11.96 11.948 0 0 0-5.73-8.578c-.7-.42-2.05-1.06-2.25-1.06Z"/></svg>'
const JAVADOCS = 'https://docs.smartspawner.site/javadocs/'

const enManualSidebar = [
  {
    text: 'Getting Started',
    items: [
      { text: 'Overview', link: '/docs/' },
      { text: 'Installation', link: '/docs/installation' },
      { text: 'Download', link: '/docs/download' },
      { text: 'FAQ', link: '/docs/faq' }
    ]
  },
  {
    text: 'Server Guide',
    items: [
      { text: 'Plugin Compatibility', link: '/docs/plugin-compatibility' },
      { text: 'Commands', link: '/docs/commands' },
      { text: 'Permissions', link: '/docs/permissions' },
      { text: 'Database Support', link: '/docs/database-support' },
      { text: 'Action Logging', link: '/docs/action-logging' }
    ]
  },
  {
    text: 'Configuration',
    items: [
      { text: 'Main Config', link: '/docs/configuration' },
      { text: 'Sell Integration', link: '/docs/sell-integration' },
      { text: 'Mob Spawners', link: '/docs/spawner-mobs' },
      { text: 'Item Spawners', link: '/docs/spawner-items' },
      { text: 'GUI Layout', link: '/docs/gui-layout' }
    ]
  },
  {
    text: 'Integrations',
    items: [
      { text: 'Overview', link: '/docs/integrations/' },
      {
        text: 'Shops and Economy',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/docs/integrations/shops/' },
          { text: 'EconomyShopGUI', link: '/docs/integrations/shops/economyshopgui' },
          { text: 'ShopGUI+', link: '/docs/integrations/shops/shopguiplus' },
          { text: 'zShop', link: '/docs/integrations/shops/zshop' },
          { text: 'Vault', link: '/docs/integrations/shops/vault' },
          { text: 'ExcellentEconomy', link: '/docs/integrations/shops/excellenteconomy' }
        ]
      },
      {
        text: 'Protections and Claims',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/docs/integrations/protections/' },
          { text: 'WorldGuard', link: '/docs/integrations/protections/worldguard' },
          { text: 'GriefPrevention', link: '/docs/integrations/protections/griefprevention' },
          { text: 'Lands', link: '/docs/integrations/protections/lands' },
          { text: 'Towny', link: '/docs/integrations/protections/towny' },
          { text: 'Residence', link: '/docs/integrations/protections/residence' },
          { text: 'RedProtect', link: '/docs/integrations/protections/redprotect' },
          { text: 'SimpleClaimSystem', link: '/docs/integrations/protections/simpleclaimsystem' },
          { text: 'FactionsUUID', link: '/docs/integrations/protections/factionsuuid' },
          { text: 'BlockLocker', link: '/docs/integrations/protections/blocklocker' }
        ]
      },
      {
        text: 'Islands and Plots',
        collapsed: true,
        items: [
          { text: 'Overview', link: '/docs/integrations/islands/' },
          { text: 'PlotSquared', link: '/docs/integrations/islands/plotsquared' },
          { text: 'minePlots', link: '/docs/integrations/islands/mineplots' },
          { text: 'SuperiorSkyblock2', link: '/docs/integrations/islands/superiorskyblock2' },
          { text: 'BentoBox', link: '/docs/integrations/islands/bentobox' },
          { text: 'IridiumSkyblock', link: '/docs/integrations/islands/iridiumskyblock' }
        ]
      },
      { text: 'AuraSkills', link: '/docs/integrations/auraskills' },
      { text: 'MythicMobs', link: '/docs/integrations/mythicmobs' },
      { text: 'MMOItems', link: '/docs/integrations/mmoitems' },
      { text: 'Troubleshooting', link: '/docs/integrations/troubleshooting' }
    ]
  }
]

const viManualSidebar = [
  {
    text: 'Bắt đầu',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/' },
      { text: 'Cài đặt', link: '/vi/docs/installation' },
      { text: 'Tải xuống', link: '/vi/docs/download' },
      { text: 'Câu hỏi thường gặp', link: '/vi/docs/faq' }
    ]
  },
  {
    text: 'Hướng dẫn máy chủ',
    items: [
      { text: 'Tương thích plugin', link: '/vi/docs/plugin-compatibility' },
      { text: 'Lệnh', link: '/vi/docs/commands' },
      { text: 'Quyền', link: '/vi/docs/permissions' },
      { text: 'Hỗ trợ cơ sở dữ liệu', link: '/vi/docs/database-support' },
      { text: 'Nhật ký thao tác', link: '/vi/docs/action-logging' }
    ]
  },
  {
    text: 'Cấu hình',
    items: [
      { text: 'Cấu hình chính', link: '/vi/docs/configuration' },
      { text: 'Tích hợp bán', link: '/vi/docs/sell-integration' },
      { text: 'Spawner Mob', link: '/vi/docs/spawner-mobs' },
      { text: 'Item Spawner', link: '/vi/docs/spawner-items' },
      { text: 'Bố cục GUI', link: '/vi/docs/gui-layout' }
    ]
  },
  {
    text: 'Tích hợp',
    items: [
      { text: 'Tổng quan', link: '/vi/docs/integrations/' },
      {
        text: 'Cửa hàng và kinh tế',
        collapsed: true,
        items: [
          { text: 'Tổng quan', link: '/vi/docs/integrations/shops/' },
          { text: 'EconomyShopGUI', link: '/vi/docs/integrations/shops/economyshopgui' },
          { text: 'ShopGUI+', link: '/vi/docs/integrations/shops/shopguiplus' },
          { text: 'zShop', link: '/vi/docs/integrations/shops/zshop' },
          { text: 'Vault', link: '/vi/docs/integrations/shops/vault' },
          { text: 'ExcellentEconomy', link: '/vi/docs/integrations/shops/excellenteconomy' }
        ]
      },
      {
        text: 'Bảo vệ và claim',
        collapsed: true,
        items: [
          { text: 'Tổng quan', link: '/vi/docs/integrations/protections/' },
          { text: 'WorldGuard', link: '/vi/docs/integrations/protections/worldguard' },
          { text: 'GriefPrevention', link: '/vi/docs/integrations/protections/griefprevention' },
          { text: 'Lands', link: '/vi/docs/integrations/protections/lands' },
          { text: 'Towny', link: '/vi/docs/integrations/protections/towny' },
          { text: 'Residence', link: '/vi/docs/integrations/protections/residence' },
          { text: 'RedProtect', link: '/vi/docs/integrations/protections/redprotect' },
          { text: 'SimpleClaimSystem', link: '/vi/docs/integrations/protections/simpleclaimsystem' },
          { text: 'FactionsUUID', link: '/vi/docs/integrations/protections/factionsuuid' },
          { text: 'BlockLocker', link: '/vi/docs/integrations/protections/blocklocker' }
        ]
      },
      {
        text: 'Đảo và plot',
        collapsed: true,
        items: [
          { text: 'Tổng quan', link: '/vi/docs/integrations/islands/' },
          { text: 'PlotSquared', link: '/vi/docs/integrations/islands/plotsquared' },
          { text: 'minePlots', link: '/vi/docs/integrations/islands/mineplots' },
          { text: 'SuperiorSkyblock2', link: '/vi/docs/integrations/islands/superiorskyblock2' },
          { text: 'BentoBox', link: '/vi/docs/integrations/islands/bentobox' },
          { text: 'IridiumSkyblock', link: '/vi/docs/integrations/islands/iridiumskyblock' }
        ]
      },
      { text: 'AuraSkills', link: '/vi/docs/integrations/auraskills' },
      { text: 'MythicMobs', link: '/vi/docs/integrations/mythicmobs' },
      { text: 'MMOItems', link: '/vi/docs/integrations/mmoitems' },
      { text: 'Khắc phục sự cố', link: '/vi/docs/integrations/troubleshooting' }
    ]
  }
]

const enFeaturesSidebar = [
  {
    text: 'Features',
    items: [
      { text: 'Overview', link: '/features/' },
      { text: 'Spawner Types', link: '/features/spawner-types' },
      { text: 'Stacking System', link: '/features/stacking-system' },
      { text: 'GUI System', link: '/features/gui-system' },
      { text: 'Mineable Spawners', link: '/features/mineable-spawners' },
      { text: 'Shop Integration', link: '/features/shop-integration' },
      { text: 'Visual Effects', link: '/features/visual-effects' }
    ]
  }
]

const viFeaturesSidebar = [
  {
    text: 'Tính năng',
    items: [
      { text: 'Tổng quan', link: '/vi/features/' },
      { text: 'Các loại Spawner', link: '/vi/features/spawner-types' },
      { text: 'Hệ thống xếp chồng', link: '/vi/features/stacking-system' },
      { text: 'Hệ thống GUI', link: '/vi/features/gui-system' },
      { text: 'Đào Spawner', link: '/vi/features/mineable-spawners' },
      { text: 'Tích hợp cửa hàng', link: '/vi/features/shop-integration' },
      { text: 'Hiệu ứng trực quan', link: '/vi/features/visual-effects' }
    ]
  }
]

const enDeveloperSidebar = [
  {
    text: 'Developer API',
    items: [
      { text: 'Overview', link: '/developer-api/' },
      { text: 'Installation', link: '/developer-api/installation' },
      { text: 'API Creation', link: '/developer-api/creation' },
      { text: 'Data Access', link: '/developer-api/data-access' },
      { text: 'Events', link: '/developer-api/events' },
      { text: 'GUI Layout API', link: '/developer-api/gui-layout' },
      { text: 'Validation', link: '/developer-api/validation' },
      { text: 'Examples', link: '/developer-api/examples' },
      { text: 'Javadocs', link: JAVADOCS }
    ]
  }
]

const viDeveloperSidebar = [
  {
    text: 'API dành cho lập trình viên',
    items: [
      { text: 'Tổng quan', link: '/vi/developer-api/' },
      { text: 'Cài đặt', link: '/vi/developer-api/installation' },
      { text: 'Khởi tạo API', link: '/vi/developer-api/creation' },
      { text: 'Truy cập dữ liệu', link: '/vi/developer-api/data-access' },
      { text: 'Sự kiện', link: '/vi/developer-api/events' },
      { text: 'API bố cục GUI', link: '/vi/developer-api/gui-layout' },
      { text: 'Kiểm tra', link: '/vi/developer-api/validation' },
      { text: 'Ví dụ', link: '/vi/developer-api/examples' },
      { text: 'Javadocs', link: JAVADOCS }
    ]
  }
]

const enSidebar = {
  '/developer-api/': enDeveloperSidebar,
  '/features/': enFeaturesSidebar,
  '/docs/changelog': [{ text: 'Changelog', items: [{ text: 'Release History', link: '/docs/changelog' }] }],
  '/docs/': enManualSidebar
}

const viSidebar = {
  '/vi/developer-api/': viDeveloperSidebar,
  '/vi/features/': viFeaturesSidebar,
  '/vi/docs/changelog': [{ text: 'Nhật ký thay đổi', items: [{ text: 'Lịch sử phát hành', link: '/vi/docs/changelog' }] }],
  '/vi/docs/': viManualSidebar
}

export default defineConfig({
  title: 'SmartSpawner',
  description: 'High-performance GUI spawner management for modern Minecraft servers.',
  cleanUrls: true,
  lastUpdated: true,
  head: [
    ['link', { rel: 'icon', type: 'image/png', href: '/logo.png' }],
    ['link', { rel: 'apple-touch-icon', href: '/logo.png' }],
    ['meta', { property: 'og:image', content: 'https://docs.smartspawner.site/banner.png' }],
    ['meta', { property: 'og:type', content: 'website' }],
    ['meta', { name: 'twitter:card', content: 'summary_large_image' }],
    ['meta', { name: 'twitter:image', content: 'https://docs.smartspawner.site/banner.png' }]
  ],
  themeConfig: {
    logo: '/logo.png',
    externalLinkIcon: true,
    socialLinks: [
      { icon: { svg: MODRINTH_ICON }, link: MODRINTH, ariaLabel: 'Modrinth' },
      { icon: 'github', link: REPO },
      { icon: 'discord', link: DISCORD }
    ],
    search: {
      provider: 'local'
    }
  },
  locales: {
    root: {
      label: 'English',
      lang: 'en',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/', activeMatch: '^/$' },
          { text: 'Docs', link: '/docs/', activeMatch: '^/docs/(?!changelog)' },
          { text: 'Features', link: '/features/', activeMatch: '^/features/' },
          { text: 'Developer API', link: '/developer-api/', activeMatch: '^/developer-api/' },
          { component: 'LanguageDropdown' }
        ],
        sidebar: enSidebar,
        editLink: {
          pattern: 'https://github.com/OpenVdra/SmartSpawner/edit/main/docs/:path',
          text: 'Edit this page on GitHub'
        },
        outline: {
          level: [2, 3],
          label: 'On this page'
        },
        docFooter: {
          prev: 'Previous page',
          next: 'Next page'
        },
        lastUpdated: {
          text: 'Last updated',
          formatOptions: {
            dateStyle: 'medium',
            timeStyle: 'short'
          }
        }
      }
    },
    vi: {
      label: 'Tiếng Việt',
      lang: 'vi',
      description: 'Quản lý spawner hiệu năng cao bằng GUI dành cho máy chủ Minecraft hiện đại.',
      themeConfig: {
        nav: [
          { text: 'Trang chủ', link: '/vi/', activeMatch: '^/vi/$' },
          { text: 'Tài liệu', link: '/vi/docs/', activeMatch: '^/vi/docs/(?!changelog)' },
          { text: 'Tính năng', link: '/vi/features/', activeMatch: '^/vi/features/' },
          { text: 'API lập trình', link: '/vi/developer-api/', activeMatch: '^/vi/developer-api/' },
          { component: 'LanguageDropdown' }
        ],
        sidebar: viSidebar,
        editLink: {
          pattern: 'https://github.com/OpenVdra/SmartSpawner/edit/main/docs/:path',
          text: 'Chỉnh sửa trang này trên GitHub'
        },
        outline: {
          level: [2, 3],
          label: 'Trên trang này'
        },
        docFooter: {
          prev: 'Trang trước',
          next: 'Trang sau'
        },
        lastUpdated: {
          text: 'Cập nhật lần cuối',
          formatOptions: {
            dateStyle: 'medium',
            timeStyle: 'short'
          }
        },
        returnToTopLabel: 'Về đầu trang',
        sidebarMenuLabel: 'Menu',
        darkModeSwitchLabel: 'Giao diện',
        lightModeSwitchTitle: 'Chuyển sang giao diện sáng',
        darkModeSwitchTitle: 'Chuyển sang giao diện tối'
      }
    }
  }
})
