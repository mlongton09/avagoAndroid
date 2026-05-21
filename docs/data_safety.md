# Data Safety — Avago Android

## Data collected

| Category | Data type | Purpose | Required | Shared with 3rd parties |
|---|---|---|---|---|
| Personal info | Name | Account display, mentions | Yes | No |
| Personal info | Email address | Sign-in, account | Yes | Firebase Auth |
| Device info | Device ID | Push token registration | Yes | FCM |
| Photos/videos | Photos | Asset/document photos | Optional | Firebase Storage |
| Location | Approximate location | Asset geolocation (optional) | No | No |
| App activity | App interactions | Analytics (Firebase) | Yes | Firebase Analytics |
| Crash logs | Crash data | Crashlytics diagnostics | Yes | Firebase Crashlytics |

## Data not collected
- Precise location (not requested)
- Contacts
- Calendar
- Financial info (user's own data stored, not harvested)
- Health info
- Messages (chat messages stored on own servers, not sold)
