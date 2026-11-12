# Frontend integration contract

This page records the values and browser-origin behavior used by the backend.

## CORS

`CORS_ALLOWED_ORIGINS` is a comma-separated list of exact browser origins, including scheme and port. It defaults to `http://localhost:8081` and `https://yongyongthon-team13.expo.app`; do not use `*`.

The API allows `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, and `OPTIONS`, with `Authorization` and `Content-Type` headers. Valid preflight requests pass without a bearer token. CORS does not make protected API operations public; normal requests still require authentication.

## Elder home location

Home-location APIs are for the authenticated elder account only. TMAP's Full Text Geocoding API is called by the backend; the TMAP key is never sent to the browser. Search only the base road or parcel address. Keep a unit or room number out of the geocoding request. The user must select or confirm the map point before saving it.

### Search address candidates

`POST /api/v1/elder-profiles/me/home-location/candidates`

```json
{"address":"경기도 용인시 처인구 ..."}
```

The response contains candidate addresses and WGS84 coordinates. An empty candidate list means TMAP did not find a usable coordinate.

### Read saved location

`GET /api/v1/elder-profiles/me/home-location`

If no location has been confirmed, `address`, `latitude`, `longitude`, and `verifiedAt` are `null`.

### Save confirmed location

`PUT /api/v1/elder-profiles/me/home-location`

```json
{
  "address":"경기도 용인시 처인구 ...",
  "addressDetail":"101동 1001호",
  "latitude":37.234567,
  "longitude":127.123456
}
```

The server encrypts the address and both coordinates at rest. Changing `homeAddress` through the elder profile PATCH clears the saved coordinates and confirmation timestamp so a changed address cannot keep using a stale pin. Legacy profiles without a confirmed location return null coordinates; clients must ask the user to set a location instead of substituting a nearby public place.

The home location is a suggested origin only. A route request should use the user's actual chosen origin, which can be the current position or the saved home location.

## Route modes

`POST /api/v1/route-plans/search` accepts `mode`:

- `WALK`: pedestrian route
- `TRANSIT`: public-transit route

`POST /api/v1/route-plans/recommendations` currently accepts only `TRANSIT` in `routeSearch.mode`.

The response's whole-route `mode` is `WALK` or `TRANSIT`. Each leg's `mode` is `WALK`, `BUS`, or `SUBWAY`.

## Trip values

Client-submitted event types for `POST /api/v1/trips/{tripId}/events`:

- `BOARDED`
- `ALIGHTED`
- `ARRIVED`
- `HELP_REQUESTED`
- `CANCELLED`
- `ABORTED`

`detailCode` is optional. It is an event-specific free string with no allowlist; `BUS` is an example for a boarding event. Server-generated events are not accepted as client event types.

`POST /api/v1/trips/{tripId}/check-ins` accepts `response` values `OK` and `NEED_HELP`.

Current trip lifecycle status values are `ACTIVE`, `HELP_REQUESTED`, `ARRIVED`, and `CLOSED`. `CANCELLED` and `ABORTED` both end in `CLOSED`; `GET /api/v1/trips/{tripId}` now also returns `lastEventType` so clients can distinguish the terminal cause. Delay is reported separately as a boolean by the location update response, not as a lifecycle status.

`GET /api/v1/guardian/elder-profiles/{elderId}/active-trip` also returns `lastLatitude` and `lastLongitude` for the latest location update. Both are `null` when there is no current location or its two-minute freshness period has expired. The shared trip status response exposes these fields to the elder's own trip lookup as well.

`POST /api/v1/guardian/elder-profiles/{elderId}/trips/{tripId}/contact-actions` accepts:

- `actionType`: `CALL`, `MESSAGE`, or `EMERGENCY_CALL`
- optional `outcome`: `CONNECTED`, `NO_ANSWER`, `FAILED`, or `OTHER`

This endpoint records an action; it does not place a call or send a message. `occurredAt` is deprecated and ignored. The server records its receipt time.

## Guardian push notifications

The mobile app uses Expo Notifications. A signed-in guardian registers the current Expo push token with `POST /api/v1/push-tokens`:

```json
{"token":"ExponentPushToken[xxxxxxxxxxxxxxxxxxxxxx]","platform":"IOS"}
```

`platform` is `IOS` or `ANDROID`. The API stores the token encrypted and returns `{ "registered": true }`; token values are never returned. Register again when Expo issues a replacement token. The app must be a development or production build with Expo/APNs/FCM push credentials configured; Expo Go cannot receive remote push notifications.

Call `DELETE /api/v1/push-tokens` with `{"token":"ExponentPushToken[...]"}` when signing out or unregistering this device. It returns `{ "registered": false }` and deactivates only the current guardian's matching token.

Delay notifications are queued when a location update estimates arrival more than 10 minutes later than the planned arrival, and that condition remains for 5 minutes. The rule is evaluated when location updates arrive; an `OK` check-in clears the current delay timer. A separate no-response alert is queued when the planned arrival time has passed by 5 minutes, the latest location is still fresh (within 2 minutes) and more than 1.2 km from the destination, and there has been no `CHECK_IN_RESPONDED` event since the planned arrival time. The server checks this condition every minute and sends at most one such alert per trip. Caregiver-sharing consent and an active guardian link are required. The in-app notification type is `NO_RESPONSE`; the Expo push data contains `type: TRIP_NO_RESPONSE` and `tripId`. A guardian assistance request also queues a push for each currently linked guardian when caregiver-sharing consent is active. Push delivery is asynchronous; the in-app notification/request APIs remain the source of truth. An outbox status of `SENT` means Expo accepted at least one ticket; it does not confirm device receipt. The backend checks Expo receipts after 15 minutes and deactivates tokens that Expo reports as `DeviceNotRegistered`; this still cannot confirm that a person saw the notification.

## Guardian assistance transport requests

An elder creates a request tied to an appointment with `POST /api/v1/appointments/{appointmentId}/transport-requests`, using type `GUARDIAN_ASSISTANCE`. Caregiver-sharing consent and at least one active guardian link are required. The request begins with `status: PENDING`; linked guardians read it with the appointment's existing `GET` endpoint. The frontend's `pickup`/`taxi` choices map to `PICKUP_BY_GUARDIAN`/`CALL_TAXI`. The API response includes `requestId`, `type`, `status`, `guardianResponse`, `requestedAt`, `updatedAt`, and `statusDescription`.

To read one request by ID, use `GET /api/v1/appointments/{appointmentId}/transport-requests/{requestId}`. The appointment ID and request ID must refer to the same record.

A linked guardian answers with `POST /api/v1/appointments/{appointmentId}/transport-requests/{requestId}/response`:

```json
{"response":"PICKUP_BY_GUARDIAN"}
```

The response can be `PICKUP_BY_GUARDIAN` or `CALL_TAXI`. A successful answer sets `status: ACCEPTED` and returns the selected `guardianResponse`. Only the first response is accepted. These values record the agreed plan; they do not book a taxi or dispatch a vehicle.
