# GeoQuiz – Play Store listing (3.0.0 draft)

Draft for Dav to paste into Play Console → Grow → Store presence → Main store listing. **Nothing here has been uploaded.**
Rules (CLAUDE.md, plan section 5 L9): describe mechanics generically, never name other games or apps, Australian spelling.

> Mode names below assume every mode ships (D63: all flags on in the RC). If Dav turns a mode back off after testing, delete its line from the full description and from "What's new".

## App name (≤ 30 characters)

Keep the current Play title unchanged. The launcher label (`app_name`) and the feature graphic say "Geography Quiz", and the repo, docs and copy below say "GeoQuiz". If the Play title is "GeoQuiz", consider matching `app_name` to it in a later release; changing the launcher label is out of scope for this RC. An owner action is still open: pick a distinctive name or subtitle before paid marketing, since many apps share these names.

## Short description (≤ 80 characters)

```
Name every country, capital and flag, plus maps, borders and a daily challenge.
```
(79 characters)

## Full description (≤ 4000 characters)

```
How many of the world's 197 countries can you name? GeoQuiz turns geography into quick, satisfying rounds you can play anywhere, with no internet needed.

CLASSIC QUIZZES
• Countries, Capitals and Flags: type every answer in the set before you give up.
• Lots of categories: countries starting with, ending with or containing a letter, word patterns, name lengths, regions and subregions, island nations, and flags by colour, colour combination, number of colours or design element.
• Forgiving typing: accents, "St" for "Saint", "&" for "and" and small typos are all understood at Normal difficulty.

NEW WAYS TO PLAY
• Country silhouettes: name the country from its outline, with optional hints.
• Tap the map: find each country on a map of the world or one continent, or type them all and watch the map fill in.
• Border hop: travel from one country to another through land borders in as few hops as you can.
• States and territories (US, Canada and Australia): tap them on the map, name them all, or pick from four.
• Currencies: which country uses this currency?
• Rivers, mountains and lakes: identify famous natural features highlighted on a map.
• Flag speed round: 60 seconds of lookalike flags. How long can you keep your combo going?

DAILY CHALLENGE
• Ten new questions every day, the same for everyone on the same date.
• Build a streak by playing each day. Earn streak freezes to cover the odd missed day (never for sale).
• Share your score as an image card or text.

PLAY YOUR WAY
• Three difficulty levels for every mode: Easy (four choices), Normal (type the answer) and Hard (exact spelling, three strikes, timer on).
• Track your best score on every category, and practise just the ones you missed.
• Pin your favourite categories to the Play screen and pick up a paused quiz where you left off.
• 38 achievements and leaderboards with Google Play Games.
• Challenge a friend with a link to beat your score.

MADE FOR EVERYONE
• Works fully offline.
• Dark theme, large text support and screen reader support.
• Correct answers are shown in blue with a tick, wrong ones in orange with a cross, so colour is never the only cue.
• Suitable for all ages. Ads are non-personalised, and a one-off purchase removes them.

Country data from mledoze/countries (ODbL). Maps made with Natural Earth. Flags from flag-icons (MIT). Full credits are in the app.
```

Length: about 2,360 characters (Play counts characters, not bytes).

## What's new in 3.0.0 (≤ 500 characters)

```
The biggest GeoQuiz update yet:
• Seven new modes: Country silhouettes, Tap the map, Border hop, States and territories (US, Canada and Australia), Currencies, Rivers, mountains and lakes, and Flag speed round.
• A daily challenge with streaks and a shareable score card.
• Easy, Normal and Hard for every mode.
• A new home screen with colourful categories, pins and a Surprise me button.
• Typo-friendly answers, dark theme and large text support.
```
(about 425 characters)

## Graphics

| Asset | File | Made by |
|---|---|---|
| App icon 512x512 | `store_assets/play_store_icon_512.png` | `python generate_store_assets.py` (Lato) |
| Feature graphic 1024x500 | `store_assets/feature_graphic_1024x500.png` | same |
| Phone screenshots 1080x1920 | `store_assets/screenshots/*.png` | `StoreScreenshotTest` (D65; see the session 13 log for the command) |
| Achievement icons + import zip | `store_assets/achievements/`, `store_assets/achievements_import.zip` | `python generate_achievements_zip.py` (Lato) |

## Owner checklist for Dav (Phase 9 items from plan section 3)

Do these only after the RC passes the device test (session 13 manual checklist).

- [ ] **Turn modes back off if needed.** For any mode or feature that shouldn't ship, set its `defaultEnabled` to `false` in `app/src/main/java/com/geoquiz/app/domain/model/FeatureFlag.kt` and rebuild. Then remove it from the description, "What's new" and the screenshots.
- [ ] **Achievement text in Play Console (edit by hand, do not re-import).** The app now uses Australian spelling (D66). In Play Console → Play Games services → Achievements, edit each of these four. A bulk import of `achievements_import.zip` would create *new* achievements for an existing game, and players' unlocks are tied to the existing ones.
  - World Traveler → **World Traveller**
  - Color Expert → **Colour Expert**, description "Complete 5 different flag colour quizzes"
  - Rainbow: description "Complete flag quizzes for 6 different colours"
  - Flag Master: description "Complete the Flags of the World quiz with 80%+"
- [x] **Achievement spelling (decided: rename, D66).** Display text only: the ids `world_traveler`/`color_expert`/`rainbow` and the Play Games ids are unchanged, so nobody loses an unlock.
- [x] **Duplicate mappings CSV.** Google's bulk-import doc (developer.android.com/games/pgs/integrate-achievements) names the file `AchievementsIconsMappings.csv`, which the script writes and zips. The stale copy `AchievementsIconMappings.csv` was deleted (approved 2026-10-17).
- [ ] **Upload graphics:** the icon, feature graphic and phone screenshots (table above).
- [ ] **Paste text:** the short description, full description and "What's new" (en-AU, plus en-US/en-GB if those listings exist).
- [ ] **Content rating and Families:** new modes add no new content types. Re-confirm the questionnaire answers are unchanged (mixed audience, D1).
- [ ] **Data safety:** no new data collected (streaks and daily results stay on the device; the share card is a local image). Confirm the form still matches.
- [ ] Then upload the AAB (`app/build/outputs/bundle/release/app-release.aab`) to a testing track first.
