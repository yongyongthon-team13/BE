#!/usr/bin/env python3
"""Run the Spring phone login flow with one real Sendm SMS, without saving secrets."""

import base64
import getpass
import json
import os
from pathlib import Path
import re
import secrets
import signal
import socket
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parent.parent
JAVA_HOME = Path("/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home")


def phone(prompt: str) -> str:
    number = re.sub(r"[ -]", "", input(prompt).strip())
    if not re.fullmatch(r"010[0-9]{8}", number):
        raise ValueError("010으로 시작하는 11자리 휴대폰 번호를 입력하세요.")
    return number


def post(base: str, path: str, body: dict) -> tuple[int, dict]:
    request = Request(
        base + path,
        data=json.dumps(body).encode("utf-8"),
        headers={"Content-Type": "application/json"},
    )
    try:
        with urlopen(request, timeout=15) as response:
            return response.status, json.load(response) if response.status != 202 else {}
    except HTTPError as error:
        try:
            detail = json.load(error)
        except (ValueError, UnicodeDecodeError):
            detail = {}
        return error.code, detail


def wait_for_server(port: int, process: subprocess.Popen) -> bool:
    for _ in range(120):
        if process.poll() is not None:
            return False
        try:
            with socket.create_connection(("127.0.0.1", port), timeout=0.3):
                return True
        except OSError:
            time.sleep(0.5)
    return False


def main() -> int:
    print("실제 SMS 한 건으로 스프링 전화번호 로그인을 확인합니다. 약 11코인이 차감될 수 있습니다.")
    user_id = input("센드엠 로그인 아이디: ").strip()
    api_key = getpass.getpass("센드엠 API 키 (화면에 표시되지 않음): ").strip()
    try:
        sender = phone("승인된 발신번호: ")
        recipient = phone("인증문자를 받을 번호: ")
    except ValueError as error:
        print(error)
        return 1
    if not user_id or not api_key:
        print("센드엠 아이디와 API 키를 모두 입력해야 합니다.")
        return 1
    if input("위 수신번호로 인증문자 한 건을 보내시겠습니까? (yes 입력): ").strip() != "yes":
        print("발송하지 않았습니다.")
        return 0

    with socket.socket() as listener:
        listener.bind(("127.0.0.1", 0))
        port = listener.getsockname()[1]
    environment = os.environ.copy()
    environment.update({
        "APP_DATA_KEY": base64.b64encode(secrets.token_bytes(32)).decode(),
        "APP_JWT_SECRET": base64.b64encode(secrets.token_bytes(32)).decode(),
        "DB_URL": f"jdbc:h2:mem:sendm_test_{secrets.token_hex(6)};MODE=MySQL",
        "DB_USERNAME": "sa",
        "DB_PASSWORD": "",
        "SMS_PROVIDER": "sendm",
        "SENDM_USER_ID": user_id,
        "SENDM_API_KEY": api_key,
        "SENDM_SENDER": sender,
        "SERVER_PORT": str(port),
    })
    if JAVA_HOME.is_dir():
        environment["JAVA_HOME"] = str(JAVA_HOME)
        environment["PATH"] = str(JAVA_HOME / "bin") + os.pathsep + environment.get("PATH", "")
    api_key = None
    process = subprocess.Popen(
        [str(ROOT / "gradlew"), "bootRun", "--console=plain"],
        cwd=ROOT,
        env=environment,
        stdin=subprocess.DEVNULL,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        start_new_session=True,
    )
    environment.clear()
    base = f"http://127.0.0.1:{port}"
    try:
        print("스프링 서버 시작을 기다리는 중...")
        if not wait_for_server(port, process):
            print("서버를 시작하지 못했습니다. 문자 발송 요청은 하지 않았습니다.")
            return 1
        status, detail = post(base, "/api/v1/auth/phone-verifications", {"phoneNumber": recipient})
        print(f"인증번호 요청 HTTP 상태: {status}")
        if status != 202:
            print(f"요청 실패: {detail.get('detail', detail.get('title', '사유 확인 필요'))}")
            return 1
        code = input("문자로 받은 6자리 인증번호: ").strip()
        if not re.fullmatch(r"[0-9]{6}", code):
            print("인증번호는 숫자 6자리여야 합니다.")
            return 1
        status, result = post(base, "/api/v1/auth/login", {
            "phoneNumber": recipient, "verificationCode": code, "role": "ELDER"
        })
        print(f"로그인 HTTP 상태: {status}")
        if status == 200 and result.get("accessToken") and result.get("refreshToken"):
            print("실제 문자 수신과 스프링 로그인 성공. 토큰은 화면에 표시하지 않습니다.")
            return 0
        print(f"로그인 실패: {result.get('detail', result.get('title', '사유 확인 필요'))}")
        return 1
    except (OSError, URLError, TimeoutError, ValueError) as error:
        print(f"테스트 실패: {error}")
        return 1
    finally:
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
        try:
            process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            os.killpg(process.pid, signal.SIGKILL)
            process.wait()


if __name__ == "__main__":
    sys.exit(main())
