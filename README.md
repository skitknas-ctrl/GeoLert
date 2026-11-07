# GeoLert

An application that alerts you when you enter a zone you have defined.

GeoLert is an Android app where you place zones on a map and get an alarm when you arrive at or leave one of them. Example: play a game on the bus and get an alert when you are 500 m from your stop.

---

## Features

### Alarm list
- The start screen shows all alarms.
- Each row shows the alarm name, its trigger type (Arrive / Leave) and an on/off switch.
- An alarm is either **active** or **inactive**. There are no validity periods or schedules.
- Tap an alarm to edit it. A button creates a new alarm.

### Create / edit alarm
- A map opens (OpenStreetMap).
- Tap the map to set the center point. A search field lets you find an address.
- Set the radius with a slider. Minimum radius: 100 m (GPS accuracy can be off by 20–50 m in cities).
- **Trigger type** (exactly one per alarm):
  - **Arrive** – fires when you cross into the zone.
  - **Leave** – fires when you cross out of the zone.
- **Alert methods** (checkboxes, any combination):
  - Sound
  - Vibration
  - Flashlight blinking
- **Message** – text shown on the phone when the alarm fires.
- Save.

### Firing rules
- An alarm fires **once per boundary crossing**.
- **Hysteresis:** after firing, the alarm is re-armed only when the position is clearly on the other side of the boundary (30 m outside it for Arrive, 30 m inside it for Leave). This prevents repeated alarms caused by GPS jitter near the edge.
- When an alarm is activated (or created), the app records whether you are inside or outside the zone. It does not fire just because you already are inside (Arrive) or outside (Leave); it waits for an actual crossing.

### Alarm screen
- Works like an alarm clock: full-screen, also on the lock screen.
- Shows the alarm name and its message.
- **Dismiss** button.
- **Snooze** is set with a slider using these 25 steps:
  - Minutes: 1, 2, 3, 4, 5, 10, 15, 20, 25, 30, 40, 50, 60, 90
  - Hours: 2, 3, 4, 5, 6, 8, 10, 12, 16, 20, 24
- The snooze slider starts at the last used value.
- A snoozed alarm rings again when the snooze time is over, **even if you have left the zone**.

### Sound
- Played on the alarm audio stream, so it is heard in silent mode and in Do Not Disturb (as long as alarms are allowed there, which is the Android default).

### Settings
- **Alarm sound** – one global sound used by all alarms with sound enabled.
- **Check position at least every…** – the longest allowed time between position checks.

---

## Location strategy

Android does not check the position on its own schedule; it only does so when an app asks. GeoLert therefore manages checks itself, aiming for good accuracy with low battery use.

### Interval rules
- **Ceiling:** never longer than the user setting "Check position at least every…".
- **Distance to nearest boundary:** the interval is shortened based on how far away the nearest relevant zone boundary is, assuming a high maximum speed (about 35 m/s ≈ 126 km/h). Far from all zones means rare checks; close to a zone means frequent checks.
- **Movement:** if the phone has moved more than 100 m since the last check, the interval drops to 30 seconds. Moving mode lasts until no 100 m step has been seen for 3 minutes.
- **Floor:** never more often than every 30 seconds, except within 500 m of a zone boundary, where the floor is 10 seconds (at 50 km/h a bus covers 420 m in 30 s).
- **Accuracy:** a position fix whose accuracy is worse than an alarm's radius is not used to decide that alarm.

### Movement triggers (extra position check)
- Significant-motion sensor (built into Android, no Google services needed).
- Wi-Fi disconnects (for example leaving the home network).
- Later, in the Play version: Activity Recognition (walking, cycling, in vehicle).

Note: detecting cell tower changes is heavily restricted since Android 10 and is not used.

### Background operation
- A foreground service with a permanent notification keeps monitoring running when needed (required by Android for frequent background location).
- The user is asked to exempt the app from battery optimization. Some manufacturers (Samsung, Xiaomi and others) otherwise stop background apps.
- After a phone restart or an app update, monitoring is started again automatically, and pending snoozes are put back.
- Snoozes use Android's alarm clock (`setAlarmClock`), so they ring on time even in deep sleep.
- A ringing alarm stops by itself after 10 minutes if nobody reacts; the notification stays.

---

## Permissions

The start screen shows a setup card until everything below is granted, one step at a time with an explanation.

- Location: precise, and **"Allow all the time"** (background location)
- Notifications
- Foreground service (location)
- Full-screen alarm (lock-screen alarm)
- Start at boot
- Vibration
- Flashlight is controlled without camera permission

---

## Build variants

The location logic sits behind an interface with two implementations:

- **foss** – no Google Play Services. Own location monitoring, OpenStreetMap maps (osmdroid or MapLibre). For F-Droid and GitHub releases. **Built first.**
- **play** – uses Google Play Services (Geofencing API, Activity Recognition). For Google Play. Built later.

Everything else (UI, alarm logic, storage) is shared.

---

## UI rules

- Content must never be placed so high that it collides with the status bar (clock, notifications, battery). Always handle system window insets.
- App language: English.

---

## Privacy

- All alarms and positions are stored only on the phone.
- No position data is sent anywhere. The internet is used only to load map tiles and for address search.

---

## Building

GitHub Actions builds the `foss` APK on every push to `main` and publishes it as a release (`v0.1.<run number>`), so it can be installed and updated with Obtainium. The three latest releases are kept.

Project layout: plain Java on the Android framework (no AndroidX), package `se.dick.geolert`, minSdk 30. Maps use osmdroid.
