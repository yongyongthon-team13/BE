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

`POST /api/v1/guardian/elder-profiles/{elderId}/trips/{tripId}/contact-actions` accepts:

- `actionType`: `CALL`, `MESSAGE`, or `EMERGENCY_CALL`
- optional `outcome`: `CONNECTED`, `NO_ANSWER`, `FAILED`, or `OTHER`

This endpoint records an action; it does not place a call or send a message. `occurredAt` is deprecated and ignored. The server records its receipt time.
