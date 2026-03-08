import os
import time
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin

# ダウンロード対象のURLと保存先ディレクトリ
TARGET_URL = "https://soundeffect-lab.info/sound/animal/"
SAVE_DIR = "animal"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Referer": TARGET_URL,
    "Accept": "audio/mpeg, */*",
}

def download_sounds():
    if not os.path.exists(SAVE_DIR):
        os.makedirs(SAVE_DIR)
        print(f"フォルダ作成: {SAVE_DIR}")

    print(f"リスト取得中: {TARGET_URL}")
    session = requests.Session()
    
    try:
        response = session.get(TARGET_URL, headers=HEADERS)
        response.encoding = response.apparent_encoding
        
        if response.status_code != 200:
            print(f"アクセス失敗: Code {response.status_code}")
            return

        soup = BeautifulSoup(response.text, 'html.parser')
        # すべてのmp3リンクを取得
        links = soup.select('a[href$=".mp3"]')
        print(f"見つかったファイル数: {len(links)}")

        for a in links:
            relative_url = a['href']
            # battle2.html の場合はURLの組み立てに注意が必要なため urljoin を使用
            file_url = urljoin(TARGET_URL, relative_url)
            
            file_name = a.get('download')
            if not file_name:
                file_name = file_url.split('/')[-1]

            save_path = os.path.join(SAVE_DIR, file_name)

            if os.path.exists(save_path) and os.path.getsize(save_path) > 1000:
                print(f"スキップ: {file_name}")
                continue

            print(f"ダウンロード中: {file_name} ...", end="", flush=True)
            
            file_res = session.get(file_url, headers=HEADERS)
            
            if file_res.status_code == 200:
                with open(save_path, 'wb') as f:
                    f.write(file_res.content)
                print(" 完了")
            else:
                print(f" 失敗 (Code: {file_res.status_code})")

            # 2秒待機
            time.sleep(2.0)

    except Exception as e:
        print(f"\nエラー: {e}")

    print("\n完了しました。")

if __name__ == "__main__":
    download_sounds()