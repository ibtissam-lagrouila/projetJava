# Fitwell Design Tokens

Extracted and sampled directly from the 20 screens of `Fitwell_-_Student_desktop_app.pdf`.

## 1. Color Palette

### Primary / Brand
- `--color-sidebar`: `#5B1421` (Deep burgundy maroon - used in navigation sidebars, sign-in right hero panel, notice banners)
- `--color-sidebar-active`: `#721B2B` (Slightly lighter maroon for active menu items)
- `--color-sidebar-text`: `#F5EFEB` (Warm off-white for sidebar navigation text)
- `--color-sidebar-muted`: `#BCA29F` (Muted pink-beige for inactive sidebar icons/counters)

### Canvas & Backgrounds
- `--color-bg-page`: `#D3B996` (Signature warm sand/camel desktop backdrop)
- `--color-bg-card`: `#E2CEB0` (Soft warm card background)
- `--color-bg-card-hover`: `#DBC5A5` (Interactive card hover state)
- `--color-bg-input`: `#DFCAAC` (Form field input background with subtle dark border)
- `--color-bg-tag`: `#ECD9C2` (Skills pills, tags, chips)

### Brand Accent / Actions
- `--color-btn-primary`: `#791E2D` (Primary action button, deep wine)
- `--color-btn-primary-hover`: `#631724` (Hover on primary buttons)
- `--color-btn-secondary`: `#E8D6BF` (Secondary / outline button fill)
- `--color-btn-secondary-border`: `#8A3D49`
- `--color-match-circle`: `#5B1421` (Circle stroke for score indicator, e.g. 96%)

### Semantic & Status
- `--color-status-healthy`: `#2D6A4F` (Green for healthy sources / success)
- `--color-status-warning`: `#9C6615` (Amber/gold for warnings)
- `--color-status-failing`: `#A31D1D` (Crimson for failing jobs/rejected items)
- `--color-badge-draft`: `#8F7E6D` (Gray-brown)
- `--color-badge-sent`: `#3D5A80` (Muted slate)
- `--color-badge-shortlisted`: `#B07D38` (Gold-bronze)
- `--color-badge-interview`: `#791E2D` (Fitwell wine)
- `--color-badge-decision`: `#2D6A4F` (Forest green)

### Typography Colors
- `--color-text-main`: `#241814` (Deep espresso brown, high contrast on cards)
- `--color-text-muted`: `#66554D` (Medium brown for subtitles, dates, secondary labels)
- `--color-text-light`: `#F5EFEB` (Off-white for dark backgrounds)

---

## 2. Typography

### Font Families
- **Editorial Headings**: `'Newsreader', 'Playfair Display', 'Georgia', serif`
  - Used for: Screen titles (`"Find the lab that will shape your future"`, `"Good morning, Yassine"`, `"Tell Fitwell what you want"`), section headers, and card titles.
- **UI & Body**: `'Plus Jakarta Sans', 'Inter', -apple-system, BlinkMacSystemFont, sans-serif`
  - Used for: Navigation labels, buttons, input fields, badges, score percentages, metadata labels.

### Font Scales
- `text-display`: `2.75rem` (44px), line-height: `1.15`, weight: `400` (Serif)
- `text-h1`: `2.125rem` (34px), line-height: `1.2`, weight: `400` (Serif)
- `text-h2`: `1.5rem` (24px), line-height: `1.25`, weight: `500` (Serif)
- `text-h3`: `1.125rem` (18px), line-height: `1.3`, weight: `600` (Sans/Serif)
- `text-body`: `0.9375rem` (15px), line-height: `1.5`, weight: `400` (Sans)
- `text-small`: `0.8125rem` (13px), line-height: `1.4`, weight: `500` (Sans)
- `text-micro`: `0.6875rem` (11px), line-height: `1.2`, weight: `600` (Sans, uppercase letter-spacing)

---

## 3. Radii, Spacing & Elevation

### Border Radii
- `--radius-sm`: `6px` (Small badges, tags, pills)
- `--radius-md`: `10px` (Input fields, standard buttons)
- `--radius-lg`: `18px` (Content cards, modal sheets)
- `--radius-xl`: `24px` (Large container cards, e.g., Onboarding box, Hero cards)
- `--radius-pill`: `9999px` (Avatars, score circles, tags like `"Medical imaging"`)

### Spacing & Layout
- Sidebar Width: `240px` fixed.
- Main Workspace: Flex 1, max container padding `32px`.
- Card Inner Padding: `24px` (standard), `16px` (compact list cards).
- Grid Gaps: `16px` to `24px`.

### Shadows & Borders
- Border: `1px solid rgba(80, 50, 30, 0.12)`
- Card Shadow: `0 2px 8px rgba(45, 25, 15, 0.04)`
- Focus Ring: `2px solid #791E2D` with `offset 2px`

