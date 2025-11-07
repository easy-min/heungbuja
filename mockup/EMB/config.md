# [EMB] 라즈베리파이 초기 설정 가이드

> **목표:** 모니터, 키보드 없이 라즈베리파이를 설정하고, 원격(SSH, VNC)으로 제어할 수 있는 기본 개발 환경을 구축한다.

---

## 1. 준비물

- 라즈베리파이 4 본체
- 16GB 이상 Micro SD 카드 및 리더기
- 전원 어댑터
- Wi-Fi 네트워크 정보 (SSID 및 비밀번호)
- 개발용 PC (Windows/macOS)

---

## 2. OS 설치 및 원격 접속 사전 설정

1.  **Raspberry Pi Imager 설치:** PC에 공식 Imager 프로그램을 설치한다.
2.  **Imager 설정:**
    - **OS 선택:** `Raspberry Pi OS (other)` -> `Raspberry Pi OS Lite (64-bit)`
    - **저장소 선택:** 연결된 SD 카드 선택
    - **고급 설정 (톱니바퀴 아이콘):**
      - **Hostname:** `raspberrypi.local` (기본값 사용)
      - **SSH 활성화:** `비밀번호 인증` 사용으로 체크
      - **사용자 설정:** 사용할 `사용자 이름`과 `비밀번호` 지정
      - **Wi-Fi 설정:** 접속할 `SSID`와 `비밀번호`를 정확하게 입력
      - **지역 설정:** 시간대 `Asia/Seoul`, 키보드 `us`
3.  **OS 설치:** [WRITE] 버튼을 눌러 SD 카드에 OS를 설치한다.

---

## 3. 원격 접속 (SSH)

1.  OS 설치가 완료된 SD카드를 라즈베리파이에 삽입하고 전원을 연결한다.
2.  약 3분 정도 부팅 시간을 기다린다.
3.  PC에서 터미널을 열고 아래 명령어로 접속한다.

    ```bash
    ssh a103@raspberrypi.local
    ```

4.  비밀번호를 입력하여 접속에 성공하면, 프롬프트가 `a103@raspberrypi:~ $` 형태로 바뀐다.

---

## 4. 시스템 최신화 및 원격 GUI (VNC) 설정

> SSH로 접속된 라즈베리파이 터미널에서 아래 명령어를 순서대로 실행한다.

1.  **시스템 최신화:** (시간이 다소 소요됨)

    ```bash
    sudo apt update
    sudo apt upgrade -y
    ```

2.  **GUI 환경 및 VNC 서버 설치:**

    ```bash
    # 최소 GUI 환경 설치 (X-Window, Openbox)
    sudo apt install --no-install-recommends xserver-xorg xinit openbox -y

    # 크로미움 브라우저 설치
    sudo apt install chromium -y

    # VNC 서버 설치
    sudo apt install tightvncserver -y
    ```

3.  **VNC 서버 최초 실행 및 비밀번호 설정:**

    ```bash
    # 아래 명령 실행 후, VNC 접속용 비밀번호(8자 이하)를 설정
    tightvncserver
    # View-only password 설정 여부는 'n' 입력
    ```

4.  **VNC 자동 실행 스크립트 설정:**

    ```bash
    # nano 편집기로 xstartup 파일 열기
    nano ~/.vnc/xstartup

    # 열린 파일에 아래 내용 작성 후 저장 (Ctrl+X -> Y -> Enter)
    #!/bin/sh
    xrdb $HOME/.Xresources
    openbox-session &
    ```

    ```bash
    # 스크립트에 실행 권한 부여
    chmod +x ~/.vnc/xstartup
    ```

5.  **VNC 서버 실행:**

    ```bash
    # 해상도를 지정하여 VNC 서버 실행 (예: 1920x1080)
    tightvncserver -geometry 1920x1080
    ```

---

## 5. 원격 GUI 접속 (VNC)

1.  PC에 `VNC Viewer` 프로그램을 설치한다.
2.  VNC Viewer를 실행하고, 주소창에 아래 형식으로 입력하여 접속한다.

    ```
    # hostname -I 명령어로 확인한 라즈베리파이의 IP 주소 사용
    [라즈베리파이 IP 주소]:1
    ```

3.  비밀번호는 `tightvncserver` 설정 시 지정한 8자 이하의 비밀번호를 입력한다.
4.  접속 성공 시, 마우스 커서가 있는 회색의 텅 빈 화면이 나타나면 모든 설정이 완료된 것이다.
