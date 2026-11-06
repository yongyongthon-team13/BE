#!/usr/bin/env python3
"""Check Sendm API access without sending a message or charging coins."""

import getpass
import http.client
import json
import sys


def main() -> int:
    user_id = input("센드엠 아이디: ").strip()
    api_key = getpass.getpass("센드엠 API 키 (화면에 표시되지 않음): ").strip()
    if not user_id or not api_key:
        print("아이디와 API 키를 모두 입력해야 합니다.")
        return 1

    # Keep the header names lowercase as written in Sendm's API guide.
    # urllib.request rewrites them to "User-id" and "Api-key".
    connection = http.client.HTTPSConnection("api.sendm.co.kr", timeout=15)
    try:
        connection.putrequest("GET", "/v2/info/coinInfo")
        connection.putheader("user-id", user_id)
        connection.putheader("api-key", api_key)
        connection.putheader("Accept", "application/json")
        connection.endheaders()
        response = connection.getresponse()
        status = response.status
        payload = json.load(response)
    except (OSError, TimeoutError, http.client.HTTPException) as error:
        print(f"연결 실패: {error}")
        return 1
    except (ValueError, UnicodeDecodeError):
        print("서버 응답을 JSON으로 읽을 수 없습니다.")
        return 1
    finally:
        connection.close()
        api_key = None

    code = str(payload.get("code", ""))
    print(f"HTTP 상태: {status}, 센드엠 코드: {code or '없음'}")
    if code != "0":
        print(f"실패 사유: {payload.get('message', '알 수 없음')}")
        return 1

    data = payload.get("data") or {}
    print(f"API 인증 성공. 잔액: {data.get('totalCoin', '확인 불가')}코인")
    print("이 검사는 조회만 수행하며 문자를 보내거나 코인을 차감하지 않습니다.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
