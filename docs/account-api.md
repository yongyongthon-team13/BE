# 계정·어르신 정보 API

담당 1의 11개 API와 전화번호 인증번호 요청 API를 구현합니다. 모든 경로는 `/api/v1` 아래에 있습니다.

## 전화번호 로그인

1. `POST /api/v1/auth/phone-verifications`에 `{"phoneNumber":"01012345678"}`을 보냅니다. SMS 요청이 접수되면 `202`를 반환합니다.
2. 받은 인증번호로 `POST /api/v1/auth/login`에 `{"phoneNumber":"01012345678","verificationCode":"123456","role":"ELDER"}`을 보냅니다. 보호자는 `role`을 `GUARDIAN`으로 지정합니다.
3. 응답의 `accessToken`을 API 호출 시 `Authorization: Bearer <accessToken>`으로 보냅니다. `refreshToken`은 기기에서 안전하게 보관합니다.
4. `accessToken`이 만료되면 `POST /api/v1/auth/refresh`에 `{"refreshToken":"..."}`을 보내 새 토큰 쌍을 받습니다. 이전 `refreshToken`은 즉시 무효가 되므로 새 토큰으로 교체해 저장합니다.
5. 로그아웃할 때 `POST /api/v1/auth/logout`에 현재 `refreshToken`을 보내고 기기에 저장한 두 토큰을 삭제합니다. 해당 기기의 접근 토큰도 즉시 무효화되며 다른 기기에서 쓰는 토큰에는 영향을 주지 않습니다.

인증번호를 확인할 때 계정이 없으면 새로 만들고, 이미 있으면 기존 계정으로 로그인합니다. 어르신과 보호자 모두 같은 절차를 사용합니다. 로그인·갱신 응답에는 `accessToken`, `tokenType`, `expiresAt`, `refreshToken`, `refreshExpiresAt`, `userId`, `role`이 있습니다. 접근 토큰은 1시간, 갱신 토큰은 최초 발급부터 30일 유효합니다. 30일이 지나거나 새 기기에서 시작하면 문자 인증이 다시 필요합니다. 로그아웃하면 해당 세션의 접근 토큰과 갱신 토큰이 모두 무효화됩니다. 클라이언트에서도 두 토큰을 삭제해야 합니다.

인증번호는 5분 유효하며 한 번만 쓸 수 있습니다. 번호별 인증번호 요청은 60초 간격·UTC 하루 10회, 인증번호 확인은 코드당 5회로 제한합니다. 같은 번호로 다른 역할에 로그인하려 하면 `409`를 반환합니다. 인증번호가 틀렸거나 만료되면 `400`, SMS 발송 서비스가 없으면 `503`, 갱신 토큰이 잘못됐거나 만료되면 `401`입니다. 갱신 토큰 원문은 DB에 저장하지 않습니다.

## 나머지 API

| 메서드 | 경로 | 내용 |
| --- | --- | --- |
| POST | `/elder-profiles` | 어르신 본인 프로필 등록 |
| GET | `/elder-profiles/{elderId}` | 본인 또는 공유 동의를 받은 연결 보호자 조회 |
| PATCH | `/elder-profiles/{elderId}` | 어르신 본인 프로필 수정 |
| PUT | `/elder-profiles/{elderId}/health-profile` | 건강정보 전체 교체. `HEALTH` 동의 필요 |
| PUT | `/elder-profiles/{elderId}/consents/{scope}` | 목적별 동의 이력 등록·철회 |
| POST | `/elder-profiles/{elderId}/guardian-link-codes` | 보호자 연결용 10분 유효 일회용 코드 발급 |
| POST | `/guardian-links` | 보호자 계정과 어르신 연결 |
| DELETE | `/guardian-links/{linkId}` | 본인 또는 보호자가 연결 해제 |
| GET | `/notifications` | 본인 알림 최근 50개 조회 |
| PATCH | `/notifications/{notificationId}` | 본인 알림 읽음 처리 |

`HEALTH`, `LOCATION`, `NOTIFICATION_CALL`, `CAREGIVER_SHARING`이 동의 범위입니다. 이름·주소·건강 메모는 AES-GCM으로 암호화하며 전화번호 원문과 인증번호 원문은 DB에 저장하지 않습니다. 개인정보 접근에는 로그인 토큰이 필요합니다.

## 서버 설정

팀 BE 프로젝트 루트에서 Java 21로 `./gradlew test`를 실행합니다. 실행 환경에는 서로 다른 두 개의 Base64 32바이트 키가 필요합니다.

```bash
export APP_DATA_KEY="$(openssl rand -base64 32)"
export APP_JWT_SECRET="$(openssl rand -base64 32)"
./gradlew bootRun
```

두 키는 운영 환경에서 안전하게 보관해야 합니다. `APP_DATA_KEY`를 잃으면 저장된 개인정보를 복호화할 수 없고, `APP_JWT_SECRET`을 바꾸면 기존 전화번호 계정을 같은 사람으로 찾을 수 없습니다. 저장소에 실제 키를 커밋하지 않습니다. 둘 중 하나라도 없으면 팀 서버는 시작하지만 계정 API는 닫힙니다.

실제 SMS는 Naver Cloud SENS 설정이 있어야 발송됩니다. `SMS_PROVIDER=ncloud`, `NCLOUD_SMS_SERVICE_ID`, `NCLOUD_ACCESS_KEY`, `NCLOUD_SECRET_KEY`, `NCLOUD_SMS_SENDER`(SENS에 등록된 발신번호)를 서버 환경변수로 지정합니다. 설정이 없으면 인증번호 요청은 `503`입니다. 로컬 테스트는 SMS 대역으로 코드 검증·토큰 발급까지 확인하며, 실제 휴대전화 수신은 계정 정보가 제공된 뒤 별도 확인해야 합니다.

## 팀 연동 메모

- 계정 API만 인증을 적용했습니다. 기존 일정·경로 API의 접근 정책은 각 담당 파트와 합의 후 함께 적용해야 합니다.
- 알림 생성은 이동 파트가 담당하며, 담당 1은 알림 목록과 읽음 처리만 제공합니다. 이동 시작 전 알림을 저장하려면 `notifications.trip_id`의 필수 여부를 함께 결정해야 합니다.
- 계정 테이블은 Flyway가 관리하고, 현재 팀의 일정 엔티티는 JPA가 관리합니다. 통합 DB 스키마와 마이그레이션 방식을 팀에서 확정해야 합니다.
