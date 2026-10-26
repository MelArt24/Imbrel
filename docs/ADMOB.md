# AdMob in Imbrel

## Integration

One standard anchored adaptive banner is hosted underneath the Home / Places /
Settings navigation bar and above Android navigation insets. A non-interactive
16 dp separator and divider separate navigation from the advertisement. The
adaptive slot is reserved on Home even before consent, while loading, and on
load failure. Places and Settings collapse the slot and separator to zero height,
placing navigation directly above the normal system inset and existing margin.
Navigation therefore moves when switching between Home and these tabs, but remains
stationary during consent updates and ad loading on Home.

The banner first loads on Home after UMP permits requests and Mobile Ads finishes
initializing. Its Android View survives tab switches, becomes invisible and pauses
off Home, resumes on Home, and is destroyed when removed, resized, or consent is
no longer usable. There is no manual refresh timer. AdMob controls any configured
automatic refresh, only while the ad is visible.

UMP is the sole source of consent; no consent value is persisted in Room or
DataStore. Each MainActivity launch requests updated consent information, followed
by the required official consent form. Mobile Ads initializes once per process on
an independent background scope. Privacy choices are available in Settings only
when UMP reports that this entry point is required. Advertising is suspended while
the form is open and permission is rechecked after dismissal, including errors.

Google SDK 25.5.0 uses Kotlin 2.3 metadata. Kotlin/Compose compiler 2.3.21 and KSP
2.3.12 are used to compile it. Room libraries move to 2.8.5 for KSP2 compatibility;
the database remains version 2, with the same entities, cache policy and queries.
Serialization runtime 1.11.0 is aligned with the new compiler and Room migration
test serializers; weather DTOs and API requests are unchanged.

## Production identifiers

Debug always uses Google's official test App ID and adaptive banner unit ID.
Production identifiers are never substituted into Debug.

Put these properties in your user-level Gradle properties (on Windows,
`%USERPROFILE%/.gradle/gradle.properties`) or provide them through your CI's Gradle
properties configuration:

```properties
ADMOB_APP_ID=<your actual AdMob application ID>
ADMOB_BANNER_AD_UNIT_ID=<your actual AdMob banner ad unit ID>
```

The first identifier contains `~`; the banner identifier contains `/`. Copy both
from the same publisher account in AdMob. These are identifiers, not credentials,
but user-level properties avoid accidentally checking configuration into Git.

`validateReleaseAdMobIds` runs before release compilation/manifest processing. It
rejects missing identifiers, malformed strings, Google's test publisher, common
zero placeholders, and mismatched publishers. It does not verify account ownership
or whether an ad unit actually exists; confirm that in the AdMob console. Gradle
sync and Debug builds work without production properties. No invented production
IDs are supplied and release cannot silently fall back to test IDs.

## Testing on a phone

1. Install `app/build/outputs/apk/debug/app-debug.apk` or run Debug from Android
   Studio. No production IDs are required. Connect to the internet and open Home.
2. Finish any UMP form. The banner must have Google's test-ad label. Ads must not
   load if UMP does not allow requests. Never click live production ads to test.
3. Switch Home / Places / Settings repeatedly: navigation buttons must stay at the
   same vertical position while Home ads load; Places and Settings must have no
   advertising gap, and advertisements must appear only on Home.
4. Rotate the phone and test gesture navigation, three-button navigation, a display
   cutout, split screen, and an offline / failed-load scenario. Blank reserved space
   is intentional; weather and navigation must continue working.
5. To exercise EEA consent on a physical test phone, copy its UMP test-device hash
   from Logcat and rebuild Debug with the following optional properties:

```properties
UMP_FORCE_EEA=true
UMP_TEST_DEVICE_HASH=<the UMP hash printed for your test device>
```

These flags are ignored by Release. Emulators are automatically UMP test devices.
The actual message must be configured for the AdMob App ID being tested; Google's
sample App ID does not validate your publisher's production message configuration.
When UMP requires a privacy-options entry, Settings must show **Manage privacy
choices**, opening Google's form. Recheck permission after changing choices. To
repeat first-install consent, reset app data only on a disposable test installation
(this also removes local weather and places).

Automated tests use fake consent gateways, an SDK initializer, and banner Views.
They never need a live ad response and do not initialize Mobile Ads or call a real
advertising load. UI tests cover placement, Home reservation, compact non-Home navigation, lifecycle,
recomposition, failure, and privacy entry visibility.

## Before a Google Play AAB release

- Create the Android app and banner unit in AdMob; set both real Gradle properties.
- Configure and publish the applicable UMP messages in AdMob Privacy & messaging.
  Review your target audience, regions and advertising settings for the actual app.
- Update your privacy policy and Google Play **Contains ads**, **Data safety** and
  advertising-ID declarations to reflect the SDK and actual app behavior. Review
  Google's SDK disclosure guidance instead of copying assumptions from this repo.
- Configure the store/developer website and app-ads.txt as required by your AdMob
  account, and complete the console's app readiness / store linking steps.
- Increment versionCode / versionName, use your normal release signing, build a
  signed AAB, and verify consent and placement in internal testing before rollout.
- Run lint / static analysis, unit tests and device tests. Confirm missing/test IDs
  reject Release, and that production AAB contains the real IDs and no UMP debug flags.

## Official references

- SDK versions: https://developers.google.com/admob/android/rel-notes
- Standard adaptive banners: https://developers.google.com/admob/android/banner
- Placement and fixed-space guidance: https://support.google.com/admob/answer/6275335
- UMP and privacy options: https://developers.google.com/admob/android/privacy
- SDK data disclosure: https://developers.google.com/admob/android/privacy/play-data-disclosure
- Kotlin/Gradle compatibility: https://kotlinlang.org/docs/gradle-configure-project.html
