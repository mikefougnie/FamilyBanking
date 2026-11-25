# Family Bank (Android)

Keep track of money you hold for your children. When a child hands you birthday money to keep, tap **Deposit**; when you give some back, tap **Withdraw**. Each child's balance and full history are always one tap away.

Everything is stored on the phone. Signing in to a GodsKingdomNow.com Family account is optional and shares the bank with the **Family Bank** page on the website and the rest of your family.

- Package: `com.godskingdomnow.familybank`
- Kotlin 2.0.21 · Jetpack Compose (Material 3) · Room · DataStore · OkHttp
- Android 8.0 (API 26) and up · target/compile SDK 35
- Icon: `app/src/main/res/mipmap-*/ic_launcher_foreground.png` (Play Store version: `playstore-icon-512.png`)

## Getting the APK

Every push to `main` builds the app on GitHub. Open **Releases** (or the newest **Actions** run), then tap **FamilyBank.apk** on your phone to download and install it. Each new build installs over the old one and keeps your data.

From Android Studio: **File → Open** this folder, let Gradle sync, then press **Run ▶**. From a terminal with the Android SDK: `./gradlew assembleDebug` (tests: `./gradlew test`).

## What it does

- **Home:** the total you're holding for all your children, and a card per child with their balance and Deposit / Withdraw buttons.
- **Child:** big balance, history grouped by month with the balance after each entry, who recorded it (when synced), and the note ("Birthday money", "Toy store").
- Pick the day when recording something from the past. Withdrawals can't be more than the child has.
- Press and hold an entry to delete it. Rename or remove a child from the ⋮ menu.
- **Share statement…** sends a child's balance and history by text or email.
- ☕ **Buy me a coffee** in the top bar and on the Family sync screen, plus a friendly prompt at most once a week ("Don't ask again" is final). The PayPal and Venmo names are in `gradle.properties` (`supportPaypal`, `supportVenmo`); leave them blank to hide coffee.

## Family sync (optional)

Sign in on the **Family sync** screen (person icon, top right) with your Family app username and password.

- It's the same Family Bank as on the website: same children, same deposits and withdrawals.
- **Parents** can add, rename and remove children and record money. **Other family members** see the bank read-only, just like the website.
- When a parent signs in, children already on the phone are added to the family's bank. A child with the same name as one on the website is joined to that child.
- Changes sync about 3 seconds after you make them, and each time you open the app. With no signal, it catches up later and keeps the day you recorded it.
- The website is the record: a child or entry removed there is removed from the phone too.
- The phone gets its own sign-in key. The website saves only a hashed copy. The key ends when you sign out, change your password, or don't open the app for 180 days.
- Signing out takes the family's children off the phone (they stay on the website). Children kept only on the phone stay.

To use a different site, tap "Use a different website address" on the sign-in screen. The default is `https://www.godskingdomnow.com/family/`.

### Website update (needed for sync)

Upload `server/family/bank_app_api.php` to the `family` folder of the site (on both godskingdomnow.com and golgothabridge.com if you use both; they share one database). No SQL import is needed: it creates its two small tables the first time someone signs in from the app. It doesn't change any existing file. See `server/INSTALL.txt`.

## Project layout

```
app/src/main/java/com/godskingdomnow/familybank/
  data/    Room database (children, entries), money helpers, repository
  sync/    sign-in storage, website API, sync manager
  ui/      Compose screens: home, child, family sync, coffee
server/family/bank_app_api.php   the website side of sync (PHP 5.6+)
```
