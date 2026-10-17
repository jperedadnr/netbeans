# nbfx — a pure-JavaFX IDE frontend on the NetBeans runtime

This repository hosts the **nbfx** work: a **pure-JavaFX** re-implementation of the NetBeans IDE
shell. It reuses the headless NetBeans platform/IDE services (project model, parsing, java.source,
indexing, autoupdate, JPDA, templates, …) and replaces every Swing view/component with a JavaFX one.
There is no NetBeans Swing window: `core.windows` is dropped from the nbfx clusters and the shell is
owned by the JavaFX application.

The full roadmap, milestones and history live in **[`docs/MVP.md`](docs/MVP.md)** (the canonical
plan). This file is the short project overview.

## Branch

- Integration branch: **`nbfx/mvp-phase2`** (based on `netbeansfx-sep26`).
- `nbfx/mvp-phase1` is the earlier line; `nbfx/mvp-phase2` is intended to replace it.

## Clusters

The FX modules are split into three clusters that parallel the NetBeans clusters, so a new FX module
goes next to the NetBeans module it replaces. Code-name-bases are unchanged; only the cluster/dir
layout differs. Dependency direction is `platform → ide → java`.

| Cluster | Dir | Contents |
|---|---|---|
| `nb.cluster.nbfx-platform` | `nbfx-platform/` | FX replacements for platform-level modules: `annotations`, `lib.javafx`, `api`, `context`, `docking`, `windows`, `statusbar`, `menus`, `macos`, `output`, `options`, `wizard`, `actions`. |
| `nb.cluster.nbfx-ide` | `nbfx-ide/` | FX replacements for ide-level modules + the app shell: `editor`, `editor.actions`, `file.actions`, `findinprojects`, `findusages`, `propertysheet`, `structure`, `structure.xml`, `structure.css`, `project`, `project.ui`, `project.ui.api`, `project.actions`, `project.context`, `project.customizer`, `plugins`, `vcs`, `debug`, `run`, `ui`, `launcher`. |
| `nb.cluster.nbfx-java` | `nbfx-java/` | FX replacements for java/extide-level modules: `project.ui.java`, `structure.java`, `structure.maven`, `structure.gradle`, `project.ui.maven`, `project.ui.gradle`, `project.ui.ant`, `build.ant`. |

## Build and test

The clusters are built as part of the NetBeans build; the lean set the application runs on is
`clusters.config.nbfx.list` (platform + the three nbfx clusters + their dependencies).

```sh
# Build the nbfx clusters (incremental; ~1.5 min once warm)
cd nbbuild && ant -Dcluster.config=nbfx build -q

# Purity/guardrail checks (forbidden Swing/AWT imports & deps, bundled classes,
#   and no core.windows jar in the distribution)
ant -f check-purity.xml check-purity

# Unit tests of a module (run from the repository root)
ant -f <cluster-dir>/<module-dir>/build.xml test-unit -Dnetbeans.dest.dir="$PWD/nbbuild/netbeans"
```

Run the application with `ant tryme` from `nbbuild` (it launches on the lean nbfx cluster set).

## Architecture in a nutshell

- **Registration is layer-driven.** Annotations on the owning class emit `NbFx/*` layer entries; the
  shell builds menus, tool bars, views, context menus, status elements, wizards and options from that
  layer (`@FxActionReference` references an action by id; `mimeType` restricts a Navigator context
  menu entry to a content type).
- **Global action context = the active component's lookup.** `NbfxGlobalContextProvider` (a
  `ContextGlobalProvider`, `position = 100`) feeds the nbfx selection into
  `Utilities.actionsGlobalContext()` via a stable `Lookups.proxy`. The shell publishes the active
  **component** — a tool view *or* an editor (`ActiveComponentContext`) — from the focused pane's
  selected tab; editors win over the follower-view fallback.
- **Navigator is service-based.** Structure/Navigator panels implement `FxStructurePanel`
  (`contentTypes()`), registered with `@ServiceProvider` and resolved by the active file's MIME;
  `nbfx.structure.java` ports the full Java Navigator (members, bean patterns, go-to);
  `nbfx.structure.xml`, `nbfx.structure.css`, `nbfx.structure.maven` and `nbfx.structure.gradle`
  the XML View, the CSS navigator, the POM model / POM inheritance / Related goals panels and the
  Gradle Related tasks panel.
- **Views observe the open-project model.** Navigators are plain `ViewProvider`s (the
  `@FxViewRegistration(navigator = true)` flag is the marker) that observe `ProjectRegistry` and
  build/drop their trees; they publish their own selection through `ViewProvider.getLookup()`.
- **Purity.** `nbfx` sources/JARs must not import Swing/AWT/legacy-window packages; the macOS menu and
  one headless accessibility SPI type are the only quarantined exceptions. `core.windows` is excluded
  from the clusters and a packaging guard (in `check-purity.xml`) asserts no `core.windows` jar ships
  in the three nbfx clusters.

## Known caveats

- GUI behaviour is verified manually with `ant tryme`; most modules have no automated GUI tests.
- On macOS, running with `-Djava.awt.headless=true` hits a known `HeadlessException` in the launcher
  (an environment artifact, not a code path).
