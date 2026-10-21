# animeplayer for Android TV — the interface

The television client is a translation of the desktop client. The design language is the
same — the same eight themes, the same accent colours, the same typeface, the same
hero banner and the same rails of cards — but every decision is re-made for a remote
control.

This document is the specification of that translation. It describes what the interface
does, not how the code is written; the code is in `samples/android-tv`.

---

## 1. The problem

The desktop client is a pointer application. It has:

* a rail of icons on the left, 64 pixels wide, with the names in tooltips;
* cards that grow when the mouse hovers over them;
* a form of seven drop-downs to filter the catalogue;
* a centred dialog, `max-w-lg` wide;
* a player that hides its controls when the mouse stops moving.

None of that works on a television. There is no pointer, so there is no hover and no
tooltip. A 64-pixel rail is invisible from three metres. A drop-down opens a list that
is built for a mouse. A centred dialog in the middle of a 1920-pixel screen is a postage
stamp.

The translation is not a restyle. It is a different set of decisions.

---

## 2. The rules

| # | The desktop client | The television client |
|---|---|---|
| 1 | `hover: scale(1.04)`, gradients on hover, `title` tooltips | **Focus is the only state.** The focused element grows by 6 %, gets a 2 dp ring and a glow. Information is shown in the element itself, never in a tooltip. |
| 2 | A rail of icons on the left | **A navigation bar on top**, 72 dp, five labelled tabs. A 64-pixel rail is unreadable at three metres. |
| 3 | Poster cards, 2:3 | **Landscape cards, 16:9.** A 16:9 television wastes half of a portrait tile, and the title of a portrait card wraps into three unreadable lines. |
| 4 | The rail is scrolled with the wheel, and with two chevron buttons | **The D-pad moves between the cards.** The rail scrolls to follow the focus and lets the next card peek in. The chevrons are gone; a "See all" tile ends the rail instead. |
| 5 | The banner turns every nine seconds | **It still turns, but it stops while the user is looking at it.** A banner that keeps changing while it is read is never read. |
| 6 | Seven drop-downs and two icon buttons | **A row of chips.** Every chip opens a full-screen sheet with a list that is scrolled with the D-pad. |
| 7 | A tab bar with Episodes, Relations, Recommendations | **Rails.** The page is one vertical stack of rails, and the D-pad moves from rail to rail. |
| 8 | The player overlay hides after 2.5 seconds of no mouse movement | **After 5 seconds**, and any key brings it back. The overlay never takes the focus while it is hidden. |
| 9 | A drop-down for the quality and the subtitles | **A side panel** that slides in from the right, 420 dp wide, with a focusable list. `BACK` closes the panel before it closes the player. |
| 10 | A centred dialog | **A full-screen sheet** with a focus trap. The content behind it is inert. |
| 11 | `localStorage` for the favourites | **DataStore**, so the favourites are the same on the television and on the desktop. |
| 12 | — | **Focus memory.** Coming back from the player puts the focus where the user left it, not on the first card. |
| 13 | — | **The back stack.** The player, then the series, then the home screen, then the exit. `MENU` opens the settings, `SEARCH` opens the search, the media keys drive the playback. |
| 14 | 12-pixel body text | **16 sp.** The Material 3 television type scale: body 16 sp, title 22 sp, headline 32 sp, display 57 sp. |
| 15 | — | **Overscan.** The margin is 48 dp on a 960 dp canvas — 5 %, the safe area every television guarantees. |

### The density

A 1080p television is 960 dp × 540 dp, because it runs at 320 dpi. The width of a card
is therefore **derived from the width of the screen**, never hard-coded: six cards fit on
a 1080p set, seven or eight on a 4K one, and the layout never looks sparse or crowded.

---

## 3. The focus

The focus is the whole interface. Four rules govern it.

1. **The navigation bar hands the focus to the content.** The spatial focus of Compose is
   good inside a row and unreliable between the areas of a screen — from the navigation
   bar, "down" picks the closest element below it, which is the first dot of the banner,
   and not the button the user meant. So every screen states where the focus enters, and
   the navigation bar calls it.
2. **The focus is visible.** A focused element grows by 6 %, gets a 2 dp ring in the
   colour of the surface — near white on the dark themes — and a glow in the accent
   colour. The ring is *not* the accent colour: an accent-coloured control would hide
   its own ring.
3. **The focus never gets lost.** Every rail remembers where the focus was, so coming
   back from the player does not throw the user back to the first card.
4. **The focus never traps the user.** A text field swallows the arrow keys — they move
   the cursor. So the search field is a *button* until the user presses the button on it:
   only then does it become a real text field and the on-screen keyboard open.

### The colours of a focus

```
ring   2 dp,  colourScheme.onSurface   — near white, always contrasts
glow   8 dp,  colourScheme.primary     — the accent, the halo
scale  1.06   focused, 0.97 pressed
```

---

## 4. The screens

### 4.1 Home

```
┌───────────────────────────────────────────────────────────────────────┐
│  ◤ animeplayer   [Home]  Search  Airing  Library  Favourites      ⚙ │
├───────────────────────────────────────────────────────────────────────┤
│                                                                       │
│   ████ HERO BANNER — key visual, two gradient scrims ████            │
│   ██  One Piece                                                      ██ │
│   ██  [1100 Episodes][TV][RELEASING][1999][88 %]                    ██ │
│   ██  Gold Roger was known as the Pirate King…   [▶ Watch now] [♥]   ██ │
│   ██                                                       ● ○ ○ ○   ██ │
│                                                                       │
│  Popular This Season ─────────────────────────────────────────        │
│  ┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐        │
│  └────────┘└────────┘└────────┘└────────┘└────────┘└────────┘        │
│  Trending Now ───────────────────────────────────────────             │
└───────────────────────────────────────────────────────────────────────┘
```

The banner is 48 % of the height of the screen. The rails follow: *Continue watching*,
*Popular this season*, *Trending now*, *Top 10 this week*, *All time popular*,
*Upcoming*, and five genre rails. Every rail ends with a **"See all"** tile that opens
the whole catalogue of that rail.

The dots of the banner sit in the bottom right corner, not in the bottom centre: the
D-pad would find them when the user pressed "down" to leave the banner.

### 4.2 The card

```
┌──────────────────────────┐
│      16:9 artwork        │
│                  [ EP 5] │
│ ▬▬▬▬▬▬▬▬▬▬░░░░░         │
└──────────────────────────┘
│ Title, two lines at most
│ 2026 · TV · 87 %
```

The artwork is 16:9, with a radius of 8 dp. The placeholder is tinted with the colour
the catalogue publishes for the poster, so a rail never looks like a row of empty boxes
while the images arrive. Overlays: `NEW`, `EP n`, a progress bar, `DUB`.

The *Top 10* rail draws the number behind the artwork, the way the streaming services do.

### 4.3 Search

```
┌───────────────────────────────────────────────────────────────────────┐
│  🔍 [ Type a title…                                    ]              │
│  [ Genres ][ Year ][ Season ][ Format ][ Status ][ Sort ]             │
│  ┌────────┐┌────────┐┌────────┐┌────────┐                            │
│  └────────┘└────────┘└────────┘└────────┘                            │
└───────────────────────────────────────────────────────────────────────┘
```

The field is a button until it is pressed; then the on-screen keyboard of the system
opens. The filters are chips, and every chip opens a full-screen sheet with a list of
options — a drop-down is a mouse shape, a sheet is a television shape.

The results are a grid of four columns of landscape cards.

### 4.4 The series

The upper half is the catalogue — the key visual, the poster, the title, the badges, the
description, the actions. The lower half is the library — the episodes that can actually
be played, in a grid of six columns.

The catalogue carries more than the artwork, and all of it is on the screen:

| The metadata | Where it is shown |
|---|---|
| The number of episodes, the format, the state, the year, the score, the length of an episode | The badges under the title |
| **When the next episode airs** | A line under the badges — `Episode 12 airs in 3 days` — and, on the cards, `Episode 4 in 4 days` in the accent colour. A series that is still running says so. |
| The state of the series | `Airing`, `Finished`, `Upcoming`, `On hiatus` — on the cards, under the metadata |
| **When an episode aired** | On the episode tile — `in 3 days` for the ones that have not aired yet, `2 weeks ago` for the ones that have |
| **The relations** | A rail of its own — *Sequels and prequels* — with the kind of relation under each card: Prequel, Sequel, Side story, Adaptation, Alternative |

The relations used to be fetched and thrown away. The catalogue API knows about them, and
so does the desktop client, which has a tab for them.

```
Episodes ───────────────────────────────────────────
┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐┌────────┐
│ EP 1 ✓ ││ EP 2 ▬░││ EP 3   ││ EP 4   ││ EP 5   ││ EP 6   │
└────────┘└────────┘└────────┘└────────┘└────────┘└────────┘
Episode 1  Episode 2  Episode 3  Episode 4  Episode 5  Episode 6
```

An episode without a thumbnail shows its number, so the tile never looks broken.

### 4.5 The player

```
┌───────────────────────────────────────────────────────────────────────┐
│ ‹ One Piece · EP 105                                          ⓘ       │
│                                                                       │
│                          [ VIDEO ]                                   │
│                                                                       │
│                                              ┌──────────────────────┐ │
│                                              │  Episodes             │ │
│                                              │  ▶ EP 105  23:14      │ │
│                                              │    EP 104  23:11      │ │
│                                              └──────────────────────┘ │
│  5:18 ━━━━━━━━━━━●─────────────── 25:56                              │
│  [Episodes][Audio][Subtitles][Quality][Info]         [Skip the intro] │
└───────────────────────────────────────────────────────────────────────┘
```

The overlay disappears after five seconds and comes back with any key. The transport is
`−10 · play/pause · +10`; the transport takes the focus when the overlay appears. The
menus open a side panel on the right: the episodes, the audio track, the subtitles, the
quality and the information about the episode.

The video is drawn on a **texture view**, not a surface view: a surface view is drawn in
its own layer, above the composition, and the controls would disappear behind it.

**The controls are never removed from the composition.** They are faded in and out with
an alpha. Removing them destroys the focused element, the video takes the focus, and no
key ever reaches the player again — the controls have to stay, and the reference
implementations do the same.

**The focus moves along a declared graph**, not by the spatial search:

```
   top row  ⇅  transport  ⇅  scrubber  ⇅  menu row
```

Every area says where "up" and "down" go, so the user always knows where the D-pad will
take them.

**The scrubber is a control, not a picture.** The D-pad moves along it, the position
follows immediately, and the thumb grows and glows when the bar has the focus.

### 4.6 The subtitles

The subtitles are drawn by the application, between the picture and the controls, from
the cues that Media3 hands to the player.

They come in **six styles**, each of them a style that fansubs and the streaming services
actually use:

| Style | What it is |
|---|---|
| **Default** | White letters with a soft shadow, at the bottom. What every streaming service does. |
| **Jōsei** (居士) | Small letters with a hard outline, at the *top*. The style of the Japanese broadcast captions, and of the fansubs that keep the translation notes and the lyrics out of the way of the picture. |
| **Plate** | White letters on a solid plate. The style of the subtitled fansub releases. |
| **Outline** | Hard outlines, no shadow, no plate. The cleanest style. |
| **Sign** | Large letters in the middle of the frame, the way a title card is written. |
| **Karaoke** | A coloured plate, the way the karaoke releases mark the line that is sung. |

On top of the style the user can make the letters **bigger or smaller** and move them
**up or down**, in steps a person can feel with a remote. The panel shows a **live
preview** drawn with the very same renderer as the episode, so what the user sees is what
the episode will look like.

The style is remembered between the sessions.

#### The typefaces

The Japanese television captions have their own tradition, and the fansubs follow it:

| Typeface | What it is | The open equivalent |
|---|---|---|
| **Maru gothic** | 丸ゴシック — the rounded gothic. The shape of the captions of a Japanese broadcast, and the face most fansubs use. | *Zen Maru Gothic* |
| **Gothic** | ゴシック — the plain gothic. Small, dense, and it stays out of the way of the picture. The workhorse of the subtitles. | *Zen Kaku Gothic New* |
| **Display** | The heavy display face, the one the Japanese television uses for the dramatic moments — the 隷書 tradition has no open equivalent, so a heavy display gothic stands in for it. | *BIZ UDPGothic* |
| **Rounded** | The rounded sans of the Japanese web. | *M PLUS Rounded 1c* |

Every typeface is shown in the picker *in itself*, so the user reads it before picking it.
All four are under the SIL Open Font License and are bundled as the Latin subsets — the
subtitles are translations, so the CJK ranges would be dead weight.

### 4.7 The other screens

* **Airing** — the broadcast schedule, from a different site than the streams.
* **Library** — what the user watched, and a search of the upstream site, which is where
  the episodes that can actually be played are.
* **Favourites** — the series the user marked, remembered by their catalogue identifier.
* **Settings** — the eight themes and the ten accent colours of the desktop client, plus
  the playback preferences.

---

## 5. The themes

The eight themes of the desktop client are ported token by token. The values are the
same HSL values, and they are mapped onto a Material 3 colour scheme:

| Token | Material 3 |
|---|---|
| `--background` | `background` |
| `--foreground` | `onBackground` |
| `--muted` | `surfaceVariant` |
| `--muted-foreground` | `onSurfaceVariant` |
| `--card` | `surface` |
| `--primary` | `primary` |
| `--secondary` | `secondary` |
| `--accent` | `primaryContainer` |
| `--destructive` | `error` |
| `--success` | `tertiary` |
| `--border` | `border`, `borderVariant` |
| `--ring` | the colour of the focus ring |

The typeface is **Nunito**, the same one the desktop client uses, bundled as a variable
font.

---

## 6. How the data is put together

Two sites, two purposes:

```
AniList ──(the catalogue: artwork, descriptions, scores)──┐
                                                         ├─► title match ─► slug
hianime ──(the episodes that can be played)─────────────┘
```

The catalogue knows a series by its title, the library knows it by the slug of the
upstream site. Nothing connects the two, so the titles are compared — the same
`TitleMatch` the desktop client uses, ported to `core` so both clients behave the same.

The AniList catalogue is a **new module**, `sources/anilist`, behind a new port,
`CatalogSource`, in `core`. A catalogue is not a stream source: it never serves a
stream, so it does not implement `AnimeSource`.

The catalogue allows thirty requests a minute, so every answer is cached for five
minutes and the rails are loaded one after another.

---

## 7. The verification

The television interface is verified with a **real Android TV emulator** and **real key
events**, not with a mouse:

```bash
# The AVD
emulator -avd animeplayer-tv -no-window -gpu swiftshader_indirect

# The application
./gradlew :samples:android-tv:installDebug
adb shell am start -a android.intent.action.MAIN \
    -c android.intent.category.LEANBACK_LAUNCHER -n dev.animeplayer.tv/.MainActivity

# The focus
adb shell input keyevent KEYCODE_DPAD_DOWN
adb shell input keyevent KEYCODE_DPAD_RIGHT
adb shell input keyevent KEYCODE_DPAD_CENTER

# The screenshots
adb exec-out screencap -p > docs/tv/target/home-01-hero.png
```

The images in `docs/tv/target/` are the result of that loop. The images in
`docs/tv/reference/` are the desktop client, for comparison.

---

## 8. What is left

* The episode panel of the player points at the details of the series; the episodes are
  not listed inside the panel yet.
* The idle mode — the attract screen after half a minute without input — is not
  implemented.
* The top ten rail uses the same cards as the other rails; the number is not drawn
  behind the artwork yet.
