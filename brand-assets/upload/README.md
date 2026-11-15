# Bilan brand assets

Built from the supplied Bilan artwork (B monogram with gold dot, and the "Bilan" wordmark). Start with `brand-guidelines/brand-guidelines.pdf`, then `INVENTORY.md` (every file with dimensions, transparency and use).

## Colours
| Name | HEX | RGB |
|---|---|---|
| Forest (primary) | `#31413A` | 49, 65, 58 |
| Cream (primary) | `#F3EEE3` | 243, 238, 227 |
| Gold (accent) | `#D5A757` | 213, 167, 87 |

Sampled from the supplied images. Forest Deep, Sage and Paper in the guidelines are proposed supporting neutrals, not part of the supplied art.

## Naming
- `-dark` = dark artwork (Forest + Gold) for **light** backgrounds.
- `-light` = light artwork (Cream + Gold) for **dark** backgrounds.
- `mono` = single colour, dot included.
- `small-size` = optical cut of the symbol with heavier hairlines, for sizes under 48 px.

## Folders
- `logo/` master vectors (SVG) and PNGs: horizontal, stacked, symbol, mono.
- `android/` adaptive icon layers, Play Store icon, legacy icon, splash artwork, notification icon, and `res/` with per-density files plus XML.
- `app-ui/` header logo and compact symbol, light and dark.
- `web/` favicons, apple touch icon, manifest icons, SVG symbol, social previews.
- `exports/` report header and footer, watermark, monochrome, sharing and certificate logos.
- `widgets/` widget symbols and small monochrome marks.
- `play-store/` feature graphic. `screenshots/` is intentionally empty (see its note).
- `brand-guidelines/` PDF, palette image, typography spec.

## Android quick start
1. Copy `android/res/*` into `app/src/main/res/` (merge with existing folders).
2. The adaptive icon is `@mipmap/ic_launcher` and already references foreground, solid Forest background, and the monochrome layer.
3. Splash: put `splash-logo-light-mode.png` in `drawable` and `splash-logo-dark-mode.png` in `drawable-night`, both named `splash_logo`. Colours are in `values` and `values-night`. See `themes_splash_example.xml`.
4. Notifications: use `@drawable/ic_stat_bilan` and set the accent colour to `bilan_forest`.

## Things to know
- **Vector provenance.** The SVGs are traces of the supplied raster images (which look AI-generated), with the gold dot rebuilt as a true circle. Curves are clean, but a type designer should redraw and optimise them before a trademark filing or large-format print. Confirm you hold the rights to the original artwork.
- **Wordmark.** The horizontal logo is the wordmark alone; the B monogram is the symbol.
- **Small sizes.** The hairline in the B disappears below roughly 48 px, so favicons, themed icons and notification icons use the heavier small-size cut.
- **Screenshots.** None were supplied, so none were made. Send real screens and they can be framed for Play Store.
- **Fonts.** Cormorant Garamond and Inter (both OFL 1.1) are recommendations; font files are not bundled.
- The supplied icon tiles and previews (including the Gemini watermark) are not part of this package.
