// @ts-check
import { defineConfig } from 'astro/config';
import starlight from '@astrojs/starlight';
import en from './src/i18n/en.json' with { type: 'json' };
import ru from './src/i18n/ru.json' with { type: 'json' };

const repo = 'https://github.com/AetherMagee/MaterialXray';
// A sidebar label in every locale, from the same string files as the landing page.
const label = (/** @type {keyof typeof en.sidebar} */ key) => ({
	label: en.sidebar[key],
	translations: { ru: ru.sidebar[key] },
});

export default defineConfig({
	site: 'https://materialxray.app',
	integrations: [
		starlight({
			title: 'Material Xray',
			description: 'An Android proxy client powered by Xray-core, with a Material Design 3 interface.',
			logo: { light: './src/assets/icon.svg', dark: './src/assets/icon-monochrome.svg', alt: 'Material Xray' },
			favicon: '/favicon.svg',
			defaultLocale: 'root',
			locales: {
				root: { label: 'English', lang: 'en' },
				ru: { label: 'Русский', lang: 'ru' },
			},
			social: [{ icon: 'github', label: 'GitHub', href: repo }],
			customCss: ['./src/styles/tokens.css', './src/styles/docs.css'],
			head: [
				// The docs have their own dark theme, so Dark Reader stays off.
				{ tag: 'meta', attrs: { name: 'darkreader-lock' } },
				{ tag: 'meta', attrs: { property: 'og:image', content: 'https://materialxray.app/og.jpg' } },
				{ tag: 'meta', attrs: { property: 'og:image:width', content: '1200' } },
				{ tag: 'meta', attrs: { property: 'og:image:height', content: '630' } },
				{ tag: 'link', attrs: { rel: 'preconnect', href: 'https://fonts.googleapis.com' } },
				{ tag: 'link', attrs: { rel: 'preconnect', href: 'https://fonts.gstatic.com', crossorigin: true } },
				{
					tag: 'link',
					attrs: {
						rel: 'stylesheet',
						href: 'https://fonts.googleapis.com/css2?family=Google+Sans+Code:wght@400;500&family=Google+Sans+Flex:opsz,wght@6..144,400..800&family=Roboto+Flex:opsz,wght@8..144,400..800&family=Roboto+Mono:wght@400;500&display=swap',
					},
				},
			],
			expressiveCode: {
				themes: ['github-dark-default', 'github-light-default'],
				styleOverrides: {
					borderRadius: '16px',
					borderColor: 'var(--md-outline-variant)',
					codeFontFamily: 'var(--md-font-code)',
					uiFontFamily: 'var(--md-font)',
					codeBackground: 'var(--md-surface-container-low)',
					frames: {
						editorTabBarBackground: 'var(--md-surface-container)',
						editorActiveTabBackground: 'var(--md-surface-container-low)',
						terminalTitlebarBackground: 'var(--md-surface-container)',
						terminalBackground: 'var(--md-surface-container-low)',
						frameBoxShadowCssValue: 'none',
					},
				},
			},
			sidebar: [
				{
					...label('startHere'),
					items: [
						{ ...label('introduction'), slug: 'docs' },
						'docs/guide/installation',
						'docs/guide/adding-servers',
						'docs/guide/root-vs-rootless',
					],
				},
				{
					...label('usingTheApp'),
					items: [
						'docs/guide/per-app-proxy',
						'docs/guide/routing',
						'docs/guide/settings',
						'docs/guide/troubleshooting',
					],
				},
				{
					...label('forProviders'),
					items: [
						{ ...label('overview'), slug: 'docs/providers' },
						'docs/providers/subscription-format',
						'docs/providers/request-headers',
						'docs/providers/response-headers',
						'docs/providers/routing-header',
						'docs/providers/per-app-routing',
						'docs/providers/deeplinks',
					],
				},
				{
					...label('reference'),
					items: ['docs/reference/share-links', 'docs/reference/faq'],
				},
			],
		}),
	],
});
