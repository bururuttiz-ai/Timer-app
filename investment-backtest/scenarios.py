#!/usr/bin/env python3
"""
「この先どうなるか」のシナリオ別に、日米の配分を比べるスクリプト

過去の成績は、最近の日本株の急騰を含むかどうかで結論が変わってしまう。
そこで未来を予想する代わりに、実際に起きた 20 年間の値動きを「この先の 20 年」に当てはめ、
どのシナリオでも大きく外さない配分はどれかを調べる。

シナリオ（現地通貨・配当なしの実際の 20 年間）:
  日本株
    崩壊   : 日経平均 1990-01〜2009-12（バブル崩壊後の 20 年）
    一進一退: 日経平均 2000-01〜2019-12
    右肩上がり: S&P500 の直近 20 年（日本株が米国並みに伸び続けた場合）
  米国株
    崩壊   : 日経平均 1990-01〜2009-12（米国が日本と同じ道をたどった場合）
    一進一退: S&P500 2000-01〜2019-12（IT バブル崩壊・リーマンを含む）
    右肩上がり: S&P500 の直近 20 年
  為替（ドル円）: 横ばい / 年2%の円高 / 年2%の円安

注意: 別々の時代をつなぎ合わせているので、日米が同時に下がるといった「連動」は再現していない。
予想ではなく「こうなったらどうなるか」を見るためのもの。

使い方:
  python scenarios.py --data-dir results/data_long
"""

from __future__ import annotations

import argparse
import itertools
from pathlib import Path

import numpy as np
import pandas as pd

from backtest import load_saved, simulate_array

YEARS = 20


def window(prices: pd.Series, start: str | None, months: int) -> np.ndarray:
    r = prices.pct_change().dropna()
    if start is None:
        return r.iloc[-months:].values
    r = r.loc[start:]
    if len(r) < months:
        raise SystemExit(f"{start} から {months} ヶ月分のデータがありません")
    return r.iloc[:months].values


def build_scenarios(prices: pd.DataFrame):
    m = YEARS * 12
    nikkei, spx = prices["JP"].dropna(), prices["US"].dropna()
    burst = window(nikkei, "1990-01", m)
    jp = {
        "崩壊": burst,
        "一進一退": window(nikkei, "2000-01", m),
        "右肩上がり": window(spx, None, m),
    }
    us = {
        "崩壊": burst,
        "一進一退": window(spx, "2000-01", m),
        "右肩上がり": window(spx, None, m),
    }
    fx = {"横ばい": 0.0, "円高2%/年": -0.02, "円安2%/年": 0.02}
    return jp, us, fx


def describe(name: str, r: np.ndarray) -> str:
    total = np.prod(1 + r)
    return f"{name}: 20年で {total:.2f}倍（年率 {total ** (1 / YEARS) - 1:+.1%}）"


def main(argv=None):
    ap = argparse.ArgumentParser(description="シナリオ別の配分比較")
    ap.add_argument("--data-dir", default="results/data_long", help="backtest.py --save-data で保存したフォルダ（長期モード）")
    ap.add_argument("--jp-weights", type=float, nargs="+", default=[0, 0.1, 0.2, 0.3, 0.5, 1.0],
                    help="日本株の比率（残りは米国株）")
    ap.add_argument("--out", default="results/scenarios.csv")
    args = ap.parse_args(argv)

    prices, _fx, _rates = load_saved(Path(args.data_dir))
    jp, us, fx = build_scenarios(prices)
    months = np.tile(np.arange(1, 13), YEARS)

    print("■ シナリオの中身（現地通貨・配当なし）")
    for label, d in (("日本", jp), ("米国", us)):
        for k, r in d.items():
            print(f"  {label}株 {describe(k, r)}")
    print()

    rows = []
    for (jk, jr), (uk, ur), (fk, fg) in itertools.product(jp.items(), us.items(), fx.items()):
        fx_m = (1 + fg) ** (1 / 12) - 1
        us_jpy = (1 + ur) * (1 + fx_m) - 1
        R = np.c_[us_jpy, jr]
        for w_jp in args.jp_weights:
            w = np.array([1 - w_jp, w_jp])
            out, _ = simulate_array(R, months, w, "annual")
            rows.append({"日本株": jk, "米国株": uk, "為替": fk, "日本比率": w_jp,
                         "20年後の倍率": float(np.prod(1 + out))})
    df = pd.DataFrame(rows)
    best = df.groupby(["日本株", "米国株", "為替"])["20年後の倍率"].transform("max")
    df["最善比"] = df["20年後の倍率"] / best
    Path(args.out).parent.mkdir(parents=True, exist_ok=True)
    df.to_csv(args.out, index=False, encoding="utf-8-sig")

    order = list(jp)
    flat = df[df["為替"] == "横ばい"].copy()
    flat["シナリオ"] = "日" + flat["日本株"] + "×米" + flat["米国株"]
    cols = [f"日{j}×米{u}" for j in order for u in order]
    table = flat.pivot(index="日本比率", columns="シナリオ", values="20年後の倍率")[cols]
    table.index = [f"日本{w:.0%}" for w in table.index]
    print("■ 20年後に何倍になるか（為替は横ばい、年1回リバランス）")
    for chunk in (cols[:3], cols[3:6], cols[6:]):
        print(table[chunk].map(lambda v: f"{v:.2f}倍").to_string())
        print()

    summary = df.groupby("日本比率").agg(
        最悪=("20年後の倍率", "min"),
        中央=("20年後の倍率", "median"),
        最良=("20年後の倍率", "max"),
        最善比_最悪=("最善比", "min"),
    )
    summary.index = [f"日本{w:.0%}" for w in summary.index]
    shown = summary.copy()
    for c in ("最悪", "中央", "最良"):
        shown[c] = shown[c].map(lambda v: f"{v:.2f}倍")
    shown["最善比_最悪"] = shown["最善比_最悪"].map(lambda v: f"{v:.0%}")
    print(f"■ 全 {len(jp) * len(us) * len(fx)} シナリオ（為替3通りを含む）でのまとめ")
    print("  最善比_最悪 = そのシナリオで一番良かった配分に比べて、最悪どこまで見劣りしたか（後悔の大きさ）")
    print(shown.to_string())
    print()
    print(f"詳細は {Path(args.out).resolve()} に保存しました。")


if __name__ == "__main__":
    main()
