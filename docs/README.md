# SmartSpawner Docs

Documentation site for [SmartSpawner](https://github.com/OpenVdra/SmartSpawner), built with [VitePress](https://vitepress.dev/).

## Structure

```
docs/
├── .vitepress/
│   ├── components/        # Vue components used in markdown
│   │   ├── card/          # DocCard, CardGrid, FeatureCard
│   │   ├── config/        # ConfigGroup, ConfigProperty
│   │   ├── home/          # Contributors
│   │   └── table/         # CommandRow, PermCommandRow, PermRow, BaseTable
│   ├── theme/             # Custom theme overrides (style.css, index.js)
│   └── config.mts         # VitePress config (nav, sidebar, locales)
├── docs/                  # All documentation pages
│   ├── commands.md
│   ├── configuration.md
│   ├── features.md
│   ├── gui-layout.md
│   ├── installation.md
│   ├── item-spawners-settings.md
│   ├── permissions.md
│   ├── spawners-settings.md
│   └── developer-api/
├── public/                # Static assets (logo, favicon)
├── index.md               # Home page
└── package.json
```

## Development

```bash
npm install
npm run docs:dev      # Start dev server at http://localhost:5173
npm run docs:build    # Build for production → dist/
npm run docs:preview  # Preview production build
```

## Custom Components

All components are auto-imported via the VitePress theme.

| Component | Usage |
|---|---|
| `<CommandRow>` | Display a command with its permission node |
| `<PermCommandRow>` | Compact command + permission table row |
| `<PermRow>` | Standalone permission row |
| `<ConfigProperty>` | Config key with type, default, description |
| `<ConfigGroup>` | Groups multiple ConfigProperty entries |
| `<DocCard>` | Link card for navigation |
| `<CardGrid>` | Grid layout for DocCards |

## Contributing

Edit the relevant `.md` file under `docs/` and open a PR. To add new pages, register them in `.vitepress/config.mts` under `themeConfig.sidebar`.
