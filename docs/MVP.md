# NetBeansFX MVP Plan

A plan to build a full Java IDE MVP on a **pure-JavaFX** frontend for NetBeans, reusing as much
headless platform/IDE infrastructure as possible while replacing every Swing view/component and
preserving NetBeans paradigms (central Lookup, Context, layer/annotation registration).

Status: draft for execution. All architectural decisions below are locked unless noted.

---

## 1. Objective and locked decisions

Build an MVP Java IDE with a **pure-JavaFX UI** on the NetBeans runtime, reusing only headless
services and substituting every view/component with a JavaFX equivalent.

| Area | Decision |
|---|---|
| Purity | **UI/component purity** — zero Swing/AWT/bridges in `nbfx`; reused modules may contain unused Swing |
| MVP feature set | Open/edit/**build/run Maven**; New File/Project; minimal Options |
| Project types | Maven (reference), **Gradle**, **Ant** via one generic path |
| Window system | Pragmatic **nbfx `View`/`Mode` SPI** (do not implement `org.openide.windows`) |
| Registration | Reuse the **layer substrate**; ship **nbfx annotation processors** |
| Distribution | **Lean `nbfx` cluster immediately** + allow/deny guardrail |
| Semantic engine | Reuse `java.source.base` + `parsing` |
| Project engines | **nbfx-native** over Maven Resolver + `maven-model`, Gradle Tooling API, Ant |
| Plugins/Modules UI | Reuse headless **`autoupdate.services`**; exclude `autoupdate.ui` |
| Options SPI | **nbfx-native `FxOptions` SPI** (own namespace) |
| SPI maturity | **"Under development" until MVP**, then freeze |
| Docs/versioning | Follow **NetBeans conventions** (`apichanges.xml`, `arch.xml`, spec versions, sigtest) |
| Action context | **View-tied `FxActionContext`** in the global Lookup; commands bind enablement to it (§17) |
| Window modes | **nbfx `FxArea` descriptor** mirroring the Swing modes/layout; sliding sides done (§24) |

### MVP definition of done

Open a Maven project, edit and save Java/XML/CSS files with completion and diagnostics, build and
run the project, see output in a JavaFX console, create files/projects, and adjust a minimal set of
options — all inside the lean, pure-JavaFX app with no visible Swing UI.

---

## 2. Current state (evidence)

The branch already contains a working **JavaFX shell over the NetBeans Runtime**:

- 11 modules under `nbfx/`, ~48k LOC / 287 Java sources (excluding `build/`).
- Cluster wiring: `nbbuild/cluster.properties:99` adds `nb.cluster.nbfx` to `clusters.config.java.list`;
  `nbbuild/cluster.properties:1337-1351` defines the cluster and its dependency on `nb.cluster.java`.
- Bootstrap: `nbfx/.../launcher/FxGuiRunLevel.java` (`@ServiceProvider(service=RunLevel.class)`) starts
  `Application.launch(JavaFXLaunchApp.class)` on a daemon thread once the platform is up (the
  `core.startup` `RunLevel` sweep); `LauncherInstall` (`ModuleInstall`) keeps only the early
  `VersioningOptOut` and `manifest.mf` registers it as `OpenIDE-Module-Install`.
- The Swing main window is gone, not hidden: `core.windows` was removed from the platform cluster
  (M9.2/M9.4); `SwingWindowSuppressor` deleted.
- JavaFX is bundled as class-path extensions in `nbfx/c.gluonhq.netbeans.nbfx.lib.javafx`
  (JavaFX 27; `javafx.version`), placed under `release/modules/ext/*.jar`; **`javafx.swing` is not bundled**.
- Registration today is `@ServiceProvider` (28 occurrences) plus heavy manual wiring in
  `JavaFXLaunchApp.start()` (`:123-259`) and `ActionBars`; there are **no** `layer.xml`,
  `@ActionRegistration`, `@ActionReference`, or `org.openide.nodes` usages.

What works: window/docking (`nbfx.docking`), session persistence (`AppState`/`SessionRestorer`),
project open/close bridged to `OpenProjects` (`ProjectRegistryImpl.java:191-234`), logical+physical
navigators, a deep editor (Java/XML/CSS syntax, semantic completion, diagnostics, breadcrumbs,
in-editor search/replace), find-in-projects, find-usages, file actions.

What is missing: build/run/debug, output console, wizards, options UI, VCS, properties/inspector,
configurable keymaps, plugins UI, and any layer/annotation-based registration.

---

## 3. Purity and architecture rules

### 3.1 Hard forbidden imports/dependencies for `nbfx/*/src`

- `javax.swing.*`, `java.awt.*` (UI), `javafx.embed.swing.*`, `SwingFXUtils`, `SwingNode`, `JFXPanel`.
- Direct module deps on: `org.openide.awt`, `org.openide.nodes`, `org.openide.explorer`,
  `org.openide.windows`, `org.openide.dialogs`, `org.netbeans.core.windows`, `core.ui`,
  `core.multiview`, `org.netbeans.modules.projectuiapi.*`.

### 3.2 Reuse boundary audit (evidence)

Swing/AWT footprint of candidate reuse modules and their Swing module deps:

| Module | AWT/Swing files | Swing deps that taint the closure |
|---|---|---|
| `platform/api.io` | 0 | — (**clean**) |
| `platform/libs.javacapi` | 0 | — (**clean**) |
| `platform/api.java.classpath` | 0 | — (**clean**) |
| `java/java.source.base` | 39 | `org.openide.dialogs` |
| `java/api.java` | 8 | — |
| `java/queries` | 4 | — |
| `platform/openide.nodes` | — | `org.openide.awt`, `org.openide.dialogs` |
| `ide/projectapi` | 13 | — |
| `ide/parsing.api` | 9 | — |
| `ide/parsing.indexing` | 6 | `api.progress`, `projectuiapi.base` |
| `ide/lexer` | 4 | — |
| `platform/autoupdate.services` | 6 | `api.progress`, `openide.dialogs` |
| `java/maven.embedder` | 0 | `api.progress`, `projectuiapi.base` |
| `java/maven` (project type) | huge | `openide.awt/nodes/explorer/windows/dialogs`, `projectuiapi`, `swing.validation`, editor/options UI |

Consequences:

- **Dependency-closure purity is not feasible for the MVP** (it would require forking the entire
  NetBeans Java stack). We adopt **UI/component purity**: `nbfx` never imports/enables Swing UI,
  and every visible surface is JavaFX. Reused modules (e.g. `java.source.base`, `parsing`) may ship
  unused Swing classes.
- The Swing main-window suppression remains, **quarantined to the launcher bootstrap**.
- `java/maven` (project type UI) and `projectuiapi` are **not** used; projects are nbfx-native
  (§6).

### 3.3 Layer-generation substrate is headless

Confirmed: `platform/openide.filesystems/src/org/openide/filesystems/annotations/` contains
`LayerGeneratingProcessor.java`, `LayerBuilder.java`, `LayerGenerationException.java` — no Swing.
By contrast NetBeans' `@ActionRegistration` processor lives in the Swing-bound module
`platform/openide.awt/src/org/netbeans/modules/openide/awt/ActionProcessor.java`. Therefore we
reuse the layer substrate and write **our own** processors (§4).

---

## 4. Registration substrate — nbfx annotation processors

New module `c.gluonhq.netbeans.nbfx.annotations`:

- Depends only on `org.openide.filesystems` (headless) + `java.compiler`.
- Provides `AbstractProcessor`s extending `LayerGeneratingProcessor`, using `LayerBuilder` to emit
  `META-INF/generated-layer.xml`.
- Registers processors via `META-INF/services/javax.annotation.processing.Processor`.

| Annotation | Layer path emitted | Consumed by |
|---|---|---|
| `@FxActionRegistration` / `@FxActionReference` | `NbFx/Actions`, `NbFx/Menus/<menu>`, `NbFx/Toolbars/<bar>` | `nbfx.actions` presenters |
| `@FxViewRegistration(id, position)` | `NbFx/Views/<id>` | `nbfx.windows` |
| `@FxProjectTypeRegistration` / `@FxProjectNodeFactory` | `NbFx/Projects/<type>/*` | `nbfx.project` / `nbfx.projectview` |
| `@FxStatusRegistration(id, position, alignment)` | `NbFx/Status/<id>` | `nbfx.statusbar` |
| `@FxPropertyEditorRegistration(valueType)` | `NbFx/PropertyEditors/<type>` | `nbfx.properties` |
| `@FxOptionsRegistration` (+ container/sub) | `NbFx/Options/<cat>/<sub>` | `nbfx.options` |
| `@FxWizardRegistration(id)` | `NbFx/Wizards/<id>` | `nbfx.wizard` |

Runtime discovery uses `Lookups.forPath(...)` / `FileUtil.getConfigFile(...)`. This preserves the
"declarative registration" feel while keeping the types and consumers Swing-free.

---

## 5. Window system and lifecycle

### 5.1 `nbfx.windows`

Formalize the existing `docking` + `NbfxTabPane` into a registered window system:

- `View`, `Pane`, `Mode` SPI; `getDefaultLocation()` maps to `DockLocation`
  (`api/view/DockLocation.java:10-24`).
- `@FxViewRegistration`; `ViewManager` becomes the window-system service
  (`api/view/ViewManager.java`).
- Open/close lifecycle callbacks, session/layout persistence under the SPI.
- Remove the static hooks `NbfxTabPane.setActionBars/setNavigatorRevealer/setHomeResolver/
  setProjectSwitcher` (set from `JavaFXLaunchApp.java:156-208`) in favour of Lookup/registry.

### 5.2 Lifecycle

- FX-first host started from an ordered `OnStart`; single `Application`.
- `OnStop` / `LifecycleManager.exit()` teardown; exit confirmation
  (`CloseConfirmation`, `JavaFXLaunchApp.java:261-263`, `:331-341`); session capture/restore
  (`SessionRestorer`, `AppState`).
- The Swing main-window suppression is gone (M9.2/M9.4): `SwingWindowSuppressor` deleted and
  `core.windows` dropped from the cluster, so there is no Swing window to suppress; the stock
  `GuiRunLevel` window branch degrades to a coded benign warning and no transitive Swing module is
  needed by nbfx UI.
- Preserve the existing off-FX-thread discipline for platform project open/close
  (`ProjectRegistryImpl.java:34-40`, on macOS the FX thread is the AppKit main thread).

---

## 6. Project system and indexing

- New `nbfx.project`: `FxProject` model + native providers that implement the **headless** NetBeans
  `org.netbeans.api.project.Project` + `ProjectFactory`, exposing `Sources`, `ClassPathProvider`,
  `ActionProvider`, `ProjectOpenedHook`. This keeps `parsing`/`java.source`/indexing working
  **without** the Swing project-type UI modules.
- Engines (direct libraries):
    - **Maven**: Maven Resolver + `maven-model` (avoid `maven.embedder`, which pulls
      `api.progress`/`projectuiapi.base`).
    - **Gradle**: Gradle Tooling API.
    - **Ant**: Ant `Project`/`ProjectHelper`.
- Build/Run/Test/Clean driven by our own `ActionProvider` implementations, invoked headlessly and
  rendered in FX; generic across project types.
- Registration: `@FxProjectTypeRegistration` / `@FxProjectNodeFactory`.
- Finish known gaps:
    - Gradle subproject discovery — `ProjectKinds.java:130-135` currently logs
      "Gradle subproject discovery not yet implemented".
    - Build-output exclusions — `FileWalker.java:149` TODO for Gradle/Ant `build/`/`dist/`.
- Migration away from the current `OpenProjects` bridge (`ProjectRegistryImpl.java:191-234`) is
  optional; keep it only if the native `Project` path registers roots with the indexer equivalently.

---

## 7. Editor and semantics

- Keep the `jfx.incubator.richtext`-based editor (`CodeEditor`, `EditorBuffer`) and the `JavaSource`
  semantic bridge (`JavaSourceContext.java` builds `JavaSource.create(cpInfo, inMemoryFo)`).
- Build `ClasspathInfo` from nbfx project roots via clean `api.java.classpath`.
- Ensure AWT touchpoints in `java.source.base`/`api.java` never surface in nbfx UI (they are unused
  helper/UI classes).
- Keep existing completion (`JavaLexical/SemanticCompletionProvider`), diagnostics
  (`JavaDiagnosticAnalyzer`), breadcrumbs, mark-occurrences, and in-editor search.

---

## 8. UI surfaces (substitute every Swing component)

### 8.1 Status bar (`nbfx.statusbar`)

Replace the hardcoded `StatusBar` (`launcher/ui/StatusBar.java:27`) with a registry-driven bar.

```java
public enum FxStatusAlignment { LEFT, CENTER, RIGHT }

public interface FxStatusElement {
    String getId();
    default FxStatusAlignment alignment() { return FxStatusAlignment.LEFT; }
    default int position() { return 0; }
    javafx.scene.Node getNode();      // JavaFX component
    default void dispose() {}
}
```

- `FxStatusBar` (LEFT/CENTER/RIGHT slots) discovers elements via `Lookups.forPath("NbFx/Status")`.
- Migrate existing segments into registered elements contributed by their modules: caret
  (`nbfx.editor`), line separator (`nbfx.editor`), project (`nbfx.project`).
- Add elements: progress (fed by `nbfx.progress`), encoding, insert/overwrite, VCS branch (later).
- Keep the existing `Platform.runLater` marshalling contract; document threading in `arch.xml`.

### 8.2 Property sheet and Properties window (`nbfx.propertysheet`)

`org.openide.nodes`/`openide.explorer` are forbidden, so define a pure-FX model/view. (The module is
renamed `nbfx.properties` → `nbfx.propertysheet`; the dockable Properties window built on it is
described in §19.1.)

```java
public interface FxProperty {                     // one row
    String getName();
    String getDisplayName();
    String getShortDescription();
    Class<?> getValueType();
    Object getValue();
    void setValue(Object value);
    boolean isWritable();
    boolean isExpert();
    boolean isDefaultValue();
    javafx.beans.value.ObservableValue<Object> valueObservable();
}

public interface FxPropertySet {
    String getName();
    String getDisplayName();
    java.util.List<FxProperty> getProperties();
}

public interface FxPropertiesProvider {           // object -> sets, mirrors Node.getPropertySets()
    FxPropertySet[] getPropertySets(Object context);
}

public interface FxPropertyEditor {
    Class<?> valueType();
    javafx.scene.Node createEditor(FxProperty property);
}
```

- `FxPropertySheet` view: grouped `TreeTableView` (Name/Value columns, expert toggle, inline
  editing, validation, reset-to-default) consuming `FxProperties`.
- Editors registered by value type via `@FxPropertyEditorRegistration`; built-ins: text, boolean,
  integer/double, enum/choice, file/folder, color, font, string list.
- Contexts publish properties through Lookup (an `FxPropertiesProvider`).
- Reused for Project Properties, File Properties, and the future FXML component inspector.
- The dockable **Properties window** (FX `NbSheet` analogue) hosts the sheet, follows the global
  context (`FxActionContext`, §17.1), is closed by default, docks in the `properties` mode (§18) and
  opens from `Window ▸ IDE Tools ▸ Properties`; see §19.1.

### 8.3 Plugins / Update Center (`nbfx.plugins`)

Reuse the **headless** engine only: `org.netbeans.api.autoupdate` (in
`platform/autoupdate.services`) — `UpdateManager`, `UpdateUnit`, `UpdateElement`, `UpdateItem`,
`UpdateUnitProvider`, `UpdateUnitProviderFactory`, `OperationSupport`, `OperationContainer`,
`InstallSupport`, and the `Restarter` interface. Never import `autoupdate.ui`.

- Pure-FX Plugins **View** (`@FxViewRegistration`) with tabs: Installed / Available / Updates /
  Settings.
- `TreeTableView`: Name, Category, Version, Installed/Available, Enabled; detail pane;
  keyword search and category grouping (NetBeans convention).
- Actions (`@FxActionRegistration`): Check for Updates, Install, Update, Uninstall,
  Activate/Deactivate, Add/Remove Update Center.
- Long operations on `RequestProcessor`; progress/cancel feed `nbfx.progress` + status bar;
  restart via `OperationSupport.doRestart(Restarter, ...)` with an FX restart dialog implementing
  `Restarter`.
- Persistence via `AutoupdateSettings` / `NbPreferences`; update centers via
  `UpdateUnitProviderFactory`.
- Cluster: include `autoupdate.services` (and `autoupdate.cli` if needed); exclude `autoupdate.ui`.
  Note `autoupdate.services` transitively pulls `api.progress`/`openide.dialogs` (accepted under UI
  purity; nbfx must not use them for UI).

### 8.4 Options / Settings (`nbfx.options`)

Mirror the NetBeans options SPI shape, but return a JavaFX `Node` (see
`platform/options.api/src/org/netbeans/spi/options/OptionsPanelController.java:39,176` for the
reference contract):

```java
public abstract class FxOptionsPanelController {
    public abstract javafx.scene.Node getComponent(org.openide.util.Lookup masterLookup);
    public abstract void update();
    public abstract void applyChanges();
    public abstract void cancel();
    public abstract boolean isValid();
    public abstract boolean isChanged();
    public org.openide.util.Lookup getLookup() { return org.openide.util.Lookup.EMPTY; }
    public void handleSuccessfulSearch(String searchText, java.util.List<String> matchedKeywords) {}
}

public interface FxOptionsDisplayer {   // mirrors org.netbeans.api.options.OptionsDisplayer
    void open();
    void open(String id);
}
```

- Registration: `@FxOptionsRegistration` (top-level), `@FxOptionsContainerRegistration` /
  `@FxOptionsSubRegistration` (grouping) → layer `NbFx/Options/<cat>/<sub>`.
- Options window: category list (left) + sub-tab area (right) + Quick Search over registered
  keywords; OK/Apply/Cancel drive `isChanged`/`applyChanges`/`cancel`; validity gates Apply.
- Persistence: `Settings` helper over `NbPreferences` with typed `ObservableValue` binding; a
  declarative `@FxSettings` mapping generates key metadata (processor in `nbfx.annotations`).
- First panels:
    - **General**: theme/dark mode, base font size.
    - **Editor**: line numbers, breadcrumbs, tab size, fonts/colors — migrates
      `EditorSettingsImpl` (`api/editor/EditorSettings.java`).
    - **Java/Maven**: JDK home, Maven home/settings.xml, offline.
    - **Plugins**: link into `nbfx.plugins`.
    - **Advanced**: generic tree over `Preferences`.

### 8.5 Output, progress, notifications, wizards

- `nbfx.output`: JavaFX `InputOutputProvider` implementing the clean
  `org.netbeans.spi.io.InputOutputProvider` / `org.netbeans.api.io.IOProvider` (0 AWT/Swing files).
  Registered so build/run tooling writes through the standard IO API into a JavaFX console.
- `nbfx.progress`: FX progress model feeding the status bar and plugin operations.
- `nbfx.notifications`: FX notifications/dialogs (generalises `ErrorReporter`/`CloseConfirmation`).
- `nbfx.wizard`: FX wizard framework; New File from headless `Templates`; New Project (Maven first,
  then Gradle/Ant); registered via `@FxWizardRegistration`.

---

## 9. Documentation and versioning governance (NetBeans conventions)

Current gaps in `nbfx`:

- `nbfx/c.gluonhq.netbeans.nbfx.api/apichanges.xml` is a stub (8 lines, no `<changes>`) and its
  `xml-stylesheet` href is malformed: `CHANGE../../nbbuild/javadoctools/apichanges.xsl`.
- `nbfx/c.gluonhq.netbeans.nbfx.api/arch.xml` is full of `XXX no answer` placeholders.
- Only `nbfx.api` sets `javadoc.arch` in `project.properties`; no `sigtest` anywhere.
- Module spec versions exist (`OpenIDE-Module-Specification-Version: 1.0`) but dependency versions
  are pinned without a documented policy.

Workstream:

1. **Module metadata**: real `OpenIDE-Module-Specification-Version` (or `spec.version.base` in
   `project.properties`), bumped on every API change; keep pinned `<specification-version>` deps;
   `public-packages` only for intended API.
2. **SPI maturity**: publish the new `Fx*` SPIs as **"under development"** in `arch.xml`
   (`<api category="under development">`), with `@since 1.0` Javadoc from the start and
   `-Xdoclint` enabled.
3. **`apichanges.xml`**: fix the stylesheet href; add a `<change>` entry for every API change
   (`id`, `api`, summary, `version major/minor` matching the spec version, `date`, `author`,
   `compatibility addition/source/binary/semantic`, `description`, `class`/`package`), using the
   DTD and the `platform/openide.filesystems/apichanges.xml` reference structure.
4. **`arch.xml`**: replace `XXX no answer` with real answers — especially `arch-quality`,
   `compat-i18n`, `dep-jrejdk`, `deploy-packages`, `lookup-lookup`, `lookup-register`,
   `resources-layer`, `resources-preferences`, `exec-threading` (FX thread model),
   `perf-startup`/`perf-exit`. Wire `javadoc.arch` for every API-bearing module.
5. **i18n**: keep `Bundle.properties` + `NbBundle`/`@Messages`; no hardcoded user-visible strings.
6. **Freeze gate** (after Phase 3): switch SPIs from "under development" to stable, finalize
   `apichanges.xml`, and enable **`sigtest`** signature checks (commit `.sig` files,
   `sigtest.gen.fail.on.error=true`) in CI.
7. **Legal/release**: Apache headers on new files; update `nbbuild/rat-exclusions.txt` when needed;
   document the JDK/JavaFX baseline (`lib.javafx/javafx.version` = 27,
   `OpenIDE-Module-Java-Dependencies: Java > 25`) in `arch.xml`; bump versions when JavaFX/JDK move.

---

## 10. Lean cluster and guardrails

- Define `clusters.config.nbfx.list` in `nbbuild/cluster.properties` with an allowlist of needed
  platform/core/java modules; exclude user-facing Swing UI clusters (`ide` editor/options/project UI,
  `extide`, `autoupdate.ui`).
- Retain the thin Swing layer transitively required by `parsing`/`indexing`
  (`api.progress`, `projectuiapi.base`, `openide.dialogs`); whole-classpath purity is **not** required.
- Guardrail build target fails when:
    1. `nbfx/*/src` imports a forbidden package (§3.1);
    2. any `nbfx` module directly depends on a denylisted module (§3.1);
    3. `nbfx` JARs contain `javax/swing` or `java/awt` classes.
- Keep a full-cluster config as a development fallback until the lean set is stable.

---

## 11. Roadmap

### Phase 0 — substrate and guardrail
- Run the dependency audit; freeze allow/deny lists + guardrail target.
- Add `nbfx.annotations`; extract/formalize `nbfx.windows`.
- Rework lifecycle; remove `NbfxTabPane` static hooks; enable lean `clusters.config.nbfx.list`.
- **Acceptance**: guardrail green; app runs lean; FX windows registered via layer.

### Phase 1 — actions and context
- `nbfx.actions` presenters (`FxMenuBuilder`/`FxToolbarBuilder`/`FxContextMenuBuilder`).
- Selection-scoped `Context` Lookup; port editor/file/project/find commands to
  `@FxActionRegistration`.
- **Acceptance**: menus/toolbars/context menus built from layer; no hardcoded `ActionBars` lists.

### Phase 2 — projects and views
- `FxProject` + providers over direct libraries; headless `Project` adapters feeding the indexer.
- Project-type support, in order (see §14): **Maven** (done: recognition/name/subprojects/icons in
  `nbfx.project.ui.maven`), then **NetBeans module / Ant** (dog-food: build this repo itself),
  then **Gradle** (Tooling API). Each type = one `ProjectKindProvider` + ordered node factories +
  `Projects/<type>/Actions` shadows.
- Node factories under `NbFx/Projects/<type>/Nodes` (positions) composed like
  `NodeFactorySupport.createCompositeChildren`; root actions from `Projects/<type>/Actions`.
- Copy the original node hierarchy and icons from the source modules (see §14.2–§14.5).
- `nbfx.projectview` tree from `Sources`/classpath + registrations.
- Move find-usages/search views onto `@FxViewRegistration`; finish Gradle subproject discovery and
  build-output exclusions.
- **Acceptance**: Maven, NetBeans-module/Ant and Gradle projects show correct logical trees,
  navigation, and open-subproject actions.

### Phase 3 — build/run and output (MVP complete)
- Build/Run/Test/Clean via `ActionProvider`; `nbfx.output`; `nbfx.progress`; `nbfx.statusbar`.
- **Acceptance**: open/edit/build/run a Maven project, see output, in the lean pure-FX app.
- **Freeze gate** (§9.6): stabilize `Fx*` SPIs, finalize `apichanges.xml`, enable `sigtest`.

### Phase 3.5 — fully compatible build execution (in-process; see §15)
- Replace the CLI `ProcessRunner` with per-tool in-process executors behind `BuildActionProvider`:
  `BuildOutput`/`BuildProgress`/`BuildExecution` SPI (no `org.openide.windows.InputOutput`).
- Order: **Ant** (reuse the installed Ant distribution from `org.apache.tools.ant.module`; no new
  bundle), **Gradle** (Tooling API via `libs.gradle` + existing `nb-tooling.gradle`), **Maven**
  (`MavenEmbedder.execute`). Cancellation + status-bar progress; remove shell-out.
- **Acceptance**: Build/Clean/Rebuild/Run/Test/Javadoc run in-process for all three tools with
  output in the Fx console and working cancel.

### Phase 4 — wizards, templates, options
- `nbfx.wizard`; New File via headless `Templates`; New Project (Maven first, then NetBeans-module/Ant
  and Gradle); `nbfx.options` panels.
- **Acceptance**: create projects/files and change core settings without leaving the FX app.

### Phase 4.5 — project properties (registerable customizers)
- Implement the registerable project-customizer pattern from §14.6: `FxCustomizerProvider` in the
  project Lookup + per-project-type category/panel registrations under
  `NbFx/Projects/<type>/Customizer/<category>`, rendered by an `FxProjectCustomizer` dialog.
- First categories: Sources, Libraries/Classpath, Build, Run, Application, Formatting, License; then
  project-type-specific panels (Maven, apisupport Important Files).
- **Acceptance**: Project Properties opens in FX and edits/persists per-type settings.

### Phase 4.6 — 1:1 project configuration UIs (full read/write; see §16)
- Framework: category SPI + layer composer (`NbFx/Projects/<type>/Customizer/<category>`) + dialog
  shell with per-category `ok`/`store`/`close` listeners, validity gating, background save.
- Per type, 1:1 categories/panels/persistence, in order: **Ant/apisupport**, **J2SE/J2SE-module**,
  **Maven**, **Gradle** (tables in §16).
- **Acceptance**: every category of each supported type opens, edits, and round-trips its model.

### Phase 5 — plugins, debug, VCS, polish
- `nbfx.plugins` (over `autoupdate.services`); `nbfx.debug` (headless `api.debugger` core or JDI/DAP);
  `nbfx.vcs` (JGit) — remove `VersioningOptOut`; configurable keymap; refactoring/rename.

### Phase 6 — global context, command refactor, modes and completion (see §17–§20)
- `nbfx.context`; move build commands to `nbfx.project.actions` (project-Lookup
  `@ProjectServiceProvider`, meaningful enablement); `nbfx.run` execution-only.
- FX mode descriptor + submenu support (§18); rename `nbfx.propertysheet` + Properties window; git
  status bar/view on `FxActionContext`; `nbfx.structure` + Java members provider.
- Migrate remaining command groups to `FxActionContext`.
- **Acceptance**: commands enable/disable from the view-tied global context; Properties/Structure
  windows track the selection; `Window ▸ IDE Tools ▸ Properties` works; guardrail green.

### Deferred / backlog
- ~~Sliding/auto-hide window sides (FX has no native sliding)~~ — **done (§24)**: custom sliding
  sides on the dock area; left to do there: dragging slide buttons, a context menu on them,
  Maximize.
- General `FxNode` abstraction — only if a surface needs it.

---

## 12. Verification

- Per phase: guardrail green; `nbfx` cluster builds (`ant -Dcluster.config=nbfx build`); smoke matrix
  (open/edit/build/run Maven, Gradle, Ant; output; session restore).
- Harness unit tests for annotation processors (assert generated `META-INF/generated-layer.xml`
  content) and presenters (layer traversal → menu/tree/status construction).
- At the Freeze gate: `sigtest` API compatibility + `ant javadoc` validation of `arch.xml` and
  `apichanges.xml`.

---

## 13. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Transitive Swing via `parsing.indexing` blocks full classpath purity | Accept UI purity; guardrail scopes to nbfx source/JARs |
| Swing main window still starts | Keep suppression quarantined to launcher; never expose to nbfx UI |
| AWT touchpoints in `java.source.base`/`api.java` | Confine to headless use; guardrail forbids AWT in nbfx |
| Removing `org.openide.windows` may break modules that assume `WindowManager` | Keep the thin Swing layer; provide nbfx `View` SPI for all nbfx UI |
| Gradle Tooling API / Maven Resolver dependency trees | Keep behind the `nbfx.project` boundary |
| Options/Properties/Plugins rewrites are large | Mechanical, registration-driven; sequence after MVP core |
| Reused `autoupdate.services` shows Swing progress | Drive operations with `nbfx.progress`; do not instantiate `autoupdate.ui` |
| Ant in-process reuse (cross-cluster jars) | Reuse the installed `${cluster}/ant/lib/*.jar` (compile via `${ant.home}`, runtime via class-path-extension / `InstalledFileLocator`); do not use the Swing-bound `o.apache.tools.ant.module` engine |
| Gradle Tooling API / init-script discovery | Depend on `libs.gradle`; locate `nb-tooling.gradle` + tooling jar with `InstalledFileLocator`; pin the Tooling API version |
| Maven in-process executor pulls `api.progress`/`projectuiapi.base` | Accepted under UI purity; keep behind the Maven UI module boundary |
| Customizer persistence parity (POM/XML/source sets) | Mirror the original writers exactly; round-trip tests per category |
| Command refactor changes enablement behavior | Make `isActionEnabled` meaningful + tests; use the marker-file provider only when the project Lookup has none |
| View-tied global context vs existing global singletons | Compose the singletons under `FxActionContext`; migrate command groups incrementally (§17) |
| Duplicating the Swing mode layout | Declarative `FxArea` weights over the existing `DockLocation`/`Docking` substrate (§18) |
| Structure panel activation contract | Test `panelActivated`/`panelDeactivated` against the editor context with the Java members provider (§19.2) |

---

## 14. Project-type UI — original architecture reference (reimplementation guide)

Always copy the original architecture first, then reimplement with FX + layer/annotation registration.

### 14.1 Generic project UI (`ide/projectui`, `ide/projectuiapi`, `ide/projectuiapi.base`)

- Window is a hidden-root tree: `ProjectsRootNode` (root) + `Children.Keys` of open projects; each
  project node is `LogicalViewProvider.createLogicalView()` wrapped in a `BadgingNode`
  (main-project bold, VCS/FS badges). Physical view = `PhysicalView` over `TYPE_GENERIC` source groups.
- The platform only defines the root + per-project host; all inner nodes come from project-type
  `NodeFactory` registrations under `Projects/<type>/Nodes`, composed by
  `NodeFactorySupport.createCompositeChildren(project, "Projects/<type>/Nodes")`.
- SPI to mirror: `LogicalViewProvider`, `PathFinder`, `CustomizerProvider`/`2`,
  `NodeFactory` + `NodeList` + `NodeFactorySupport`, `CommonProjectActions`, `ProjectSensitiveActions`,
  `FileSensitiveActions`, `MainProjectSensitiveActions`, `ProjectActionPerformer`, `FileActionPerformer`,
  `BuildExecutionSupport`, `DefaultProjectOperations`, `ProjectChooser`, `ProjectCustomizer`,
  `ProjectOpenedHook`, `RecommendedTemplates`/`PrivilegedTemplates`, `ProjectProblemsProvider`.
- Actions are driven by `ActionProvider` commands; root actions come from layer folder
  `Projects/<type>/Actions` via `CommonProjectActions.forType(type)`.
- Icons: project root/folder/package/file plus action icons (see per-type sections).

### 14.2 Java / Ant (J2SE) (`java/java.j2seproject`, `java/java.api.common`, `java/java.project.ui`)

- Project type id `org-netbeans-modules-java-j2seproject`; logical root children =
  `JavaSourceNodeFactory` (pos 200, "Source Packages"/"Test Packages" via
  `PackageView.createPackageView(SourceGroup)`) + `LibrariesNodeFactory` (pos 300).
- No separate "Resources" node; resources live in the source-package view. Java file nodes are plain
  `DataObject` delegates (`JavaNode`).
- Build: `J2SEActionProvider` maps commands to Ant targets (`build`/`jar`, `clean`, `clean,jar`/
  `clean,compile`, `run`, `test`, `debug`, `compile-single`, `run-single`, `test-single`, `javadoc`)
  executed via `ActionUtils.runTarget` → `AntTargetExecutor`.
- Icons: `j2seProject.png`; packages `package.gif`/`packageEmpty.gif`/`packagePrivate.gif`/
  `packagePublic.gif`; libraries `libraries.gif`; jar `jar.gif`; project deps `projectDependencies.gif`.

### 14.3 NetBeans module / Ant (apisupport) — dog-food target

- Project type id `org.netbeans.modules.apisupport.project` (legacy Ant type id; note
  `ProjectManager.Result.getProjectType()` is `null` for Ant-based projects — read the `<type>` from
  `nbproject/project.xml` instead). Suite type `...apisupport.project.suite`.
- Logical nodes (layer `Projects/org-netbeans-modules-apisupport-project/Nodes`):
  `SourcesNodeFactory` (100), `TestDataDirsNodeFactory` (150), `ImportantFilesNodeFactory` (200,
  "Important Files": manifest.mf, build.xml, nbproject/*), `LibrariesNodeFactory` (300).
- Actions (layer `Projects/.../Actions`): generic shadows (Build 300 / Rebuild 400 / Clean 500 /
  Run 700 / Debug 800 / Test 1000 / Javadoc 1800 / SetMain / Close / Rename / Move / Copy / Delete /
  Find / Customize) plus apisupport-specific: Reload in Target Platform (1400), Install/Reload in Dev
  IDE (1500), Create NBM (1600), Generate Architecture Description (1900).
- Build: `ModuleActions` maps commands to harness Ant targets (`build`, `clean`, `run`, `debug`,
  `javadoc-nb`, `test-unit`, `nbm`, `reload`, `reload-in-ide`, `arch-nb`) via `ActionUtils.runTarget`.
- Icons: root `module.png` (OSGi `bundle.png`), Important Files badge `config-badge.gif`, libraries
  `libraries-badge.png`, suite root `suite.png`.
- **Cluster requirement**: `ProjectManager` only recognises an apisupport project if its
  `AntBasedProjectType` is registered, which is done by `apisupport.ant`; `project.ant` alone is not
  enough. The nbfx cluster therefore depends on `nb.cluster.apisupport` (`nbbuild/cluster.properties`).
  Trade-off: this widens the otherwise-lean nbfx cluster; the long-term alternative is an nbfx-native
  Ant/apisupport `Project` provider (§6).
- **Acceptance for dog-food**: open this repository's own modules (and `nbbuild`) and build them.

### 14.4 Maven (`java/maven`) — reference (implemented)

- Type `org-netbeans-modules-maven`; root `MavenProjectNode` children from
  `Projects/org-netbeans-modules-maven/Nodes`: Sources (100), Other Sources (200), Generated (300),
  Modules (400), Dependencies (500), Java Dependencies (520), Project Site (600), Project Files (700).
- Actions: generic shadows + `Run Maven` (custom popup 1400), `buildWithDependencies` (500),
  `closeSubprojects` (2000), `Open POM` (1650), `Reload Project` (1700), `Add Dependency`.
- Build: `ActionProviderImpl` → `ActionToGoalUtils` → `defaultActionMappings.xml`/`nbactions.xml` →
  `RunConfig` → `MavenCommandLineExecutor` (goals: `install`, `clean`, `clean install`, `test`,
  `exec:exec` for run/debug with `${jpda.address}`, `javadoc:javadoc`).
- Icons: root `Maven2Icon.gif`, `jaricon.png`, `nbmicon.png`/`suiteicon.png` (from maven.apisupport),
  badges `libraries-badge.png`, `modules-badge.png`, `projectfiles-badge.png`, `others-badge.png`.
- nbfx status: recognition + name + subprojects + icons implemented via
  `nbfx.project.ui.maven` (`MavenProjectKindProvider`); node tree + build actions still TODO.

### 14.5 Gradle (`extide/gradle`, `java/gradle.java`, ...) — do not forget

- Type `org-netbeans-modules-gradle`; Java/Groovy/WAR support is layered through **plugin-scoped
  LookupProviders** (`Projects/org-netbeans-modules-gradle/Plugins/<plugin-id>/Lookup`), not
  project-type inheritance. Model + execution use the **Gradle Tooling API** (`ProjectConnection`,
  `BuildLauncher`, `BuildActionExecuter`) with an injected init script (`nb-tooling.gradle`) and the
  `netbeans` Gradle plugin — not Ant, not a raw CLI.
- Logical nodes (layer `Projects/org-netbeans-modules-gradle/Nodes`): Source Packages/Resources (100),
  Distributions (150), Configurations (190), Build Scripts (200), Subprojects (400), Java
  Dependencies (520). Tasks live in the "Related Tasks" navigator, not the tree.
- Actions: generic shadows + `Run Gradle` custom popup (1400), `Reload Project` (1700), `Prime`.
  Command→task mapping is data-driven (`action-mapping.xml`): `clean`, `build`/`assemble`,
  `clean build`, `--rerun-tasks test`, `run`, `run --debug-jvm`, `war`, `javadoc`.
- Icons: root `gradle.png` (+ `javaseProjectIcon.png` from gradle.java), `buildscripts-badge.png`,
  `gradle-task.gif`, `libraries-badge.png`.

### 14.6 Project properties / customizers (registerable) — architecture to copy

Mirror the NetBeans `CustomizerProvider`/`CustomizerProvider2` + `ProjectCustomizer` model, but render
in FX:

- `FxCustomizerProvider` in the project Lookup: `showCustomizer()` / `showCustomizer(category, sub)`
  opens the FX project-properties dialog, optionally preselecting a category.
- Categories/panels are **registered per project type**, exactly like the original
  `@CustomizerPanel` / layer folder `Projects/<type>/Customizer/<category>` (positions): a panel
  factory contributes a category (display name, position) and an FX `Node`.
- `FxProjectCustomizer` dialog: left category list + right panel area; OK/Apply/Cancel drive
  `isChanged`/`applyChanges`/`cancel`; validity gates Apply (reuse the `nbfx.options` panel contract).
- Persist through `nbproject/*` writers (Ant/apisupport), the Maven model, or Gradle properties as
  appropriate; the dialog itself stays project-type agnostic.
- Initial categories to copy: Sources, Libraries/Classpath, Build, Run, Application, Formatting,
  License, plus project-type-specific ones (Maven, apisupport Important Files).
- **Roadmap** (not in the current step): implement after the project trees + build actions exist.

## 15. Build execution — fully compatible (in-process)

**Goal**: run builds through the build systems in-process (no CLI shell-out), writing to the Fx
console, while keeping UI purity. Replace the CLI `ProcessRunner` with per-tool executors behind the
existing `BuildActionProvider` SPI; keep `commandLine()` as a fallback.

### 15.1 Findings (evidence)

| Tool | Original entry point | Engine reusable headlessly? |
|---|---|---|
| Ant | `ActionUtils.runTarget` → `AntTargetExecutor` → `TargetExecutor` (`extide/o.apache.tools.ant.module`) | **No** — `TargetExecutor` imports Swing/AWT/`openide.awt`, hard-requires legacy `InputOutput`, and `execute(OutputStream)` throws `"XXX No support for outputStream currently!"` (`run/TargetExecutor.java:487`). Module closure 49 modules / 37 Swing. |
| Maven | `RunUtils.run` → `MavenCommandLineExecutor` (`java/maven`) | **No** — CLI `ProcessBuilder` + Swing; there is **no in-process executor**. |
| Maven embedder | `MavenEmbedder.execute(MavenExecutionRequest)` (`java/maven.embedder`) | **Yes** — 0/19 files import Swing/AWT; already a friend of `nbfx.project.ui.maven`. |
| Gradle | `RunUtils.executeGradle` → `GradleDaemonExecutor` (`extide/gradle`) | **Partial** — driver UI-bound, but `ProjectConnection.newBuild()` underneath is headless. |
| Gradle Tooling API | `extide/libs.gradle` (`org.gradle.tooling.*`) | **Yes** — 0 Swing, public `org.gradle`; init script/tooling jar already bundled. |

Already headless: `org.netbeans.spi.project.ActionProvider`, `org.netbeans.api.io`,
`org.netbeans.modules.maven.api.execute.RunConfig`, `org.netbeans.modules.gradle.api.execute.*`.

Ant distribution is installed by `org.apache.tools.ant.module` to `${cluster}/ant/lib/*.jar`
(`build.xml:56-70`) but `org.apache.tools.ant` is **not** on its classpath and the engine is Swing.

### 15.2 Design

- New SPI in `nbfx.project.ui.api` (or `nbfx.build.api`): `BuildOutput`, `BuildProgress`,
  `BuildExecution` (`cancel()`); `BuildActionProvider.executor(Path, String)` → `BuildExecution`
  (default `null` ⇒ CLI fallback). No `org.openide.windows.InputOutput`.
- **Ant** (`nbfx.build.ant`, new module): reuse the installed Ant distribution — compile via
  `<classpath mode="compile">${ant.home}/lib/ant.jar:...` and expose at runtime via a
  `class-path-extension` `<runtime-relative-path>` at the installed `ant/lib/*.jar` (or
  `InstalledFileLocator`). Run `Project`/`ProjectHelper`/`DefaultLogger` + a custom `BuildListener`
  → `BuildOutput`; targets per `BuildCommands` (`build`/`clean`/`clean build`/`test-unit`/`run`/
  `javadoc-nb`). Do **not** depend on the Swing closure of `o.apache.tools.ant.module`.
- **Gradle** (`nbfx.project.ui.gradle`): depend on `org.netbeans.modules.libs.gradle`; drive
  `GradleConnector.forProjectDirectory(dir).connect().newBuild()`, reuse `nb-tooling.gradle` +
  `netbeans-gradle-tooling.jar` via `InstalledFileLocator`, `setStandardOutput/Error`, `run()`,
  cancel via `CancellationTokenSource`.
- **Maven** (`nbfx.project.ui.maven`): friend of `java/maven.embedder`; `EmbedderFactory
  .getProjectEmbedder()` → `createMavenExecutionRequest()` (goals/working dir/properties) →
  `execute()` with a custom `TransferListener`/`BuildLogger` → `BuildOutput`; replicate
  `defaultActionMappings` goals.

### 15.3 Milestones (Phase 3.5)

1. `BuildOutput`/`BuildProgress`/`BuildExecution` SPI + run-module wiring (CLI still fallback).
2. Ant embedded executor.
3. Gradle Tooling driver.
4. Maven embedder executor.
5. Cancellation + status-bar progress; remove CLI shell-out.

### 15.4 Risks

- Ant cross-cluster jar reuse (compile `${ant.home}` + runtime path / `InstalledFileLocator`).
- Gradle init-script/tooling-jar discovery + Tooling API version coupling.
- Maven embedder pulls `api.progress`/`projectuiapi.base` (accepted under UI purity).
- Headless verification needs real builds → integration tests.

## 16. Project configuration UIs — 1:1 (full read/write)

**Goal**: a 1:1 JavaFX port of the "Project Properties" customizers for every supported project
type, with full read/write persistence.

### 16.1 Framework (extends Phase 4.5)

- Category SPI equivalent to `CompositeCategoryProvider` (`@Registration` → layer folder
  `Projects/<type>/Customizer[/<category>]` with `displayName`/`position`); per-category
  `ok`/`store`/`close` listeners; validity gating; background save under the project write mutex.
- Dialog shell: category tree + panel area + OK/Apply/Cancel (+ Help); panel caching; error/warning
  strip. Composed from `NbFx/Projects/<type>/Customizer`.
- **Follow-up — lazy panel init**: the dialog currently creates every panel up front (needed so
  `isChanged()`/`isValid()` over all panels never sees null fields). Revisit: create panels lazily
  on first selection, and make the button state consider only created panels (or give panels a
  lightweight "dirty/valid" signal independent of the built UI), so opening Properties does not
  instantiate every category.

### 16.2 Per-type inventory (category → panel → persistence)

- **Ant / apisupport** (`apisupport.ant`):
  `Sources→CustomizerSources`, `Libraries→CustomizerLibraries`, `Display→CustomizerDisplay`,
  `Versioning→CustomizerVersioning`, `Build/Compiling→CustomizerCompiling`,
  `Build/Packaging→CustomizerPackaging`. Persist: project/private `.properties` + `manifest.mf` +
  `nbproject/project.xml` (dependencies/friends/public-packages/classpath-ext).
  Suite: `Sources`, `Libraries`, `Application`(branding) → `SuiteProperties`.
- **J2SE / J2SE-module** (`java.j2seproject`):
  `Sources→CustomizerSources`, `Libraries→CustomizerLibraries`,
  `Build(BuildCategory)/{Build, Jar, Javadoc}→CustomizerCompile/Jar/Javadoc`,
  `Run→CustomizerRun`, `Application→CustomizerApplication`, `License→license panel`.
  Persist: `AntProjectHelper`/`EditableProperties` (project + private + `config.properties`).
- **Maven** (`java/maven`):
  `General(BASIC)→BasicInfoPanel`, `Sources(SOURCES)→SourcesPanel`,
  `Configurations(CONFIGURATION)→ConfigurationsPanel`, `Run(RUN)→RunJarPanel`,
  `Actions(MAPPING)→ActionMappings`, `Build/Compile(COMPILE)→CompilePanel`,
  `License(LICENSE_HEADERS)→license panel`, `Formatting`. Persist: POM `ModelOperation`s +
  auxiliary properties + `nbactions.xml`.
- **Gradle** (`extide/gradle`, `java/gradle.java`):
  `Project Information(info)→ProjectInfoPanel`, `Sources(sources)→SourcesPanel`,
  `Configurations(CONFIGURATIONS)→ConfigurationsPanel`,
  `Build/Gradle Execution(build/execute)→GradleExecutionPanel`,
  `Build/Build Actions(build/actions)→BuildActionsCustomizer`,
  `License(license)→LicenseHeadersPanel`. Persist: Gradle source-set/config providers + aux props.

### 16.3 Milestones (Phase 4.6)

1. Framework (SPI + layer composer + dialog + listeners/validity/save).
2. Ant / apisupport.
3. J2SE / J2SE-module.
4. Maven.
5. Gradle.

### 16.4 Risks

- Persistence parity is the hard part (POM model ops, apisupport XML writers, Gradle source sets) —
  mirror the original writers exactly; round-trip tests per category.
- Large surface → deliver type-by-type.

## 17. Global action context and command refactor

**Problem (evidence).** Build commands live in `nbfx.run`
(`c.gluonhq.netbeans.nbfx.run`); their enablement uses a separate `BuildActions` default-Lookup
mechanism and is effectively always true; commands reach into
`ProjectRegistry`/`FileSelectionContext`/`EditorContext` directly instead of a single context; there
is no unified observable context.

**Swing reference.** `org.netbeans.spi.project.ActionProvider` is looked up in the *project's* Lookup
(registered with `@ProjectServiceProvider(service=ActionProvider.class,
projectType="org-netbeans-modules-maven")`, layer `Projects/<type>/Lookup`). Enablement is
`getSupportedActions().contains(command)` **and** `isActionEnabled(command, context)`. UI actions are
generic (`ide/projectui` `ProjectAction`/`LookupSensitiveAction`) and the global context is
`Utilities.actionsGlobalContext()` (activated nodes). `org.netbeans.spi.project.ActionProvider` cannot
be reused directly because the implementing modules are Swing-bound.

### 17.1 `nbfx.context` (new) — NetBeans global context + per-domain facades

`FxActionContext` is a **generic `Lookup`** (in `nbfx.api.actions`) with **no compile-time dependency
on any domain type** (`OpenProject`, `EditorDocument`, `FileObject`, `ViewProvider`).
`FxActionContextImpl` (in `nbfx.context`) is a thin wrapper over the NetBeans global context
`Utilities.actionsGlobalContext()`.

```java
public abstract class FxActionContext extends Lookup {
    public static FxActionContext getDefault() { ... }   // Lookup.getDefault().lookup(...)
}
```

- nbfx contributes a **`ContextGlobalProvider`** (`NbfxGlobalContextProvider`, in `nbfx.ui`, with
  `@ServiceProvider(position = 100)`) that feeds the **raw** nbfx selection — the selected
  `FileObject`s, the active `EditorDocument`, the active `ViewProvider` — into the NetBeans global
  context. It mirrors the Swing `GlobalActionContextImpl` (which reads the activated `TopComponent`).
  The explicit `position` makes `Lookup.getDefault()` return it before the platform provider (which
  has no position and is therefore ordered last), so `Utilities.actionsGlobalContext()` reflects the
  JavaFX frontend.
- **Per-domain facades resolve on demand** (pull-based; nothing is pushed): `nbfx.project.context`
  provides `ProjectContext.selectedProject()`, which derives the `OpenProject` from the `FileObject`
  carried by the context — "the project from the file from the view" — via
  `ProjectRegistry.ownerOf(file)`. Editor/file facades follow the same shape.
- **View-tied.** The shell publishes the selected tab of `NbfxTabPane.focusedPane()` to
  `ViewSelectionContext` (`nbfx.api.view`); the provider turns it into the context's `ViewProvider`
  entry — the nbfx replacement for the activated TopComponent. **Superseded by §22 (implemented):**
  the provider is now a stable proxy that follows the active component's lookup (the context *is*
  the active component's lookup), retiring the flat reconstruction and the per-consumer precedence
  heuristics; `ViewSelectionContext` still carries the active view/editor.
- `ContextSensitiveCommand` (in `nbfx.api.actions`) is domain-agnostic: it takes the observed context
  types (`Class<?>...`), listens on the context's `Lookup.Result`s, and re-evaluates
  `isEnabled(FxActionContext)`. A build command observes `FileObject` and calls the `ProjectContext`
  facade, without `nbfx.api` depending on the project API.
- Verified by `NbfxGlobalContextProviderTest` (module-system: the global context carries the nbfx
  selection) and `ProjectContextTest` (facade resolution).
- **Fixed (Java Navigator round, §23):** the provider was registered *without* the `position = 100`
  its javadoc and this plan promised, so `MetaInfServicesLookup` ordered it after the Swing
  `GlobalActionContextImpl` (`openide.windows`, discovered first) and
  `Utilities.actionsGlobalContext()` cached the empty `TopComponent` context: every `FxActionContext`
  consumer (Navigator, Properties, Versioning, context commands) was blind at runtime, while the unit
  test instantiates the provider directly and never saw it. Also: when no view is active the active
  editor document now wins over the `lastSelection` fallback (an editor tab *is* the active
  component); the fallback only applies while a follower view that exposes nothing is active
  (`editorWinsOverEarlierNavigatorSelection`).
- **Pitfall:** the context is a `Lookups.proxy`, and `SimpleProxyLookup` holds its `Lookup.Result`s
  weakly. A consumer must keep the result it listens on in a field; an inline
  `context.lookupResult(T.class).addLookupListener(...)` is collected with its listener and never
  fires again. `StructurePanel` was fixed; `PropertiesView`, `GitStatusView` and
  `FindInSelectionCommand` still subscribe that way and should be fixed the same way.

### 17.2 `nbfx.project.actions` (new) and `nbfx.run` (execution-only)

- Move the Build/Clean/Rebuild/Run/Test/Debug/Javadoc commands and their menu/toolbar/context-menu
  registrations out of `nbfx.run` into `nbfx.project.actions`.
- Register `BuildActionProvider` in the **project's Lookup** via
  `@ProjectServiceProvider(service=BuildActionProvider.class, projectType=…)` for
  `org-netbeans-modules-maven`, `…-gradle`, `…-apisupport-project`, `…-java-j2seproject`; resolve it
  with `project.getLookup().lookup(BuildActionProvider.class)` (keep the marker-file fallback).
- `isActionEnabled(command)` becomes meaningful: unsupported or absent provider ⇒ **disabled**; no
  default-true.
- `nbfx.run` keeps its name and becomes execution-only: `BuildRunner`, `ProcessRunner`, `BuildTool`,
  `BuildActions`.

### 17.3 Node API — current replacement status

- **No full `Node`.** The property model (`FxPropertiesProvider`/`FxPropertySet`/`FxProperty`) is the
  `Node.getPropertySets()` analogue; the navigator tree uses `ProjectEntry` (a local `Node`/`Children`
  analogue in `nbfx.project.ui.java`). There is no `FxNode` (name/icon/children/actions/context
  menu/property sets in one abstraction); actions and selection are separate.
- The Properties/Structure windows therefore consume `FxPropertiesProvider`/`FxStructurePanel`, not a
  Node layer. A general `FxNode` is **not** required for the MVP; revisit only if a surface needs it.

### 17.4 Migrate remaining command groups

Editor → file/navigator → find → project lifecycle → view/window → debug → VCS → options/plugins, one
module per step; each drops direct context access and binds to `FxActionContext`.

---

## 18. Window modes and layout (duplicate the Swing modes)

Swing modes (`.wsmode` + layer; `platform/core.ui`, `core.windows`, `spi.navigator`): `editor`
(kind=editor) center; `explorer` (view) left (h20/w0.3 + v20/w0.5, active `projectTabLogical_tc`);
`navigator` (view) left below explorer (v60/w0.5, active `navigatorTC`); `properties` (view) right;
`output` (view) bottom; plus the four `*SlidingSide` modes (auto-hide, permanent). Tabs are grouped
per mode by `.wstcgrp`.

nbfx today: `DockLocation` (LEFT, LEFT_BOTTOM, CENTER, CENTER_BOTTOM, BOTTOM, RIGHT) + `Docking`
(permanent NAVIGATOR/MAIN panes, docked panes below/edge) + `ViewRegistry`. It lacks named modes and
declared default weights (only `DEFAULT_NAVIGATOR_SHARE = 0.22`).

- **DONE (885de5ba)**: declarative **`FxArea`** descriptor (`nbfx.api.view`, the analogue of a Swing
  `*.wsmode`) + registry, mirroring the Swing modes with FX names/weights: `editor`→CENTER,
  `explorer`→LEFT (w0.3), `navigator`→LEFT_BOTTOM (w0.5), `properties`→RIGHT (w0.3),
  `output`→CENTER_BOTTOM (w0.3). `Docking` derives the primary-pane divider share from
  `FxArea.EXPLORER` (replacing the hard-coded 0.3; `DEFAULT_NAVIGATOR_SHARE` kept as a deprecated
  alias) and gained `paneOf(FxArea)`. It still lacks a declared weight on the docked panes; the
  below/edge panes keep the docking lib's defaults until layout composition (§33).
- "One pane, many tabs" already models tc-groups; keep it.
- **Submenu support** (additional refactoring step): `LayerMenuBuilder.build()` recurses nested
  folders → JavaFX `Menu` (position/separators/`displayName` honoured) — **DONE (f0c20f)**; tested
  headlessly on the JavaFX 26+ headless glass platform (`-Dglass.platform=Headless` + `prism.order=sw`,
  `test-unit-sys-prop.*` in `nbfx.api`). Enables `Window ▸ IDE Tools ▸ Properties`.
  **Extended (§23):** `@FxMenuRegistration` gained `path` (`Menus` by default, `Menus/<menu>` or
  `ContextMenus/<context>` for a submenu) and `separatorBefore`, so a module declares a submenu of a
  *context* menu the way it declares one of a menu (the editor's `Navigate` submenu,
  `NbFx/ContextMenus/Editor/Navigate`); the menu and view processors write `displayName` as a
  `bundlevalue` so `#key` references resolve; `EditorContextMenu` recurses with
  `ActionLayerReader.readSubmenus` (display-only accelerators, as the menu is shown directly).
- **Done (§24):** sliding/auto-hide sides — a custom auto-hide UI over the dock area, since FX has
  no native sliding: `DockArea.minimize/restore` is the model (the four `*SlidingSide` modes
  collapse into "a pane out of the area, with the side it went to"), `SlidingArea`/`SlideBar` in
  `nbfx.ui` the bars and the slide-in panel.

---

## 19. Completion surfaces driven by the global context

### 19.1 Properties window (`nbfx.propertysheet`, renamed from `nbfx.properties`)

- Rename the module `c.gluonhq.netbeans.nbfx.properties` → `c.gluonhq.netbeans.nbfx.propertysheet`
  (update `nb.cluster.nbfx`, dependencies, `apichanges.xml`/`arch.xml`, refs).
- The window is the FX `NbSheet` analogue: a dockable view hosting `FxPropertySheet`, driven by
  `FxActionContext` (current global selection: project/file/editor document) via `FxPropertiesProvider`s;
  add an `EditorDocument` provider.
- **Closed by default**; docked in the `properties` mode (right, per §18).
- Menu `Window ▸ IDE Tools ▸ Properties` (`ActionIds.SELECT_PROPERTIES`) opens it.

### 19.2 Structure window (`nbfx.structure`, new)

Ports `NavigatorTC` + `NavigatorController` + `ProviderRegistry` + `NavigatorPanel`. nbfx already uses
"navigator" for the left-column project views (`NavigatorProvider`), so the structure window is named
**Structure** to reduce confusion; the mode keeps the name `navigator` for parity with Swing.

- `FxStructurePanel` SPI: `getDisplayName()`, `getComponent()` (FX `Node`),
  `panelActivated(FxActionContext)`, `panelDeactivated()`, optional toolbar/undo.
- `FxStructureController`: follows `FxActionContext` (active document → selected file → project),
  resolves panels by content type (file MIME / project kind), selects and activates/deactivates them.
- `StructureView` (`ViewProvider` id `structure`, mode `navigator`/LEFT_BOTTOM) with a panel selector
  (ComboBox) + content area.
- Menu `Window ▸ IDE Tools ▸ Structure` (`ActionIds.SELECT_STRUCTURE`) opens it (folds into M8a's
  `Window ▸ Navigator` rename, top-level, `SELECT_NAVIGATOR`).
- Provider registration under `NbFx/Structure/<mimeType>` (mirrors Swing `Navigators/<mimeType>`).
- **Java members provider** (additional step): the first `FxStructurePanel`, using the Java semantic
  engine — proves the editor/global-context contract end to end. **Superseded (§23):** the
  placeholder `JavaStructurePanel` is replaced by the Java Navigator port in `nbfx.structure.java`
  (`ClassMemberPanel` + `BeanPanel`, after NetBeans' `java.navigation`/`beans` panels).
- **Members reactivity (done):** the panel is kept in sync with the selected file; the provisional
  three-type patch was superseded when §22 landed, and the subscription is now narrowed to a single
  `FileObject` result on the active-view lookup — see §22.7 and §22.8. The Java panels' tracker
  (§23) observes `EditorDocument` *and* `FileObject` on the context: an editor's document is scanned
  again after each edit with the caret's row selected, a file from a project view is scanned once.
- **Host changes (§23):** `StructurePanel` shows the active panel's
  `FxStructurePanel.getToolbar()` beside the selector (the Swing `NavigatorPanelWithToolbar`
  layout), remembers the panel selected last per content type (NbPreferences), and `StructureView`
  exposes `select(FxStructurePanel)` so a command can bring a panel up; the window is
  `openAtStartup`, carries the Swing `navigator.png` icon (`Window ▸ Navigator` is Shortcut+7).

### 19.3 Git status bar and view follow the global context

`GitBranchStatusElement` and `GitStatusView`/`GitStatusViewProvider` currently follow
`ProjectRegistry.selectedProjectProperty()`. Switch them to `FxActionContext`, resolving the repository
root from the selected project/file/document.

---

## 20. Milestones (refactor + completion)

1. `nbfx.context` (generic `FxActionContext` wrapping `Utilities.actionsGlobalContext()`) +
   `NbfxGlobalContextProvider` (in `nbfx.ui`, `position=100`) + `nbfx.project.context` facade;
   `ContextSensitiveCommand`; view-tied active-view plumbing.
2. `nbfx.project.actions` + `@ProjectServiceProvider` in the project Lookup + meaningful enablement;
   `nbfx.run` execution-only.
3. FX mode descriptor (§18) + submenu support.
4. Rename `nbfx.propertysheet`; Properties window follows `FxActionContext`; `EditorDocument` provider.
5. Git status bar/view follow `FxActionContext`.
6. `nbfx.structure` SPI/controller/view; then the Java members provider.
7. Migrate remaining command groups to `FxActionContext`; enablement + provider-resolution +
   panel-activation tests; purity green.

**Done (§24):** sliding/auto-hide sides.

8. Naming & layer parity (§21.1): window **Structure → Navigator** (`Window ▸ Navigator`,
   `SELECT_NAVIGATOR`), **Debug → Debugging**, **Git → Versioning**; `NbFx/Navigators/<mime>`,
   `ContextMenus/Navigator`; de-SPI the left column (`NavigatorProvider` → plain `ViewProvider`s).
9. Own window system (§21.2): nbfx-native lifecycle `FxWindowSystem` (deliberately **not** the
   `org.netbeans.core.WindowSystem` interface — see §21.2) + `FxGuiRunLevel`; de-core.windows (drop
   `SwingWindowSuppressor`; hard-exclude `core.windows` + its dependents from the distribution).

### 20.1 Status (updated this round)

- **Sliding sides (done, §24):** every pane docked next to the editor pane carries *Minimize
  Window Group*; minimized groups live in bars along the window's left/right/bottom edge, their tabs
  slide in on hover or click, *Dock* returns one tab, *Restore Window Group* the pane; the state
  persists with the layout. `DockArea` gained `minimize`/`restore`/`minimizedPanes()`/`Anchor`.
- **Done:** milestones 1–6. `nbfx.context`, `nbfx.project.actions`, `nbfx.propertysheet` (renamed),
  `nbfx.structure`, `nbfx.structure.java` all created/updated and in `nb.cluster.nbfx`; submenu support
  and `Window ▸ IDE Tools` added; Git follows `FxActionContext`; the Java members panel
  (`JavaStructurePanel`) is the first `FxStructurePanel`.
- **In progress:** milestone 7 (migrate remaining command groups). Migrated so far:
    - `DebugFileCommand` → `ContextSensitiveCommand` (observes `EditorDocument`).
    - **File group** (`nbfx.file.actions`): added `nbfx.api.actions.ContextAction` (the functional
      `ContextSensitiveCommand`); `FileActions` reads the `FileObject` selection and active
      `ViewProvider` from `FxActionContext` instead of `FileSelectionContext`; Paste is re-evaluated on
      context change. `FileActionsTest` (5 tests, context-driven).
    - **Find group** (`nbfx.findinprojects`): `FindInProjectsCommand`/`ReplaceInProjectsCommand` read
      the active `EditorDocument` from `FxActionContext` (dropped `EditorContext`).
    - **Project lifecycle**: `NewFileWizard.defaultFolder()` reads the selected `FileObject` from
      `FxActionContext` (before falling back to the selected project).
    - **Debug**: `ToggleBreakpointCommand` becomes a `ContextAction` over `EditorDocument` (enabled only
      with an active editor; toggles on the caret line).
    - **VCS / options / plugins**: already context-*free* or already on `ProjectContext`
      (`GitBranchStatusElement`, `GitStatusView`); nothing to migrate.
    - **Deliberate exceptions** (not migrated): `FindInSelectionCommand` stays on
      `FileSelectionContext` because its enablement depends on the shell's navigator **focus** signal,
      which is shell state, not part of the global selection context; the editor Save commands and
      `FindUsagesCommandsProvider` keep `EditorContext` because they are *window-scoped*
      (`ActionRegistry.createScoped` drives each detached window from its own active document); the
      launcher-owned project Open/Close commands keep the shell's project observable; `SearchScope` and
      `NavigatorContextMenuFactory.hasModifiedDocuments` need the full document registry, not the
      selection.
- **Verification:** `ant -Dcluster.config=nbfx build -q` green; `ant -f nbfx/check-purity.xml -q` green;
  unit tests green for `nbfx.api`, `nbfx.run`, `nbfx.project.context`, `nbfx.project.actions`,
  `nbfx.propertysheet`, `nbfx.ui`, and `nbfx.file.actions`.
- **Registration-class fold (Part A, §21.3):** `@FxActionReference` moved onto the owning classes —
  `PropertiesView` (`Menus/Window/IDE Tools`, 10), `CutCommand`/`CopyCommand`/`PasteCommand`
  (`ContextMenus/Editor`, 10/20/30), `FindUsagesCommand` (`ContextMenus/Editor`, 140, sepBefore);
  `PropertiesMenuRegistrations`/`EditorContextMenuRegistrations`/`FindUsagesContextMenuRegistrations`
  deleted. A Navigator window action was introduced (initially `SELECT_STRUCTURE` under IDE Tools,
  then renamed in M8a to `SELECT_NAVIGATOR`, top-level `Menus/Window`, position 45, on `StructureView`
  with `SelectNavigatorCommand` + `NavigatorCommandsProvider`).
- **Structure/members reactivity (done):** the Navigator/Structure members tree refreshes when the
  selected file changes. It was first committed provisionally on the flat context; §22 reworked it
  onto the active-view lookup and the three-type subscription is now a single `FileObject` result —
  see §22.7 and §22.8.
- **Java Navigator port (done, §23):** `nbfx.structure.java` now holds the full Java navigation
  feature set (Members/Bean Patterns panels, Go to Source/Declaration/Super Implementation/
  Implementation, Inspect Members, editor hyperlinks and override badges, `Navigate` menu and editor
  submenu); `nbfx.api` gained the editor navigation SPIs (`HyperlinkProvider`,
  `EditorAnnotationProvider`, `ElementContextMenuContributor`, `SourceLocation`, `CellBreadth`,
  `Command.getIcon()`) and `nbfx.annotations` the submenu `path`; two runtime defects of the global
  context were fixed on the way (§17.1).

---

## 21. Ownership of the UI: window system, lifecycles and naming (NetBeans parity)

### 21.1 Naming & layer parity (M8)

- Window **Structure → Navigator** (inverts §19.2's old naming; the mode id/`FxArea.navigator` stays,
  matching Swing). Menu item `Window ▸ Navigator` top-level (`SELECT_NAVIGATOR`, position ~45),
  supersedes `SELECT_STRUCTURE` under IDE Tools. Rebase the planned `NbFx/Structure/<mime>` →
  `NbFx/Navigators/<mime>`; the Part B mime context root becomes `ContextMenus/Navigator`.
- Window **Debug → Debugging**; window **Git → Versioning** (NetBeans has no Git window; versioning
  lives in Output/status bar). `NbFx` prefix + `com.gluonhq` codes stay as documented product
  deviations.
- De-SPI the left column: delete `NavigatorProvider`; Projects/Files become plain `ViewProvider`s —
  the `@FxViewRegistration(navigator = true)` flag already carries the marker
  (`JavaFXLaunchApp:464` drops the `instanceof`). Project lifecycle moves to the open-project model /
  `ProjectRegistry` + `FxActionContext` (views observe, not get fed); expansion/selection persist as
  per-view session state; `revealFile`/icons become a targeted service; search-result/scope
  enumeration targets named views (`SELECT_FILES`/`SELECT_IN_PROJECTS`). `nbfx.api` interface removal
  → `apichanges.xml`. Optional **Favorites** view proves the SPI-less design.
- `@FxActionReference` sits on the owning class (never a dedicated registration class); the same
  applies to the mime-type-based node/tree registration model (Part B design, deferred).

### 21.2 FxWindowSystem & lifecycle (M9)

- **Contract: nbfx-native, NOT `org.netbeans.core.WindowSystem`.** That legacy hook (`init/load/save/
  show/hide` in `o.n.core`) is only ever driven by the stock Swing `GuiRunLevel` on the AWT EDT, and
  when `core.windows` leaves the cluster there is no driver left for it — so nbfx owns its lifecycle
  contract (`FxWindowSystem` in `nbfx.windows`: `init → load → show`, `save`, `resetLayout`; phase
  guards; `FxWindowSystemListener` load/save events mirror the forbidden openide.windows
  `WindowSystemListener`). FX UI-ready is our runLater notifier, replacing Swing `invokeWhenUIReady`.
- **Startup:** `FxGuiRunLevel` (`@ServiceProvider(service = RunLevel.class)`, `nbfx.launcher`) launches
  the JavaFX application (`Application.launch` on a daemon thread) from the `core.startup` `RunLevel`
  sweep — the same SPI the stock `GuiRunLevel` uses, granted via `core.startup`'s friend list. Replaces
  `JavaFXLauncher extends ModuleInstall` + `SwingWindowSuppressor` (the AWT hide-hack); `LauncherInstall`
  keeps only the early `VersioningOptOut`. **Done:** the shell now registers itself as `WindowSink`
  and drives `FxWindowSystem.init/load/show` + `save` on exit (see §21.3 "Window-sink wiring"). The
  stock `GuiRunLevel`
  (`o.n.core`) degrades to its coded benign warning once `core.windows` is absent
  (`core.startup.Main:317` sweeps the `RunLevel` SPI; `GuiRunLevel.java:203`).
- **Two-source merge, verified in NetBeans source** (`WindowManagerParser`/`ModeParser`/
  `PersistenceManager`): layer files (`Windows2/...`) hold the defaults, `config/Windows2Local` holds
  the persisted state and on load the **persisted overrides the layer**, stale modes/components whose
  declaring module vanished are pruned, corrupt `.settings` falls back to the module file, and
  `save()` writes only the local folder. M9.0 mirrors this: `ViewStateStore` (NbPreferences
  `com/gluonhq/netbeans/nbfx/window`) = local folder; `FxWindowSystem.defaultState()` (areas from
  `FxArea.defaults()`, view order/start-up tabs from `ViewRegistry` + `openAtStartup`/`navigator`,
  `NEVER` views never attached) = module folder; merge prunes stale area ids + uninstalled/N-EVER
  views; corrupt/absent → defaults. `AppState`/`SessionRestorer` are NOT rehoused (they own the
  nbfx.ui dashboard/session; kept as the shell-side snapshot source via `WindowSink`).
- **De-core.windows:** no `org.openide.windows`/`org.netbeans.core.windows` deps in nbfx code;
  `check-purity` extended; `cluster.properties`: drop `core.windows` from `nb.cluster.platform` and
  `validation.nb.cluster.platform`; trim its dependents (`applemenu` → existing `nbfx.macos`;
  assess `core.nativeaccess`, `core.multitabs`, `core.kit`, `ide/spi.palette`, `java.lsp.server`);
  resolve residual references via the build's auto-disable diagnostics; packaging guard asserts no
  Swing windowing jar in the distribution.

### 21.3 Status

- **Done this round:** Part A (see §20.1) — the registration-class fold + the Navigator window
  action (initially `SELECT_STRUCTURE`, renamed below). **M8a** — naming & layer parity: the
  Structure window is now **Navigator** (`Window ▸ Navigator`, `SELECT_NAVIGATOR`, top-level
  `Menus/Window` position 45; command/provider classes renamed `SelectNavigatorCommand`/
  `NavigatorCommandsProvider`); **Debug → Debugging**; Git window → **Versioning**; the Git window's
  menu ref folded onto `GitStatusViewProvider` (`GitMenuRegistrations` deleted). View ids/mode
  (`structure`, `debug`, `git`, `navigator` area) stay stable. Cluster + purity + tests green.
- **M9.1** — window metadata: `@FxViewRegistration` gains `area()`/`openAtStartup()`/
  `persistenceType()` (nested `ALWAYS/NEVER/ONLY_OPENED`); `ViewRegistry` resolves `area` attr →
  `FxArea` else default-by-location; view record extended; headless unit-test props.
- **M9.0** — nbfx-native window system in `nbfx.windows` (no NetBeans `WindowSystem` interface, see
  §21.2): `FxWindowSystem` lifecycle + phase guards + `FxWindowSystemListener`; `WindowSink` seam
  (FX shell adapter; nothing wired yet); `ViewStateStore` (persisted = `Windows2Local` analogue,
  NbPreferences) merged over `FxWindowSystem` layer defaults (areas/start-up tabs), stale pruned,
  corrupt→defaults, `resetLayout`; `WindowState`/`AreaState` records. 10 headless tests in-memory.
  Cluster + purity green.
- **M9.2 + M9.4** — boot swap + de-`core.windows` (folded): `FxGuiRunLevel`
  (`@ServiceProvider(service=RunLevel.class)`, `nbfx.launcher`) launches JavaFX from the `RunLevel`
  SPI (`Main:317`, after module enable), exactly like the stock `GuiRunLevel` — access to the
  friend-restricted `org.netbeans.core.startup` package is granted by adding
  `com.gluonhq.netbeans.nbfx.launcher` to `core.startup`'s `<friend-packages>` (the same mechanism
  that lets `org.netbeans.core` use `RunLevel`). `JavaFXLauncher` ModuleInstall shrinks to
  `LauncherInstall` (validate-only: `VersioningOptOut`, which must run before module restore);
  `SwingWindowSuppressor` deleted. `nbfx.launcher` drops `openide.windows` (keeps `core.startup`
  friend + the legacy `projectuiapi.base` app-enable bridge, as does `nbfx.ui`); `nbfx.ui` drops its
  unused `openide.windows` dep. `cluster.properties`: `core.windows` removed from
  `nb.cluster.platform` + `validation.nb.cluster.platform`. Purity excludes trimmed, deps 0.
  Headless smoke with `core.windows` removed: platform boots; `core.kit`/`core.multitabs`
  (+project)/`texttools`/`ide.kit`/`applemenu` auto-disable on the missing module/capability (benign);
  `FxGuiRunLevel` runs and launches JavaFX (friend access confirmed at runtime); no window system
  booted. `core.startup` rebuilt so its jar carries the new friend entry.
- **Window-sink wiring** — the shell now drives the window system: `FxWindowSystem` is a
  `@ServiceProvider(service = FxWindowSystem.class)` with a pluggable `WindowSink` (`setSink`; lazy
  global-`Lookup` resolution; a no-op sink until the shell registers, so headless tests still run);
  `JavaFXLaunchApp implements WindowSink` and its `start()` does
  `Lookup.getDefault().lookup(FxWindowSystem.class)` → `setSink(this)` → `init()`/`load()`/`show()`
  (falls back to `stage.show()` when no system is registered), saving via `windowSystem.save()` in
  both `requestQuit()` and `stop()`. Main-window geometry moved out of `AppState` into
  `WindowState`/`ViewStateStore`; `AppState` keeps a legacy `persistedWindowBounds()` reader that the
  sink consults once for migration (when no window-system geometry is stored), and
  `AppState.isVisibleOnAnyScreen` is now public. The sink preserves the macOS maximized detection
  (flag *and* fills-screen) and tracks a separate restore-size. Areas/view payloads are left empty
  for now (layout stays with `AppState`/`SessionRestorer`, per the chosen scope). Cluster + purity +
  `nbfx.windows` (14) and `nbfx.ui` (4) unit tests green.
- **`openAtStartup` in the shell (stopgap, §23):** until the window system's areas drive the layout,
  `JavaFXLaunchApp.introduceDefaultViews()` docks every non-navigator view registered
  `openAtStartup` that the user has never been offered (first run, a newer version bringing a new
  view, and `Reset Windows`), recording them in `AppState` (`views.introduced`) so a view closed by
  the user stays closed — the shell-side counterpart of `FxWindowSystem.defaultState()`'s start-up
  tabs. `ViewTabs` renders the registration's `iconName` on the tab (16 px, resolved like an
  `iconBase`); `ViewRegistry.find(id)` serves it.
- **M9.3** — purity extension + packaging guard: `check-purity` now also forbids `import
  org.netbeans.core.windows` in nbfx sources, and a new `check-packaging` target asserts the nbfx
  application's clusters (`platform`/`nbfx`/`java`/`ide`/`extide`/`apisupport`, i.e.
  `clusters.config.nbfx.list`) ship no `core.windows` jar (module or locale), so a built distribution
  cannot silently pull the Swing window system back in transitively. Verified 0 violations and
  confirmed the guard fires on a planted jar.
- **M8b (de-SPI the left column), in progress.** Target: delete `NavigatorProvider`; Projects/Files
  become plain `ViewProvider`s (the `@FxViewRegistration(navigator = true)` flag is the marker); the
  views observe the open-project model instead of being fed; expansion/selection persist as per-view
  `ViewStateStore` payloads; `revealFile`/icons become targeted services. Executed incrementally:
    - **Step 1 (done):** targeted services `ProjectIconProvider` (`nbfx.api.project`) and
      `NavigatorRevealer` (`nbfx.api.view`). `ProjectNavigatorImpl` implements/registers
      `ProjectIconProvider` (`iconName`/`createIcon` variants); `WindowEnvironmentImpl`
      implements/registers `NavigatorRevealer` (the shell wires `revealFileInOwningNavigator`).
      `SearchIcons`/`UsagesIcons`/`ScopeChooser`, `SearchResultsPane` and the shell's recent-project /
      project-switching icons no longer reference `NavigatorProvider` for icons or reveal; the
      `getProjectIconName`/`getProjectIcon` methods were removed from `NavigatorProvider`.
    - **Step 2 (done):** inverted the project-open flow. The shell now opens the project in
      `ProjectRegistry` first (`openProject` stashes pending expansion/selection, then
      `projectRegistry.open`); the navigators observe `ProjectRegistry.getOpenProjects()` (subscribed in
      `getView()`) and add/drop their trees, cancelling their own load on removal. `onProjectLoaded`
      only does the post-load bookkeeping; a failed/cancelled load closes the registry entry
      (`cancelProjectLoading`), which the views observe. The shell no longer calls
      `addProject`/`removeProject`/`removeAllProjects`.
    - **Step 3 + 4 (done):** `NavigatorProvider` deleted. The two views are plain `ViewProvider`s
      registered under it (the `@FxViewRegistration(navigator = true)` flag is the marker; the
      `instanceof` and the `manifest.mf` `OpenIDE-Module-Provides` entry are gone). The shell-facing
      navigator concerns moved to `ViewProvider` **default methods** (`revealFile`,
      `restoreExpandedPaths`/`restoreSelectedPath`, `getExpandedPaths`/`getSelectedPath`,
      `setOnProjectLoaded`/`setOnProjectLoadFailed`, `selectedFile`) — chosen over per-view
      `ViewStateStore` payloads, so expansion/selection still persists through `AppState`/`SessionRestorer`
      for now. The shell resolves navigators by filtering `lookupAll(ViewProvider.class)` against the
      registrations' navigator ids. `apichanges.xml` records the removal and the new defaults/services.
- **Part B** mime-type node registration remains design-only.
- **Done (§22):** active-view lookup (`ViewProvider extends Lookup.Provider`; the global context
  follows the active component's lookup) — see §22.7. It was the prerequisite for reworking the
  Structure/members reactivity and for the §21.1 de-SPI; also the substrate for future view
  sub-views (code/history/preview).

---

## 22. Active-view lookup — `TopComponent` parity

**Goal.** Replace the reconstructed global action context with the Swing model: the context *is* the
active component's lookup. This removes the shared-slot selection, the per-consumer precedence
heuristics, and the special-casing of editors, and is the substrate the Structure/members reactivity
fix (the provisional patch, see §22.8) was reworked onto. **Status: implemented — see §22.7.**

### 22.1 Problem (evidence)

nbfx builds the context from three shared observables flattened into one `AbstractLookup`
(`NbfxGlobalContextProvider.java:42-82`) instead of delegating to the active component as Swing does
(`GlobalActionContextImpl:149-163` — `Lookups.proxy(this)` whose `getLookup()` returns
`registry.getActivated().getLookup()`, refreshed on `TopComponent.Registry.PROP_ACTIVATED`).
Consequences:

- `FileSelectionContext` is a single global slot that **every** navigator writes
  (`JavaFXLaunchApp:155-162`), so the Logical and Physical views answer for each other; the shell
  already works around this by reading the focused provider directly
  (`focusedNavigatorFile`, `JavaFXLaunchApp:796-811`).
- Consumers re-derive "which selection wins" with an `instanceof NavigatorProvider` precedence:
  `PropertiesView.updateContext` (`PropertiesView.java:60-63`) and `StructurePanel.selectedFile`
  (`StructurePanel.java:130`; moved to `FxStructurePanel.selectedFile` by the provisional patch).
- Editors are not `ViewProvider`s, so they need the parallel `EditorContext` rather than contributing
  a lookup like the editor `TopComponent`.

### 22.2 Design

1. **`ViewProvider` becomes a lookup provider.** `ViewProvider extends Lookup.Provider` with
   `default Lookup getLookup() { return Lookup.EMPTY; }`, mirroring `TopComponent.getLookup()`. The
   lookup describes *what the view currently represents as the current selection* (its content).
2. **Active component.** The shell already tracks the focused pane's selected tab
   (`updateActiveView`, `JavaFXLaunchApp:770-774`; `NbfxTabPane.viewOf:1081`, `documentOf:289`).
   Extend this to the active *component lookup*: a view tab contributes `view.getLookup()`; an editor
   tab contributes the editor lookup.
3. **Editor is a `Lookup.Provider`.** Model `EditorDocument` (or the editor tab) as a `Lookup.Provider`
   so the editor side mirrors the editor `TopComponent` instead of the out-of-band `EditorContext`
   entry into the flat context.
4. **Context provider becomes a follower.** `NbfxGlobalContextProvider` becomes a stable
   `Lookups.proxy(activeComponent)` (never rebuilt, so consumers' cached `Lookup.Result`s keep firing)
   instead of an `InstanceContent`-fed `AbstractLookup`. The shared observables stop being flattened;
   each component feeds its own lookup.
5. **Navigators publish per-view selection.** Each navigator (Projects/Files) feeds its lookup from
   `selectedFile()`/`selectedFiles()` + project, so two navigators no longer interfere. The shared
   `FileSelectionContext` selection slot retires; only its focus signal stays (it is shell state, not
   selection, and drives file-vs-editor Cut/Copy/Paste dispatch, §20.1).
6. **Follower views and the empty context (chosen: shell fallback).** A follower view
   (Properties/Versioning/Structure/Usages) may leave its lookup `EMPTY`. Rather than requiring every
   follower to publish its content, the provider keeps the *last selection-bearing component's* lookup
   when the active view exposes nothing (`lastSelection`), so activating a follower does not blank the
   context; only a genuine selection (a view's file/document/project, or an editor document) replaces
   it. This is the **implemented** mechanism (§22.4 step 5); the alternative — every follower publishes
   what it presents, matching Swing's `NbSheet`/navigator activated nodes — is deferred until a follower
   actually needs to change the selection. Either way the `instanceof NavigatorProvider` heuristics are
   deleted: consumers read `context.lookup(...)` flatly.

### 22.3 Sub-views (code / history / preview) — forward-looking infrastructure

A `ViewProvider` will later own **multiple sub-views** (e.g. a Source view with Code/History/Preview
tabs, as in the Swing multiview / component-tab model), each with its own selection. `getLookup()`
must therefore resolve to the **active sub-view's** lookup (a proxy that re-publishes when the
sub-view selection changes), aggregating any outer-view entries. This is deliberately planned now so
the `ViewProvider` contract is shaped for it:

- The single `getLookup()` returns a *proxy* over `activeSubview.getLookup()` unioned with the view's
  own entries — not a fixed lookup.
- Switching sub-views must be an observable/re-publish event, exactly like `setActivatedNodes`.
- The shell's active-component plumbing (§22.2 design item 2) then needs no change: it keeps publishing
  `view.getLookup()`, which now follows the sub-view.
- Downstream, per-sub-view session/layout state and per-sub-view `ViewStateStore` payloads are the
  natural extension of the M9 window metadata; defer the storage design until a concrete view needs
  it (the de-SPI work in §21.1 is the first that could introduce a real multi-section view).

### 22.3.1 Multi-view Swing reference

Swing's editor multi-view (`MultiViewHandler`/`MultiViewDescription`/`DelegatingController`) and the
component-tab host each expose the *active inner component's* activated nodes; the `TopComponent`'s
lookup is effectively a proxy over the active inner view. nbfx mirrors this with the §22.3 proxy.
Evaluate `platform/core.multiview` when implementing.

### 22.4 Migration / sequencing

1. Add `ViewProvider.getLookup()` (default `EMPTY`) — no behavior change; `nbfx.api` API change →
   `apichanges.xml`.
2. Shell composes the active-component lookup (view or editor) and `NbfxGlobalContextProvider`
   becomes the proxy follower (§22.2 design items 2–4).
3. Make `EditorDocument` a `Lookup.Provider` and drop its out-of-band context entry.
4. Navigators publish their selection lookup (§22.2 design item 5); retire the shared `FileSelectionContext`
   selection slot (keep the focus signal).
5. Follower views publish what they present; delete the `instanceof NavigatorProvider` precedence in
   `PropertiesView`/`StructurePanel`/`GitStatus*` (§22.2 design item 6). **Chosen instead:** a follower
   view may expose an empty lookup and the provider then keeps the last selection-bearing component
   (`lastSelection`), so the precedence is deleted without making every follower a lookup provider.
6. Redo the Structure/members reactivity on the active-view lookup; delete the three-type
   subscription and `FxStructurePanel.selectedFile` heuristic (see §22.8).

### 22.5 Risks

- **Proxy stability.** The global context must be a `Lookups.proxy`/`ProxyLookup` that *follows* the
  active component; rebuilding it breaks consumers holding `Lookup.Result`s.
- **Follower-view empty context.** If a follower view is active but exposes an empty lookup, the
  Properties/Structure content would blank; the provider's `lastSelection` fallback keeps the previous
  component instead (§22.2 design item 6). Every follower may still override `getLookup()` to publish
  what it presents when that becomes desirable.
- **Threading.** Views publish lookups on the FX Application Thread; the context swaps on focus/tab
  change (`updateFocusScope`/`updateActiveView`).
- **API change** → `nbfx.api` `apichanges.xml`; overlaps the §21.1 de-SPI removal of
  `NavigatorProvider`.
- **Migration ordering.** Commands already migrated to `FxActionContext` (build, file actions, find)
  must keep resolving while the provider changes shape.

### 22.6 Verification

- Extend `NbfxGlobalContextProviderTest` for active-component switching (view ↔ editor).
- Add per-view-selection tests (two navigators do not answer for each other).
- Headless smoke; purity green.

### 22.7 Status

**Implemented.** `ViewProvider`/`EditorDocument` are `Lookup.Provider`s; `NbfxGlobalContextProvider`
is a stable proxy that follows the active view/editor document with a `lastSelection` fallback;
navigators publish their own selection; `FileSelectionContext.selectedFiles` is retired (only the
focus signal remains); the `instanceof NavigatorProvider` precedence is deleted from
`PropertiesView`/`FxStructurePanel`; the Structure panels' three-type subscription is narrowed to a
single `FileObject` result. `NbfxGlobalContextProviderTest` covers active view/editor switching, two
navigators, and the follower fallback. The three-type subscription narrowed here means the provisional
patch in §22.8 is now superseded (the `FxStructurePanel.selectedFile` heuristic is gone); see §22.8 for
what the patch originally did.

**Corrections found at runtime (§23):** the provider was not actually the one
`Utilities.actionsGlobalContext()` used (missing `position`), and the `lastSelection` fallback hid the
active editor behind an earlier navigator selection; both fixed, the second with a test — see §17.1.
The `JavaStructurePanel` placeholder this section refers to is gone; its role is played by the
`DocumentTracker` of `nbfx.structure.java` (§23).

### 22.8 Provisional patch — Structure/members reactivity (commit `nbfx structure: refresh Navigator members on selection change`)

**Superseded by §22.7** (kept as history). Until §22 lands, the Structure ("Navigator") window's
members were kept in sync by a **provisional** patch, committed separately and to be reworked onto the
active-view lookup:

- `JavaStructurePanel` subscribes, in `panelActivated`, to the global context's `FileObject`,
  `EditorDocument` and `ViewProvider` results, rebuilds the members tree on change (coalesced by the
  last file), and unsubscribes in `panelDeactivated` — mirroring upstream `NavigatorController`
  (whose contract is that the *panel* listens while active; panels are not re-activated on a
  same-content-type selection change).
- `FxStructurePanel.selectedFile(FxActionContext)` centralizes the file resolution (navigator
  selection when a navigator is active, else editor document, else context file); `StructurePanel`
  now calls it.

Why provisional: it hard-codes exactly what §22 removes — the three-type subscription and the
`instanceof NavigatorProvider` precedence (§22.1). Under §22 these collapse to reading the active
component's lookup. Tracked here so the rework in §22.4 step 6 is not forgotten; the code carries no
in-line "provisional" marker.

---

## 23. Java Navigator (`nbfx.structure.java`)

The Java Navigator mapping:

- **Module split.** `nbfx.structure` stays the Navigator *window* (`StructureView`, `StructurePanel`,
  `FxStructurePanel` SPI, `Window ▸ Navigator`); `nbfx.structure.java` is the analogue of NetBeans'
  `java.navigation` (+ the `beans` navigator panel): `ui.ClassMemberPanel`/`ui.BeanPanel` are the
  `FxStructurePanel`s for `text/x-java` (`@ServiceProvider` positions 100/200) over
  `ClassMemberPanelUI`/`BeanPanelUI` (the trees, filter bar, row menus), each fed by a
  `DocumentTracker` while it is the active panel; `model` holds the scanners/resolvers
  (`MembersScanner`, `BeanPatternsScanner`, `GoToResolver`, `OverriddenScanner`, `TypeResolver`);
  `actions` holds `GoToCommand` (4 kinds), `InspectMembersCommand` (2), `GoToHyperlinkProvider`,
  `OverriddenAnnotationProvider`, `FileMembersMenuContributor`, `JavaNavigationCommandsProvider`.
- **Context, not shell observables.** The tracker reads `EditorDocument` then `FileObject` from
  `FxActionContext` (results kept in fields, see the §17.1 pitfall): an active editor is rescanned
  400 ms after each edit (`EditorDocument.textVersionProperty()`) and keeps the caret's row selected;
  a file selected in a project view is scanned once, no row selected; a non-Java file clears the
  panel (the host then shows "No structure available"). Scans are suspended while the panel is off
  screen and resumed when shown. *Inspect Members* sets a type aside from the context (history combo
  in the panel toolbar, `<auto>` returns); a re-activation of the editor the inspection started from
  keeps it, turning to another file or editing the active document ends it.
- **Window-scoped commands keep `EditorContext`.** The Go to / Inspect commands are editor actions
  with `createScoped` variants for detached windows, the same deliberate exception as Find Usages
  (§20.1); they enable on a Java active document.
- **Layer registrations sit on the owning classes (§21.1).** `GoToCommand` declares the editor's
  `Navigate` submenu (`@FxMenuRegistration(path = "ContextMenus/Editor", displayName =
  "#MENU_Navigate", separatorBefore)`) and its `Menus/Navigate` + submenu refs; `InspectMembersCommand`
  its own. The shell's `MenuRegistrations` only declares the top-level `Navigate` menu (position 40,
  between View and Tools as in NetBeans) and the shell-owned `Select in Projects` / `Select in Files`
  entries (`SELECT_IN_FILES` is new; both carry Shift+Shortcut+1/2 for display, the keys being bound
  per window by `NbfxTabPane`). `Window ▸ Navigator` stays on `StructureView` (position 45).
- **Editor SPIs added to `nbfx.api`** (`apichanges.xml` `editor.navigation.spi`):
  `HyperlinkProvider` (Shortcut+Click → declaration, Alt+Shortcut+Click → implementations popup;
  `HyperlinkSupport` in `nbfx.editor`), `EditorAnnotationProvider`/`EditorAnnotation` (gutter glyphs
  for overriding/overridden methods; `AnnotationSupport` + `MarkLineNumberDecorator`),
  `ElementContextMenuContributor` (items for a source element shown outside the editor — Find Usages
  on a Navigator row), `SourceLocation`, `CellBreadth` (shared cell width against horizontal scroll-bar
  oscillation), `EditorDocument.textVersionProperty()`/`getCaretScreenPosition()`, the Navigate
  `ActionIds`, and `Command.getIcon()` (a contributed command's own menu/toolbar icon — the shell's
  `ActionBars.ICONS` only covers shell commands; the macOS system menu bar needs the 16 px file
  itself, hence `navigator-16.png`).
- **Kept as data, not behaviour:** `@FxViewRegistration.iconName` on `StructureView` (rendered by
  `ViewTabs`, §21.3) and `openAtStartup = true` (honoured by the shell stopgap, §21.3).
- **Deferred:** styling of the panels (the `navigator.css` of the old module is attached to the panel
  nodes as is), the Javadoc window button (placeholder, disabled), the Hierarchy view, and persisting
  the inspect history. `docs`-level: §19.2's `NbFx/Structure/<mime>` registration folder was never
  needed — panels resolve by `contentTypes()` through the global Lookup.
- **Verification:** all nbfx modules build; `nbfx.annotations` (1), `nbfx.api` (15), `nbfx.windows`
  (14), `nbfx.ui` (5) unit tests green; `check-purity` 0 violations; generated layers inspected
  (`NbFx/Views/structure.instance` with `openAtStartup`/`iconName`, `Menus/Navigate`,
  `ContextMenus/Editor/Navigate`).

---

## 24. Sliding sides (minimized window groups)

The NetBeans auto-hide behaviour (`core.windows` `view/ui/slides`: `SlideBar`, the
`*SlidingSide` modes, `TabbedSlideAdapter`'s minimize/pin buttons) rebuilt on the nbfx dock area.
What the user sees matches the Swing IDE: a *Minimize Window Group* button at the right end of the
tab header of every pane docked next to the editor pane (never the editor pane itself; a view dragged
into it loses the button, an editor group dragged next to it gains one); clicking it collapses the
pane to a group of buttons - *Restore Window Group*, then one button per tab with its icon and its
title written along the bar - in a bar along the edge the pane was nearest to (left, right or bottom,
judged against the editor pane exactly as `DefaultView.guessSlideSide` does, plus the top for a pane
above the editors, a side NetBeans does not slide to); hovering a button
slides the tab's content in over the area after 200 ms and out 400 ms after the mouse leaves;
clicking it slides the tab in to stay, with the focus, until the focus leaves it, the mouse is
pressed elsewhere or Escape; the slid-in panel's header offers *Dock* (that one tab back, the
rest of the group staying minimized) and *Close*, and its inner edge drags to resize.

- **Model: `nbfx.docking`.** `DockArea.minimize(pane)` takes any pane but the main one out of the
  tree with its components, remembering an `Anchor` (the neighbouring leaf, the side, and the share
  of their joint space it had - the same anchor an emptied permanent pane now records, so it also
  comes back at its former size); `restore(pane)` puts it back there, or along the area's edge when
  the leaf is gone; `minimizedPanes()` is an observable list the host follows; `anchorOf` tells where
  a pane goes back. An `Anchor.into(leaf)` makes a single component minimized out of a pane rejoin
  it. An anchor to a leaf that is out itself (Projects/Files and the Navigator below it both
  minimized, in either order) is handed over on restore: the returning pane takes the place its
  anchor leaf would go back to and that leaf is re-anchored next to it on the opposite side with the
  rest of their joint space, so the two come back as they were whichever returns first; a pane
  anchored *into* a minimized pane restores that pane and joins it (the NetBeans "previous mode"
  constraints, without a mode tree). A minimized primary stays out when it receives content (only `restore` brings it back, so a
  view opened into a minimized Projects group joins the group); an emptied minimized docked pane is
  disposed of; `applyTree` restores everything first (Reset Windows). `paneBelow` of a minimized
  primary is `null`, and `Docking.paneAt(LEFT_BOTTOM)` falls back to the (minimized) navigator pane.
  `build` now applies the shape's dividers, so the tree read back is the one built (the constructor
  used to leave the split pane's 0.5 default in place of `FxArea.EXPLORER`'s 0.3).
- **View: `nbfx.ui` `SlidingArea` + `SlideBar`.** `SlidingArea` wraps the area (it is what the main
  window's `BorderPane` shows; `Docking.slides()`): a `BorderPane` with the three bars around the
  area, and the slide-in panel as an unmanaged child positioned by `layoutChildren` from the bars'
  preferred sizes and the pane's slide-in size (its width/height when minimized, resizable, kept per
  pane). The panel is an `NbfxTabPane` of its own - the `PaneRole.SLIDE` pane, never persisted -
  that borrows the *tab itself* while it shows (as NetBeans shows the slid TC in a tabbed
  container): the tab keeps its header, its close button and its header drag, so it can be dragged
  to another pane, a split zone or out to a new window, and the pane's header control is *Dock*. On
  slide-out the tab goes back to its group at its place; if it left on its own (closed, dragged,
  docked) the panel just hides and the group is refreshed. While on loan the tab still belongs to
  its group - `SlidingArea.tabsOf`/`selectedOf` list it, the slide bar shows it, persistence
  captures it there - and `Docking.isEmpty` (the host's emptiness for the area) counts the lending
  pane as not empty, so the area does not dispose of a one-tab group meanwhile. The slide pane takes
  no drops (a tab or file landing there would vanish with the panel) and does not activate/focus on
  selection (a hovered-in tab is only looked at; clicking into it focuses and activates as any
  pane, so `NbfxTabPane.focusedPane()` naturally reports it and the global context follows).
  `NbfxTabPane` owns the header button (a child of the control laid out over the skin's header; the
  `header-control` pseudo-class makes the header leave room for it; state recomputed on parent/scene
  changes) and reveals a tab of a minimized pane by sliding it in (`selectTabAndMoveToFront`, so
  Window ▸ Projects, Open File and friends work on a minimized group); `editorHome` never opens new
  files into a minimized pane or the slide pane. The tab context menus and *Window ▸ Configure
  Window* got working *Minimize* (one tab, anchored into its pane) and *Minimize Group* items,
  enabled for minimizable panes only, and *Dock* works on a slid-in tab. `JavaFXLaunchApp` treats
  the sliding area as the focus scope. *Dock* sends a tab back to where its group came from, as
  NetBeans docks a window back to its previous mode, so tabs docked one by one rejoin
  (`SlidingArea.dock`/`dockOutOf`): a group anchored *into* a pane of the area joins it; otherwise
  the first dock out of a group (a minimized permanent pane such as Projects/Files, or a docked one)
  restores the group's own pane at its anchor holding that tab, and leaves the other tabs minimized
  in a docked pane anchored into it (the one already anchored there, or a new one on the same side
  with the same slide-in size); when the pane a group came from is itself minimized, that pane comes
  back with the tab and the rest of both groups stay minimized; later docks and Restore Window
  Group join that pane. The tab icons: `ViewTabs` draws a view's registered `iconName` on its
  header, and the slide buttons copy the header's icon, so Projects and Files were given the icons
  their Window-menu items use (`projectTab.png`/`filesTab.png`, now resources of
  `nbfx.project.ui.java` beside their views), and Output its own (`output.png` on the view,
  `output-16.png` on `SelectOutputCommand.getIcon()` for *Window ▸ Output*, both in `nbfx.output`,
  the pattern `SelectNavigatorCommand` set) - unlike NetBeans, which hides the icon on a view tab
  until it slides in or is dragged elsewhere, a tab keeps its icon wherever it shows.
- **Persistence.** `AppState.PaneLayout` gained `Minimized(side, size, anchorIndex, anchorSide,
  anchorShare, anchorPane)`, serialized into the role field (`DOCKED@LEFT@250.0@1@BOTTOM@0.35@-1`;
  `INTO` for an anchor into a leaf; `anchorPane` indexes the layout's panes when the anchor is
  another minimized pane, set in a second pass of `applyLayout` once every minimized pane exists;
  old lines read as showing panes; the editor and detached panes never read as minimized). `Layout.dockedPanes()` excludes minimized panes (they are not tree leaves);
  `withoutEditors` remaps the anchor indexes to the pruned tree. `SessionRestorer` captures the
  state from `SlidingArea`/`DockArea.anchorOf` (anchor = index of the leaf among the persisted
  leaves) and, on restore, minimizes the permanent pane or a new docked pane again - anchored only
  when the area took the recorded shape - before moving the tabs in.
- **Styling:** `styles.css` (`.nbfx-icon-button`/`.nbfx-glyph` shapes for minimize, restore and
  close; `.nbfx-slide-bar`, `.nbfx-slide-button`, `.nbfx-slide-panel`), kept to the tab-header
  palette; proper theming later, as with the Navigator.
- **Deferred:** dragging the slide *buttons* themselves (the slid-in tab's header already drags)
  and dropping tabs on a bar; a context menu on the slide buttons; Maximize; the slide-in
  animation is a short fade rather than a slide.
- **Verification:** `nbfx.docking` got a headless unit-test setup (same `glass.platform=Headless`
  properties as `nbfx.api`) and `DockAreaTest` (10: leave/return with share, docked pane below the
  navigator, disposal when emptied, anchor into a pane, minimized primary ignoring content, empty
  primary staying hidden after restore, `applyTree`, main pane refused, the four minimize/restore
  orders of the navigator pane and the pane below it, a pane anchored into a minimized pane);
  `AppStateLayoutTest` (7: round trips with leaf, into and minimized-pane anchors, legacy line,
  editor/detached never minimized, anchor remapping of leaves and panes). `nbfx.ui` (12),
  `nbfx.docking` (10) green; `check-purity` 0 violations; docking, ui, launcher and
  `project.ui.java` build. Checked by hand in `ant tryme`: minimizing/restoring the left groups,
  docking tabs one by one back into one pane, the slid-in tab's header dragging to other panes and
  windows, the four sides; persistence across a restart with both left groups minimized still to
  be confirmed.

---

## Appendix: Key reference locations

- Layer processors: `platform/openide.filesystems/src/org/openide/filesystems/annotations/`
  (`LayerGeneratingProcessor.java`, `LayerBuilder.java`).
- Swing action processor to avoid: `platform/openide.awt/src/org/netbeans/modules/openide/awt/ActionProcessor.java`.
- Output SPI: `platform/api.io/src/org/netbeans/spi/io/InputOutputProvider.java`,
  `platform/api.io/src/org/netbeans/api/io/IOProvider.java`.
- Options reference contract:
  `platform/options.api/src/org/netbeans/spi/options/OptionsPanelController.java`,
  `platform/options.api/src/org/netbeans/spi/options/OptionsCategory.java`.
- Plugins engine API: `platform/autoupdate.services/src/org/netbeans/api/autoupdate/`
  (`UpdateManager`, `OperationSupport`, `UpdateUnit`, `UpdateElement`, `InstallSupport`, ...).
- Existing status bar: `nbfx/.../launcher/ui/StatusBar.java:27`.
- Existing editor settings: `nbfx/.../api/editor/EditorSettings.java`.
- Existing project bridge: `nbfx/.../launcher/project/ProjectRegistryImpl.java:191-234`.
- Cluster config: `nbbuild/cluster.properties:99`, `:1337-1351`.
- Docs to fix: `nbfx/.../api/apichanges.xml`, `nbfx/.../api/arch.xml`.
- Swing Properties window: `platform/core.windows/src/org/netbeans/core/windows/view/ui/NbSheet.java`
  (+ `actions/GlobalPropertiesAction.java`), `platform/core.ui/.../resources/properties.wstcref`.
- Swing modes: `platform/core.ui/src/org/netbeans/core/ui/resources/windowmanager/*.wsmode`,
  `platform/core.io.ui/.../output.wsmode`, `ide/spi.navigator/.../navigator.wsmode`.
- Swing structure Navigator: `ide/spi.navigator/src/org/netbeans/modules/navigator/`
  (`NavigatorTC.java`, `NavigatorController.java`, `ProviderRegistry.java`) and
  `org/netbeans/spi/navigator/NavigatorPanel.java`.
- Swing sliding sides (§24 reference): `platform/core.windows/src/org/netbeans/core/windows/view/ui/slides/`
  (`SlideBar.java`, `SlideGestureRecognizer.java` - the 200/400 ms timers, `SlideBarContainer.java`),
  `platform/core.windows/src/org/netbeans/core/windows/view/DefaultView.java:89` (`guessSlideSide`),
  `platform/o.n.swing.tabcontrol/src/org/netbeans/swing/tabcontrol/plaf/AquaViewTabDisplayerUI.java:249-273`
  (minimize / pin / restore-group buttons), `.../plaf/Bundle.properties` (`Tip_Minimize_Window_Group`,
  `Tip_Restore_Window_Group`, `Tip_Pin`); nbfx: `nbfx/c.gluonhq.netbeans.nbfx.docking/src/.../DockArea.java`
  (`minimize`/`restore`/`Anchor`), `nbfx/c.gluonhq.netbeans.nbfx.ui/src/.../shell/{SlidingArea,SlideBar}.java`.
- Swing Java navigator (§23 reference): `java/java.navigation/src/org/netbeans/modules/java/navigation/`
  (`ClassMemberPanel.java`, `ClassMemberPanelUI.java`, `actions/`), `java/beans/src/org/netbeans/modules/beans/BeanPanel.java`;
  nbfx port: `nbfx/c.gluonhq.netbeans.nbfx.structure.java/src/.../structure/java/{ui,model,actions}/`.
- Swing `@ProjectServiceProvider`/`ActionProvider`:
  `ide/projectapi/src/org/netbeans/spi/project/{ActionProvider,ProjectServiceProvider}.java`.
- Swing global context / active-component lookup (§22):
  `platform/openide.windows/.../GlobalActionContextImpl.java:149-163` (`Lookups.proxy` over
  `registry.getActivated().getLookup()`), `platform/openide.windows/.../TopComponent.java`
  (`getLookup()`/`setActivatedNodes`). Swing multi-view reference:
  `platform/core.multiview/src/org/netbeans/core/api/multiview/MultiViewHandler.java`,
  `platform/core.multiview/src/org/netbeans/core/spi/multiview/MultiViewDescription.java`,
  `.../MultiViewElement.java`, `.../MultiViewElementCallback.java`.
- Swing window system internals (M9 reference):
  `platform/core.startup/.../Main.java:317` (RunLevel SPI sweep),
  `platform/core.startup/.../RunLevel.java`, `platform/o.n.core/.../GuiRunLevel.java:125-233`
  (init/load/show phases), `platform/o.n.core/.../WindowSystem.java`,
  `platform/core.windows/.../WindowSystemImpl.java` (listener events),
  `.../PersistenceHandler.java`, `.../persistence/PersistenceManager.java`
  (`Windows2`/`Windows2Local`, `wswmgr`/`wsmode`/`wstcref`/`wsgrp`/`settings`),
  `.../persistence/RoleFileSystem.java` (per-role overlay), `.../resources/layer.xml`,
  `platform/openide.windows/.../TopComponent.java:1467` (`@Registration(mode, openAtStartup,
  persistenceType)`), `.../persistence/ModuleChangeHandler.java`.
- nbfx launcher (M9.2, done): `nbfx/.../launcher/{FxGuiRunLevel,LauncherInstall}.java` replaced
  `JavaFXLauncher`/`SwingWindowSuppressor`; `core.startup` now lists `com.gluonhq.netbeans.nbfx.launcher`
  as a friend so it can implement `RunLevel`.
- nbfx view/mode substrate: `nbfx/.../api/view/{ViewProvider,ViewManager,DockLocation}.java`,
  `nbfx/.../ui/shell/Docking.java`, `nbfx/.../windows/ViewRegistry.java`.
- Git status element: `nbfx/.../vcs/GitBranchStatusElement.java`.
