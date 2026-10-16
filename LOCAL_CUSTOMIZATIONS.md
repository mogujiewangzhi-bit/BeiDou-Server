# Local Server And Admin Customizations

This branch is a server and admin frontend source snapshot based on BeiDouMS/BeiDou-Server
commit `593b34a16550913337699b711d391ecd524ef877`, captured on 2026-10-16.
It retains the upstream history and AGPL-3.0 license.

## Included

- GM blessing composition and action-unlock fix.
- GM super-character command, career selection menu, class-specific attributes,
  equipment, inventory expansion, supplies, backup and restore support.
- Offline administrator resource grants and database migrations.
- Character attribute and skill management APIs, and shortcut policy.
- Server-side localization changes.
- Current custom GM NPC menus and three direct Free Market merchant scripts.
- Free Market merchant catalog and related unit tests.
- Admin frontend character management, resource and inventory panels, skill
  management, and searchable character selectors used by existing workflows.
- Admin image-repair and cash-package name/content overlays, retained as
  deployment source files in `gms-ui/deployment-overlays`.

## Scope And Limitations

This is a source snapshot, not an export of a running game installation.
Player/account tables, database dumps, credentials, local configuration,
client binaries, generated JARs, logs and backups are not included.
The original upstream application configuration is retained; replace its
example credentials and signing secret before deployment.

The local admin frontend source is now included. Installed dependencies and
compiled frontend bundles are not included. The upstream environment examples
are retained; local environment files are not copied from the running setup.

The independent Super Spear Crusher experiment is still in progress and is
not part of this published snapshot. No claim of game-client compatibility
or completed gameplay verification is made for that experiment.

Free Market shops 880201/880202/880203 use the native database shop mechanism.
Their NPC scripts and item catalog are included, but existing runtime shop
rows and persistent NPC placement rows are not copied from the live database.
Configure those rows from the catalog when deploying to a fresh server.
Do not import an unrelated production database to obtain shop definitions.

## Verification

This snapshot compiled successfully with Java 21. The selected test run
completed with 167 tests, zero failures, zero errors and zero skipped tests.
The changed/new files were also checked for local runtime credentials,
private-key/token patterns, user-specific references and generated artifacts.

Java 21 is required. The selected tests are:

```text
GmBlessingTest,GmBlessingActionTest,SuperCharacterTest,SuperCharacterSuppliesTest,
CharacterAdminServiceTest,CharacterSkillAdminServiceTest,OfflineGrantServiceTest,
SkillShortcutPolicyTest
```

`GmBlessingTest` expects upstream `gms-server/wz/Skill.wz` files.
`SuperCharacterTest` additionally requires a WZ XML fixture directory through
`-Dsuperchar.resources=<fixture-directory>` containing `Skill.wz` and
`Character.wz`. Fixtures are ordinary equipment and skill data, not player data.
Do not run the upstream database code-generation tests against a production
database as part of routine CI.

Database integration probes and real game-client purchase/combat workflows
are separate from these unit tests. A successful unit test run does not
establish end-to-end gameplay correctness.

## Admin Frontend

The frontend is built from `gms-ui` using its existing `build` script, which
runs `vue-tsc --noEmit` followed by the production Vite build. Reuse the lockfile
and install dependencies before building on a new machine.

The captured frontend passed the type check and production build. The existing
image optimizer reported warnings for two PNG files; both original images were
still emitted. Existing eval and duplicate CSS-output warnings were not changed
as part of this source publication. No frontend bundle was deployed to the
running server by this upload task.

The runtime overlay files are separate from the Vue build. To reproduce the
existing overlay deployment, copy the three files from
`gms-ui/deployment-overlays/assets` to the deployed frontend's `assets` directory
and include these scripts before the main application module:

```html
<script src="/assets/icon-repair.js"></script>
<script src="/assets/package-names.js"></script>
```

The package-name overlay reads `assets/package-catalog.json`. The image overlay
uses the public maplestory.io service and still requires access to that service.
These are source snapshots, not an export of live account or player data.
