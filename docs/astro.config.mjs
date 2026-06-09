// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';

// https://astro.build/config
export default defineConfig({
	site: 'https://docs.smartspawner.site',
	integrations: [
		starlight({
			title: 'SmartSpawner',
			customCss: [
				'./src/styles/custom.css',
			],
			description: 'A customizable spawner GUI plugin that generates mob drops and experience directly, without spawning mobs',
			favicon: './Spawner.png',
			disable404Route: true,
			logo: {
				src: './src/assets/Spawner.png',
				alt: 'SmartSpawner',
			},
			tableOfContents: {
				minHeadingLevel: 2,
				maxHeadingLevel: 2,
			},
			social: [
				{ icon: 'github', label: 'GitHub', href: 'https://github.com/NighterDevelopment/SmartSpawner' },
				{ icon: 'discord', label: 'Discord', href: 'https://dsc.gg/nighterdevelopment' },
			],
			components: {
				Footer: './src/overrides/Footer.astro',
			},
			sidebar: [
				{
					label: 'Getting Started',
					items: [
						{ label: 'Installation', slug: 'installation' },
						{ label: 'Features Overview', slug: 'features' },
					],
				},
				{
					label: 'User Guide',
					items: [
						{ label: 'Commands', slug: 'commands' },
						{ label: 'Permissions', slug: 'permissions' },
					],
				},
				{
					label: 'Configuration',
					items: [
						{ label: 'config.yml', slug: 'configuration' },
						{ label: 'GUI Layouts', slug: 'gui_layout' },
						{ label: 'spawners_settings.yml', slug: 'spawners_settings' },
						{ label: 'item_spawners_settings.yml', slug: 'item_spawners_settings' },
					],
				},
				{
					label: 'Integrations',
					items: [
						{ label: 'Plugin Compatibility', slug: 'plugin-compatibility' },
						{
							label: 'Setup Guides',
							collapsed: false,
							items: [
								{ label: 'MythicMobs', slug: 'integrations/mythicmobs' },
								{ label: 'SuperiorSkyblock2', slug: 'integrations/superiorskyblock2' },
								{ label: 'SimpleClaimSystem', slug: 'integrations/simpleclaimsystem' },
								{ label: 'HuskClaims', slug: 'integrations/huskclaims' },
							],
						},
					],
				},
				{
					label: 'Developer API',
					collapsed: false,
					items: [
						{ label: 'API Installation', slug: 'developer-api/api-installation' },
						{ label: 'API Events', slug: 'developer-api/api-events' },
						{
							label: 'API Methods',
							collapsed: false,
							items: [
								{ label: 'Creation Methods', slug: 'developer-api/api-creation' },
								{ label: 'Validation Methods', slug: 'developer-api/api-validation' },
								{ label: 'Spawner Data Methods', slug: 'developer-api/api-data-access' },
								{ label: 'Methods Examples', slug: 'developer-api/api-examples' },
							],
						},
					],
				},
			],
		}),
	],
});
