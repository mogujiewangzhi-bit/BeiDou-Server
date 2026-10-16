# Admin Runtime Overlays

These source files preserve the existing deployment's icon recovery and
cash-package name/content display. They are independent of the Vue bundle.

Copy `assets/icon-repair.js`, `assets/package-names.js`, and
`assets/package-catalog.json` into the deployed frontend's `assets` directory.
Load the two scripts before the application module in its HTML entry point.
The source snapshot's root `LOCAL_CUSTOMIZATIONS.md` contains the script tags.

The catalog contains game package/item definitions, not accounts, characters,
transactions, passwords, or other player data. No generated frontend bundles
are included here.
