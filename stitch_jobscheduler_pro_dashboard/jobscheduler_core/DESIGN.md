---
name: JobScheduler Core
colors:
  surface: '#0b1326'
  surface-dim: '#0b1326'
  surface-bright: '#31394d'
  surface-container-lowest: '#060e20'
  surface-container-low: '#131b2e'
  surface-container: '#171f33'
  surface-container-high: '#222a3d'
  surface-container-highest: '#2d3449'
  on-surface: '#dae2fd'
  on-surface-variant: '#c7c4d7'
  inverse-surface: '#dae2fd'
  inverse-on-surface: '#283044'
  outline: '#908fa0'
  outline-variant: '#464554'
  surface-tint: '#c0c1ff'
  primary: '#c0c1ff'
  on-primary: '#1000a9'
  primary-container: '#8083ff'
  on-primary-container: '#0d0096'
  inverse-primary: '#494bd6'
  secondary: '#89ceff'
  on-secondary: '#00344d'
  secondary-container: '#00a2e6'
  on-secondary-container: '#00344e'
  tertiary: '#ffb783'
  on-tertiary: '#4f2500'
  tertiary-container: '#d97721'
  on-tertiary-container: '#452000'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e1e0ff'
  primary-fixed-dim: '#c0c1ff'
  on-primary-fixed: '#07006c'
  on-primary-fixed-variant: '#2f2ebe'
  secondary-fixed: '#c9e6ff'
  secondary-fixed-dim: '#89ceff'
  on-secondary-fixed: '#001e2f'
  on-secondary-fixed-variant: '#004c6e'
  tertiary-fixed: '#ffdcc5'
  tertiary-fixed-dim: '#ffb783'
  on-tertiary-fixed: '#301400'
  on-tertiary-fixed-variant: '#703700'
  background: '#0b1326'
  on-background: '#dae2fd'
  surface-variant: '#2d3449'
typography:
  display-lg:
    fontFamily: Geist
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  title-sm:
    fontFamily: Geist
    fontSize: 18px
    fontWeight: '600'
    lineHeight: 24px
  body-md:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  body-sm:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '400'
    lineHeight: 16px
  mono-label:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.02em
  headline-lg-mobile:
    fontFamily: Geist
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base_grid: 4px
  container_margin: 24px
  gutter: 16px
  section_gap: 32px
  sidebar_width: 260px
  sidebar_collapsed: 64px
---

## Brand & Style

The design system is engineered for high-performance distributed systems management. It targets DevOps engineers and backend developers who require a high-density, low-latency visual interface that remains legible during long monitoring sessions.

The aesthetic follows a **Modern Corporate** style with a **Technical/Minimalist** edge. It leverages a "Dark Slate" foundation to reduce eye strain, punctuated by high-vibrancy primary actions and status indicators. The visual language is defined by precision: razor-sharp alignment, subtle micro-interactions, and a clear information hierarchy that prioritizes data over decoration. The emotional response should be one of control, reliability, and technical sophistication.

## Colors

The palette is optimized for a technical dashboard environment. 

- **Primary (Indigo):** Used for main action buttons, active sidebar states, and primary focal points.
- **Surface Tones:** A deep "Slate" spectrum is used to differentiate layout layers. The base background is nearly black to provide maximum contrast for data.
- **Status Semantic Colors:** These are non-negotiable for system health. 
    - **Emerald:** Indicates completed jobs or healthy nodes.
    - **Amber:** Indicates queued tasks or CPU throttling.
    - **Blue:** Indicates active processing/running states.
    - **Rose:** Indicates failed jobs, timeouts, or offline workers.
- **Neutral/Borders:** High-precision borders use `Slate-800` to define sections without adding visual noise.

## Typography

This design system utilizes a trio of typefaces to maximize clarity:
- **Geist** for headlines and structural navigation to provide a modern, technical feel.
- **Inter** for all body text, descriptions, and data inputs due to its exceptional legibility at small sizes.
- **JetBrains Mono** for specialized data points such as Job IDs, CRON expressions, and log outputs.

Scale large headlines down on mobile devices using the provided mobile tokens. Always use `mono-label` for technical metadata to distinguish it from instructional text.

## Layout & Spacing

The layout is built on a **12-column fluid grid** system.

- **Desktop:** 24px outer margins with 16px gutters between cards.
- **Sidebar:** A persistent left-hand navigation. It should be collapsible to an icon-only state to maximize data workspace.
- **Density:** The design system favors a "Compact" density model. Use 8px (2 units) for internal component padding and 16px (4 units) for card padding.
- **Responsive Behavior:** Below 1024px, the sidebar transitions to an overlay drawer. Below 768px, the 12-column grid collapses into a single-column stack.

## Elevation & Depth

Depth is communicated through **Tonal Layering** and **Subtle Outlines** rather than heavy shadows.

1.  **Level 0 (App Base):** `#020617` (Blackest Slate).
2.  **Level 1 (Cards/Sidebar):** `#0F172A` (Deep Slate) with a 1px solid border of `#1E293B`.
3.  **Level 2 (Modals/Popovers):** `#1E293B` with a soft 15% opacity black shadow (0px 10px 25px).

Use "Glassmorphism" sparingly: only for the persistent Header or Sidebar background blurs (12px blur, 80% opacity) to maintain context of the content scrolling beneath.

## Shapes

The shape language is "Soft" yet professional. 

- **Components:** Buttons, inputs, and cards use a 0.25rem (4px) corner radius to maintain a precise, engineered appearance.
- **Badges/Status:** These are the only exception, using a full "Pill" shape (999px) to distinguish them from interactive buttons.
- **Iconography:** Icons should use a 1.5pt or 2pt stroke weight with slightly rounded caps to match the typography.

## Components

- **Buttons:** 
    - *Primary:* Indigo background, white text.
    - *Secondary:* Ghost style, Slate-800 border, Slate-200 text.
- **Status Badges:** Pill-shaped. Use a 10% opacity background of the status color with a 100% opacity text color (e.g., light green text on dark green tint).
- **Data Tables:** 
    - No vertical borders. 
    - 1px Slate-800 horizontal dividers only.
    - Row hover state: background change to `#1E293B`.
    - Header text: `mono-label` in uppercase with Slate-400 color.
- **Input Fields:** Dark background (`#020617`), 1px Slate-700 border. On focus, the border transitions to Primary Indigo with a 2px outer glow.
- **Cards:** Used to wrap logical groups of data (e.g., "Active Workers"). Should include a title header with a bottom divider.
- **Charts:** Use minimalist sparklines for "Job Success Rate." Ensure chart lines use the semantic status colors without fills to keep the UI clean.