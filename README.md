# 정보채널 안드로이드 앱

[정보채널](https://wo8592-a11y.github.io/wo8592/) 게시판을 전체 화면으로 보여주는 웹뷰 앱입니다. 글쓰기, 사진·동영상 첨부, 파일 저장, 뒤로 가기를 앱 안에서 처리합니다. 같은 사이트의 페이지는 앱에 두고, 다른 주소는 기기의 브라우저로 엽니다.

Android Studio가 없어도 GitHub Actions가 서명된 APK를 만듭니다.

## 아이콘

밝은 하늘색에서 남색으로 이어지는 배경 위에, 겹친 글 카드와 말풍선을 올렸습니다. 여러 사람이 잡담과 유용한 글을 함께 올리는 게시판에 맞춘 모양입니다. 다시 그리려면 Python 3과 Pillow가 있는 컴퓨터에서 아래를 실행합니다.

```bash
py -3 tools/make_icon.py
```

## GitHub Actions로 APK 받기

1. [GitHub](https://github.com/new)에서 새 저장소를 만듭니다. README를 자동으로 추가하지 않습니다.
2. 이 프로젝트 폴더에서 아래를 실행합니다. `YOUR_NAME`과 `YOUR_REPO`는 본인 저장소 주소로 바꿉니다.

```bash
git init
git add .
git commit -m "Add InfoChannel WebView app"
git branch -M main
git remote add origin https://github.com/YOUR_NAME/YOUR_REPO.git
git push -u origin main
```

3. GitHub 저장소의 **Actions** 탭에서 **Build APK**가 끝날 때까지 기다립니다. 처음에는 Android SDK와 Gradle을 받아 10분 안팎이 걸릴 수 있습니다.
4. 완료된 워크플로를 열고, 아래 **Artifacts**의 `infochannel-release`를 받습니다. 안의 `app-release.apk`가 설치 파일입니다.
5. 휴대폰으로 APK를 보낸 뒤 설치합니다. 설치가 막히면 설정에서 이 출처의 앱 설치를 허용합니다.

같은 서명이라 이후 빌드로 앱을 업데이트할 수 있습니다. 서명에 쓰는 키스토어와 비밀번호(`infochannel`)는 이 저장소에 포함되어 있습니다. 수업·개인 설치용이며, Play 스토어에 올릴 앱이라면 키를 저장소 밖으로 옮겨야 합니다.

직접 다시 빌드하려면 Actions 탭의 **Run workflow**를 누르면 됩니다.
