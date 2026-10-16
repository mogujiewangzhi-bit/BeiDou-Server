# Local Server Customizations

This branch is a server-side source snapshot based on BeiDouMS/BeiDou-Server
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

## Scope And Limitations

This is a source snapshot, not an export of a running game installation.
Player/account tables, database dumps, credentials, local configuration,
client binaries, generated JARs, logs and backups are not included.
The original upstream application configuration is retained; replace its
example credentials and signing secret before deployment.

The existing upstream frontend is retained. Local frontend modifications
are outside this server-source upload and are not included.

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
