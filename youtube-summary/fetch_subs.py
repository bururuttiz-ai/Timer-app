# AI仙人ch の日本語字幕だけを取得するスクリプト（動画本体はダウンロードしません）
# 使い方: このファイルを置いたフォルダで  python fetch_subs.py  を実行
# 途中で止まっても、もう一度実行すれば続きから再開します。
import os, random, shutil, subprocess, sys, time

VIDEO_IDS = [
    "NCQZiT0e59g",
    "fzG7_2lKre4",
    "NkYIWyF9OFY",
    "lC5jxpYmatE",
    "02x6GYqPl-g",
    "sZNkoYbmRFk",
    "GkEXneBRc_k",
    "2m2_3FaHXrw",
    "LPHj86pkxco",
    "lrEmdYGkDCY",
    "J6rfwNmxKfs",
    "mLDYnTmU3XM",
    "q6_Wy9M3VlU",
    "ARsaFW3HAYA",
    "iahxsJLPlaA",
    "vVGYnI0SMYo",
    "blA3Uo2A0LI",
    "H9WxW-FPym4",
    "K3DwuE_9bps",
    "ZKIEEbx4200",
    "KXDUvNggpgw",
    "mW-iJyt7Pq0",
    "6UQEfJNsTUQ",
    "_gIkptDTi5Y",
    "YHaVog3xsXY",
    "WxnKQHmTPe4",
    "XQn9bevPEM8",
    "FM9wm9YIM2o",
    "iakLiPJbB3Y",
    "z2a7bF_xBz4",
    "QIlzqc7DO7c",
    "t9uSEpW5ExI",
    "pexvXmUqbvg",
    "ct5RIWHrqLU",
    "wOZBN3qiFvU",
    "bogsZSiAwmY",
    "gn67Aso0w50",
    "iyfy8DHn640",
    "2Rdppb837vc",
    "o8pnrqy7XXk",
    "W7jS-dZuIJY",
    "R5o_MPwm2Bc",
    "99EQoP7q3pA",
    "rqouV2ppJkk",
    "8rSfFsHCy98",
    "RNgFdrcT93A",
    "d5LdEr_sKkw",
    "MH1Ins7MDq4",
    "sJlPJwy-_T8",
    "RBY17LwqYXQ",
    "-S0HfE7gHDk",
    "MhAIyr617fA",
    "9_9U_Cby7jM",
    "HajiMEZ6v0A",
    "LRmmoSQgA60",
    "QqRnbOoUGJ8",
    "rWCwc60pXW4",
    "cy2dX--0RNQ",
    "eh9d_cRocc4",
    "86dXMl2rjH0",
    "DKaJuDcCHB8",
    "B8FtUPqgA0E",
    "3AVoFH32Mso",
    "FFaYklH8J7Y",
    "iVPe79Xg9KQ",
    "5ZfredrJD-M",
    "x57EsSeBNH8",
    "0iGEIp5qFGI",
    "9EOFekoD4ZQ",
    "MXw6uRve12g",
    "u4Vgz851jAI",
    "5Haopt8_x7g",
    "mlgiZGl9AZc",
    "N6GOtTGAFec",
    "XLX3ycKYKcE",
    "Fth5Qlcnp7M",
    "4yhXPLvRPfA",
    "7r3AD_E08iM",
    "Gn3O0Tq9kG0",
    "T5hkWPQGd_E",
]

HERE = os.path.dirname(os.path.abspath(__file__))
SUBS = os.path.join(HERE, "subs")
NOSUBS_FILE = os.path.join(HERE, "nosubs.txt")


def ensure_ytdlp():
    try:
        import yt_dlp  # noqa: F401
    except ImportError:
        print("yt-dlp が入っていないので、インストールします…")
        subprocess.check_call([sys.executable, "-m", "pip", "install", "-U", "yt-dlp"])


def already_done(vid, nosubs):
    if vid in nosubs:
        return True
    return any(f.startswith(vid + ".") for f in os.listdir(SUBS))


def main():
    ensure_ytdlp()
    os.makedirs(SUBS, exist_ok=True)
    nosubs = set()
    if os.path.exists(NOSUBS_FILE):
        nosubs = set(open(NOSUBS_FILE, encoding="utf-8").read().split())

    total = len(VIDEO_IDS)
    for i, vid in enumerate(VIDEO_IDS, 1):
        if already_done(vid, nosubs):
            print(f"[{i}/{total}] {vid} は取得済み。スキップ")
            continue
        print(f"[{i}/{total}] {vid} の字幕を取得中…")
        cmd = [
            sys.executable, "-m", "yt_dlp",
            "--skip-download", "--write-auto-subs", "--write-subs",
            "--sub-langs", "ja",
            "--sleep-requests", "1", "--sleep-interval", "5", "--max-sleep-interval", "15",
            "-o", os.path.join(SUBS, "%(id)s.%(ext)s"),
            f"https://www.youtube.com/watch?v={vid}",
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, encoding="utf-8", errors="replace")
        log = result.stdout + result.stderr
        if "not a bot" in log or "Sign in to confirm" in log or "HTTP Error 429" in log:
            print("\n!! YouTube のボット判定で止まりました。ここで終了します。")
            print("   数時間〜1日あけて、もう一度実行すると続きから再開します。")
            break
        if any(f.startswith(vid + ".") for f in os.listdir(SUBS)):
            print("   OK")
        elif "ERROR" in log:
            print("   エラーで取得できませんでした（次回の実行でやり直します）")
            print("   " + log.strip().splitlines()[-1][:200])
        else:
            print("   字幕が見つかりませんでした（字幕なしとして記録）")
            with open(NOSUBS_FILE, "a", encoding="utf-8") as f:
                f.write(vid + "\n")
        time.sleep(random.uniform(5, 15))

    if os.path.exists(NOSUBS_FILE):
        nosubs = set(open(NOSUBS_FILE, encoding="utf-8").read().split())
        shutil.copy(NOSUBS_FILE, SUBS)
    done = sum(already_done(v, nosubs) for v in VIDEO_IDS)
    zip_path = shutil.make_archive(os.path.join(HERE, "subs_for_claude"), "zip", HERE, "subs")
    print(f"\n完了: {done}/{total} 本を処理しました。")
    print(f"この ZIP ファイルを Claude に送ってください → {zip_path}")
    input("\nEnter キーを押すと閉じます")


if __name__ == "__main__":
    main()
