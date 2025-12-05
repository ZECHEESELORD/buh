# Menu Toolkit

Fulcrum’s menu toolkit treats every inventory like a playful canvas: tabs, paginated lists, anchored controls, and
virtual grids all sit behind fluent builders so you never juggle slot math alone. This guide walks through a hands-on
flow for building a thriving UI surface and wraps up with an API reference so you can dive straight back into code.

## Step 1: Kick Off the Toolkit

Start by grabbing a `MenuService` reference and sketching a lightweight welcome menu. Keep it tiny so you can focus on
the click handlers and confirm your shading choices feel good.

```java
MenuService menuService = services.menu();

menuService.createMenuBuilder()
    .

title("&6Welcome Hub")
    .

rows(3)
    .

addButton(
        MenuButton.builder(Material.DIAMOND)
            .

name("&aClick Me")
            .

description("&7Opens the fun stuff")
            .

onClick(player ->player.

sendMessage("You poked the gem!"))
        .

build(),
        1,1)
                .

addButton(MenuButton.builder(Material.BARRIER)
        .

name("&cClose")
        .

slot(22)
        .

onClick(Player::closeInventory)
        .

build())
        .

buildAsync(player);
```

## Step 2: Shape the Layout

Anchored controls (via `.slot(index)`) remain fixed while coordinate buttons scroll with the viewport. Blend the two so
players always keep their navigation bar while browsing wider canvases.

```java
MenuButton fixedSearch = MenuButton.builder(Material.COMPASS)
        .name("&eSearch")
        .slot(8)
        .onClick(this::openSearch)
        .build();

menuService.createMenuBuilder()
    .

title("&dCompass Bay")
    .

viewPort(6)
    .

rows(10)
    .

columns(12)
    .

anchor(AnchorPoint.TOP_LEFT)
    .

addButton(fixedSearch)
    .

addButton(itemButton, 3,4)
    .

fillEmpty(Material.BLACK_STAINED_GLASS_PANE)
    .

buildAsync(player);
```

## Step 3: Build a Paginated List

List menus behave like table views: supply a collection (or supplier) and the builder handles pagination, navigation
buttons, and empty states for you.

```java
menuService.createListMenu()
    .

title("&bPlayer List")
    .

rows(6)
    .

addBorder(Material.BLUE_STAINED_GLASS_PANE)
    .

addItems(Bukkit.getOnlinePlayers(),online ->
        MenuDisplayItem.

builder(Material.PLAYER_HEAD)
            .

name("&e"+online.getName())
        .

secondary("&7Level "+online.getLevel())
        .

description("&7World: "+online.getWorld().

getName())
        .

build())
        .

onPageChange((viewer, oldPage, newPage) ->viewer.

sendMessage("Page "+(newPage +1)))
        .

emptyMessage(Component.text("No players online", NamedTextColor.GRAY))
        .

buildAsync(staffMember);
```

## Step 4: Link Menus with Parents

Register a parent menu once, then let child menus declare `.parentMenu(id)` to gain a drop-in back button. This keeps
navigation consistent without writing manual listeners for every child.

```java
Menu mainMenu = menuService.createMenuBuilder()
        .title("&6Main Menu")
        .rows(3)
        .addButton(shopButton, 1, 1)
        .buildAsync(player)
        .join();

menuService.

registerMenuInstance("main",mainMenu);

menuService.createMenuBuilder()
    .

title("&eShop")
    .

parentMenu("main")
    .

addButton(buyButton, 2,2)
    .

buildAsync(player);
```

## Step 5: Cache Templates for Reuse

Heavy menus (large data pulls, expensive icons) should be cached. Save them in the registry and reopen whenever a player
runs the relevant command.

```java
menuService.registerMenuInstance("shop",shopMenu);
menuService.

openMenuInstance("shop",player);

if(menuService.

hasMenuInstance("shop")){
        menuService.

getMenuInstance("shop").

ifPresent(menu ->menuService.

openMenu(menu, viewer));
}
```

## Step 6: Craft a Tabbed Controller

Tabbed menus render a tab strip, divider, and content pane. You define each tab’s id, icon, entries, and optional
divider theme. The builder tracks state in `MenuContext` so titles automatically display breadcrumbs like
`Profile » Privacy`.

```java
menuService.createTabbedMenu()
    .

title("&bProfile Settings")
    .

contentRows(3)
    .

divider(Material.CYAN_STAINED_GLASS_PANE)
    .

defaultTab("privacy")
    .

tab(tab ->tab
        .

id("privacy")
        .

name("&bPrivacy")
        .

icon(Material.LOCK)
        .

divider(Material.LIGHT_BLUE_STAINED_GLASS_PANE)
        .

items(buildPrivacyAnchors())
        .

items(() ->

buildPrivacyEntries(player)))
        .

tab(tab ->tab
        .

id("friends")
        .

name("&eFriends")
        .

icon(Material.PLAYER_HEAD)
        .

divider(Material.ORANGE_STAINED_GLASS_PANE)
        .

items(buildFriendAnchors())
        .

items(() ->

buildFriendEntries(player)))
        .

tab(tab ->tab
        .

id("empty")
        .

name("&cEmpty")
        .

icon(Material.BARRIER)
        .

emptyMessage(Component.text("No records yet", NamedTextColor.GRAY)))
        .

tabScroller(TabbedMenuBuilder.TabScrollerConfig.defaults())
        .

onTabChange((viewer, oldTab, newTab) ->
        Message.

debug("settings.tab.changed",oldTab, newTab)
            .

skipTranslation()
            .

send(viewer))
        .

buildAsync(player);
```

## Step 7: Scroll Oversized Inventories

Virtual grids unlock large shops or vaults. Declare the visible viewport first, then the backing rows and columns. Add
scroll buttons so explorers can cruise through the map.

```java
menuService.createMenuBuilder()
    .

title("&5Treasure Vault")
    .

viewPort(6)
    .

rows(20)
    .

columns(15)
    .

anchor(AnchorPoint.CENTER)
    .

addButton(MenuButton.builder(Material.COMPASS)
        .

name("&eSearch Items")
        .

slot(8)
        .

onClick(this::openSearch)
        .

build())
        .

addScrollButtons()
    .

onScroll((viewer, oldRow, oldCol, newRow, newCol) ->
        viewer.

sendMessage("Viewing row "+newRow +", column "+newCol))
        .

buildAsync(player);
```

Populate the grid by looping rows and columns: `addButton(item, row, col)` scrolls with the viewport, while
`.slot(index)` stays pinned.

## Step 8: Stream Live Data

Suppliers rebuild menu entries at render time, which keeps diagnostics and watcher menus fresh. The lambda executes
whenever a player opens the menu or you call `.refreshMenu(player)`.

```java
menuService.createListMenu()
    .

title("&aLive Diagnostics")
    .

rows(6)
    .

addItems(() ->

buildSystemMetrics())
        .

emptyMessage(Component.text("No metrics yet", NamedTextColor.GRAY))
        .

buildAsync(player);
```

## Step 9: Watch Navigation and Debug Hooks

Page and tab callbacks help you instrument UX changes. When you need a visual check, run
`/menudebug TABBED|DYNAMIC|BIG` (staff only) to summon sample menus and verify textures, pagination, and scrollers.

```java
menuService.createListMenu()
    .

title("&eChangelog")
    .

rows(5)
    .

addItems(changelogEntries)
    .

onPageChange((viewer, oldPage, newPage) ->
        metrics.

pageFlips().

incrementAndGet())
        .

buildAsync(viewer);

menuService.

getOpenMenu(player).

ifPresent(menu ->{
        // poke the active menu if needed
        menuService.

refreshMenu(player);
});
```

## Reusable Building Blocks

### MenuButton

Use `MenuButton` for any interactive slot so lore formatting, cooldowns, and sounds remain consistent across the
network.

```java
MenuButton purchaseButton = MenuButton.builder(Material.EMERALD)
        .name("&aBuy Item")
        .secondary("&7100 coins")
        .description("&7Left: purchase", "&7Right: info")
        .amount(5)
        .onClick(player -> purchaseItem(player))
        .onClick(ClickType.RIGHT, this::showInfo)
        .sound(Sound.UI_BUTTON_CLICK)
        .cooldown(Duration.ofSeconds(3))
        .slot(22)
        .build();
```

### MenuDisplayItem

Display items are static tiles or dividers. They share the same formatting helpers but skip click handlers so they are
perfect for stats or separators.

```java
MenuDisplayItem infoTile = MenuDisplayItem.builder(Material.BOOK)
        .name("&eInformation")
        .secondary("&7Server Stats")
        .description("&7Players: " + playerCount, "&7TPS: " + ticksPerSecond)
        .lore("&7Extra detail line")
        .slot(4)
        .build();
```

## MenuService Patterns

* `openMenu(menu, player)` keeps interactions centralized in one service.
* `closeMenu(player)` and `refreshMenu(player)` let you control lifecycle without touching Bukkit inventories directly.
* `getOpenMenu(player)` plus `hasMenuOpen(player)` make it trivial to add safety checks inside commands.
* `registerMenuInstance(id, menu)` and `openMenuInstance(id, player)` turn any menu into a reusable template.

## API Reference

### Builder Factories

| Factory               | Description                                                                               |
|-----------------------|-------------------------------------------------------------------------------------------|
| `createMenuBuilder()` | Pixel-perfect builder supporting anchors, scrollable viewports, and mixed slot placement. |
| `createListMenu()`    | Paginated list builder with optional suppliers, borders, and page callbacks.              |
| `createTabbedMenu()`  | Multi-pane builder that auto-renders tab strips, dividers, and breadcrumb titles.         |

### Menu Lifecycle

| Method                   | Purpose                                                               |
|--------------------------|-----------------------------------------------------------------------|
| `openMenu(menu, player)` | Open a constructed menu for a viewer.                                 |
| `closeMenu(player)`      | Force-close any active menu.                                          |
| `refreshMenu(player)`    | Re-render the current menu so suppliers and dynamic sections rebuild. |
| `hasMenuOpen(player)`    | Quick boolean check before scheduling menu actions.                   |
| `getOpenMenu(player)`    | Retrieve the active `Menu` instance for advanced inspection.          |

### Instance Registry

| Method                           | Purpose                                                 |
|----------------------------------|---------------------------------------------------------|
| `registerMenuInstance(id, menu)` | Cache a heavy menu for repeat use.                      |
| `openMenuInstance(id, player)`   | Open a cached menu by identifier.                       |
| `getMenuInstance(id)`            | Fetch an `Optional<Menu>` for conditional reopen flows. |
| `hasMenuInstance(id)`            | Cheap existence check before rehydrating dependencies.  |

### Debug Helpers

| Tool                 | Usage                                                                     |
|----------------------|---------------------------------------------------------------------------|
| `/menudebug TABBED`  | Preview tabbed panes, confirm divider theming, and tab scroll thresholds. |
| `/menudebug DYNAMIC` | Stress-test supplier driven list menus with fake metrics.                 |
| `/menudebug BIG`     | Inspect viewport scrolling, anchored controls, and oversized canvases.    |

Now you are ready to script new menus with confidence: start with a builder, mix anchored controls with scrollable
grids, feed it dynamic suppliers, and lean on the registry whenever you crave reuse. When in doubt, poke `/menudebug`
and watch the toolkit show off.
