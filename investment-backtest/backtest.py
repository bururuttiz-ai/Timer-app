#!/usr/bin/env python3
"""
指数の長期検証スクリプト（円建て投資家の視点）

やること:
  1. 米国株・日本株・全世界株などの月次データを取得し、すべて「円建て」に換算
  2. 外貨建て資産のリターンを「現地通貨リターン」と「為替要因」に分解
  3. 10/15/20年などのローリングリターン（どの時点で始めても何%だったか）
  4. 最大ドローダウンと、元本回復までにかかった期間
  5. 配分の比較（例: 米国100% / 米国80%+日本20% / 半々）、年1回リバランス
  6. 為替ヘッジした場合の近似（ヘッジコスト＝日米短期金利差）

データ:
  - 株価: Yahoo Finance（yfinance）
  - ドル円・短期金利: FRED（米セントルイス連銀、APIキー不要）

使い方:
  python backtest.py                 # 長期モード（S&P500 と 日経平均、1971年〜、配当なし）
  python backtest.py --mode etf      # ETFモード（SPY / TOPIX ETF / ACWI、配当込み、2008年〜）
  python backtest.py --demo          # ネット接続なしでダミーデータを使った動作確認
  python backtest.py --help          # その他のオプション
"""

from __future__ import annotations

import argparse
import sys
import time
from pathlib import Path

import numpy as np
import pandas as pd

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
import matplotlib.dates  # noqa: E402
import matplotlib.ticker as mticker  # noqa: E402

try:
    pd.tseries.frequencies.to_offset("ME")
    MONTH_END = "ME"
except ValueError:  # pandas < 2.2
    MONTH_END = "M"

pd.set_option("display.width", 250)
pd.set_option("display.max_columns", 50)

FRED_URL = "https://fred.stlouisfed.org/graph/fredgraph.csv?id={}"
FRED_USDJPY = "DEXJPUS"  # 日次 ドル円（1971年〜）
FRED_RATE_US = "IR3TIB01USM156N"  # 米 3ヶ月金利（月次, %）
FRED_RATE_JP = "IR3TIB01JPM156N"  # 日 3ヶ月金利（月次, %）

# 名前: (ティッカー, 通貨)
PRESETS = {
    "long": {
        "assets": {"US": ("^GSPC", "USD"), "JP": ("^N225", "JPY")},
        "portfolios": {
            "US50JP50": {"US": 0.5, "JP": 0.5},
            "US80JP20": {"US": 0.8, "JP": 0.2},
        },
        "start": "1971-01-01",
        "note": "長期モードは価格指数のため配当を含みません（配当込みだと年2%前後上乗せ）。",
    },
    "etf": {
        "assets": {
            "US": ("SPY", "USD"),
            "JP": ("1306.T", "JPY"),
            "WORLD": ("ACWI", "USD"),
        },
        "portfolios": {
            "WORLD80JP20": {"WORLD": 0.8, "JP": 0.2},
            "WORLD50JP50": {"WORLD": 0.5, "JP": 0.5},
        },
        "start": "2008-04-01",
        "note": "ETFモードは配当込み（調整後終値）。信託報酬は差し引き済みですが期間が短い点に注意。",
    },
}

# 参照パレット（dataviz の categorical 順）。エンティティごとに固定で割り当てる
PALETTE = ["#2a78d6", "#eb6834", "#1baf7a", "#eda100", "#e87ba4", "#008300", "#4a3aa7", "#e34948"]
SURFACE = "#fcfcfb"
TEXT_PRIMARY = "#0b0b0b"
TEXT_SECONDARY = "#52514e"
GRID = "#e4e3df"


# ---------------------------------------------------------------- データ取得


def to_month_end(s: pd.Series) -> pd.Series:
    s = s.dropna()
    s.index = pd.to_datetime(s.index).tz_localize(None) if getattr(s.index, "tz", None) else pd.to_datetime(s.index)
    return s.resample(MONTH_END).last().dropna()


def fetch_yahoo(ticker: str, start: str) -> pd.Series:
    import yfinance as yf

    df = None
    for attempt in range(4):  # Yahoo は一時的に失敗することがあるので再試行
        try:
            df = yf.download(ticker, start=start, auto_adjust=True, progress=False)
        except Exception as e:
            print(f"  {ticker} の取得に失敗（{e}）。再試行します…")
        if df is not None and not df.empty:
            break
        time.sleep(5 * (attempt + 1))
    if df is None or df.empty:
        raise RuntimeError(f"Yahoo Finance から {ticker} を取得できませんでした")
    close = df["Close"]
    if isinstance(close, pd.DataFrame):
        close = close.iloc[:, 0]
    return to_month_end(close.rename(ticker))


def fetch_fred(series_id: str) -> pd.Series:
    df = pd.read_csv(FRED_URL.format(series_id), na_values=".")
    df.iloc[:, 0] = pd.to_datetime(df.iloc[:, 0])
    s = df.set_index(df.columns[0])[series_id].astype(float)
    s.index = pd.DatetimeIndex(s.index)
    return to_month_end(s.rename(series_id))


def fetch_usdjpy(start: str) -> pd.Series:
    try:
        return fetch_fred(FRED_USDJPY)
    except Exception as e:  # FRED が落ちているときは Yahoo の JPY=X（1996年〜）
        print(f"  FRED からドル円を取得できず（{e}）。Yahoo の JPY=X を使います。")
        return fetch_yahoo("JPY=X", start)


def load_real(assets: dict, start: str):
    prices = {}
    for name, (ticker, _ccy) in assets.items():
        print(f"  {name}: {ticker} を取得中…")
        prices[name] = fetch_yahoo(ticker, start)
    print("  ドル円を取得中…")
    fx = fetch_usdjpy(start)
    rates = None
    try:
        print("  日米短期金利を取得中（ヘッジコストとシャープレシオ用）…")
        rates = pd.DataFrame({"US": fetch_fred(FRED_RATE_US), "JP": fetch_fred(FRED_RATE_JP)})
    except Exception as e:
        print(f"  金利データを取得できませんでした（{e}）。ヘッジ比較は省略し、無リスク金利は0%とします。")
    return pd.DataFrame(prices), fx, rates


def load_demo(assets: dict, start: str, seed: int = 0):
    """ネット接続なしで動作確認するためのダミーデータ（実在の値ではありません）。"""
    rng = np.random.default_rng(seed)
    idx = pd.date_range(start, "2026-08-31", freq=MONTH_END)
    n = len(idx)
    params = {"USD": (0.075, 0.15), "JPY": (0.05, 0.18)}
    prices = {}
    for name, (_t, ccy) in assets.items():
        mu, sig = params[ccy]
        r = rng.normal(mu / 12, sig / np.sqrt(12), n)
        prices[name] = pd.Series(100 * np.exp(np.cumsum(r)), idx)
    fx = pd.Series(150 * np.exp(np.cumsum(rng.normal(-0.01 / 12, 0.10 / np.sqrt(12), n))), idx)
    rates = pd.DataFrame({"US": 4.0 + np.cumsum(rng.normal(0, 0.15, n)).clip(-4, 6),
                          "JP": 0.5 + np.cumsum(rng.normal(0, 0.05, n)).clip(-0.5, 3)}, idx)
    return pd.DataFrame(prices), fx, rates


# ---------------------------------------------------------------- 計算


def build_returns(prices: pd.DataFrame, fx: pd.Series, rates, assets: dict, hedge: bool):
    """円建て月次リターン・現地通貨リターン・為替リターンを作る。"""
    fx_ret = fx.pct_change()
    jpy, local = {}, {}
    for name, (_t, ccy) in assets.items():
        p = prices[name].dropna()
        r_local = p.pct_change()
        local[name] = r_local
        if ccy == "USD":
            jpy[name] = (1 + r_local) * (1 + fx_ret.reindex(r_local.index)) - 1
            if hedge and rates is not None:
                # ヘッジ付き ≈ 現地通貨リターン + (円金利 − ドル金利)/12
                diff = (rates["JP"] - rates["US"]).reindex(r_local.index) / 100 / 12
                jpy[f"{name}_H"] = r_local + diff
        else:
            jpy[name] = r_local
    return pd.DataFrame(jpy), pd.DataFrame(local), fx_ret


REBALANCE_METHODS = ["none", "annual", "quarterly", "monthly", "band"]


def _is_rebalance_month(mode: str, month: int) -> bool:
    return mode == "monthly" or (mode == "quarterly" and month % 3 == 0) or (mode == "annual" and month == 12)


def simulate_array(R: np.ndarray, months: np.ndarray, w: np.ndarray, mode: str,
                   band: float = 0.05, cost: float = 0.0):
    """月次リターン行列 R（T×資産数）から、配分 w のポートフォリオの月次リターンと売買回数を返す。

    mode: none（買いっぱなし）/ annual（毎年12月末）/ quarterly / monthly /
          band（いずれかの資産が目標比率から band 以上ずれたら戻す）
    cost: 売買額に対するコスト率（例 0.001 = 0.1%）
    """
    hold = w.copy()
    out = np.empty(len(R))
    trades = 0
    for t in range(len(R)):
        hold = hold * (1 + R[t])
        total = hold.sum()
        cur = hold / total
        drift = np.abs(cur - w)
        if mode == "band":
            do = drift.max() >= band
        else:
            do = _is_rebalance_month(mode, months[t])
        if do and drift.sum() > 0:
            total *= 1 - cost * drift.sum()
            hold = w.copy()
            trades += 1
        else:
            hold = cur
        out[t] = total - 1
    return out, trades


def simulate_portfolio(rets: pd.DataFrame, weights: dict, mode: str, band: float = 0.05,
                       cost: float = 0.0) -> pd.Series:
    cols = list(weights)
    r = rets[cols].dropna()
    w = np.array([weights[c] for c in cols], dtype=float)
    out, _ = simulate_array(r.values, r.index.month.values, w / w.sum(), mode, band, cost)
    return pd.Series(out, r.index)


def sharpe(r, rf) -> float:
    """年率シャープレシオ＝（平均超過リターン×12）÷（超過リターンの標準偏差×√12）"""
    ex = np.asarray(r) - np.asarray(rf)
    sd = ex.std(ddof=1)
    return np.nan if sd == 0 else ex.mean() * 12 / (sd * np.sqrt(12))


def compare_rebalance(rets: pd.DataFrame, rf: pd.Series, portfolios: dict, window_years: int,
                      band: float, cost: float, step: int = 1):
    """リバランス方法ごとに、全期間の成績と、N年ローリング窓でのシャープレシオを比べる。

    ローリング窓は「その月に始めて N 年保有した場合」を毎月ずらして計算する。
    1つの期間だけだと偶然に左右されるので、何割の開始時点で買いっぱなしに勝てたかを見る。
    """
    full_rows, win_rows, rolling = [], [], {}
    m = window_years * 12
    for pname, weights in portfolios.items():
        cols = list(weights)
        r = rets[cols].dropna()
        if len(r) < 24:
            continue
        w = np.array([weights[c] for c in cols], dtype=float)
        w = w / w.sum()
        R, months = r.values, r.index.month.values
        rf_a = rf.reindex(r.index).fillna(0).values
        for mode in REBALANCE_METHODS:
            out, trades = simulate_array(R, months, w, mode, band, cost)
            ser = pd.Series(out, r.index)
            full_rows.append({
                "配分": pname, "方法": mode,
                "年率リターン": cagr(ser), "年率リスク": ser.std() * np.sqrt(12),
                "シャープ": sharpe(out, rf_a), "最大下落": drawdown(ser).min(),
                "売買回数/年": trades / (len(r) / 12),
            })
        if len(r) < m + 12:
            continue
        starts = range(0, len(r) - m + 1, step)
        sh = {mode: [] for mode in REBALANCE_METHODS}
        for i in starts:
            Rw, mw, rfw = R[i:i + m], months[i:i + m], rf_a[i:i + m]
            for mode in REBALANCE_METHODS:
                out, _ = simulate_array(Rw, mw, w, mode, band, cost)
                sh[mode].append(sharpe(out, rfw))
        ends = r.index[[i + m - 1 for i in starts]]
        base = np.array(sh["none"])
        rolling[pname] = {}
        for mode in REBALANCE_METHODS[1:]:
            diff = np.array(sh[mode]) - base
            rolling[pname][mode] = pd.Series(diff, ends)
            win_rows.append({
                "配分": pname, "方法": mode, "窓の数": len(diff),
                "買いっぱなしに勝った割合": (diff > 0).mean(),
                "シャープ差_中央": np.median(diff), "シャープ差_最小": diff.min(), "シャープ差_最大": diff.max(),
            })
    full = pd.DataFrame(full_rows).set_index(["配分", "方法"])
    wins = pd.DataFrame(win_rows).set_index(["配分", "方法"]) if win_rows else pd.DataFrame()
    return full, wins, rolling


def cagr(r: pd.Series) -> float:
    r = r.dropna()
    if len(r) == 0:
        return np.nan
    return (1 + r).prod() ** (12 / len(r)) - 1


def drawdown(r: pd.Series) -> pd.Series:
    w = (1 + r.dropna()).cumprod()
    return w / w.cummax() - 1


def longest_underwater(r: pd.Series) -> int:
    dd = drawdown(r)
    longest = cur = 0
    for v in dd:
        cur = cur + 1 if v < 0 else 0
        longest = max(longest, cur)
    return longest


def rolling_cagr(r: pd.Series, years: int) -> pd.Series:
    m = years * 12
    logr = np.log1p(r.dropna())
    return np.expm1(logr.rolling(m).sum() * 12 / m)


def summarize(rets: pd.DataFrame, rf: pd.Series, windows: list[int]) -> pd.DataFrame:
    rows = {}
    for name in rets:
        r = rets[name].dropna()
        row = {
            "開始": r.index[0].strftime("%Y-%m"),
            "年率リターン": cagr(r),
            "年率リスク": r.std() * np.sqrt(12),
            "シャープ": sharpe(r, rf.reindex(r.index).fillna(0)),
            "最大下落": drawdown(r).min(),
            "最長含み損(年)": longest_underwater(r) / 12,
            "最悪12ヶ月": (np.expm1(np.log1p(r).rolling(12).sum())).min(),
        }
        for y in windows:
            rc = rolling_cagr(r, y).dropna()
            if rc.empty:
                continue
            row[f"{y}年_最悪"] = rc.min()
            row[f"{y}年_中央"] = rc.median()
            row[f"{y}年_最良"] = rc.max()
            row[f"{y}年_マイナス率"] = (rc < 0).mean()
        rows[name] = row
    return pd.DataFrame(rows).T


def fx_decomposition(local: pd.DataFrame, jpy: pd.DataFrame, fx_ret: pd.Series, assets: dict) -> pd.DataFrame:
    rows = {}
    for name, (_t, ccy) in assets.items():
        if ccy != "USD":
            continue
        rl = local[name].dropna()
        rf = fx_ret.reindex(rl.index)
        rows[name] = {
            "期間": f"{rl.index[0]:%Y-%m}〜{rl.index[-1]:%Y-%m}",
            "現地通貨 年率": cagr(rl),
            "為替 年率": cagr(rf),
            "円建て 年率": cagr(jpy[name].reindex(rl.index)),
            "現地通貨 リスク": rl.std() * np.sqrt(12),
            "円建て リスク": jpy[name].reindex(rl.index).std() * np.sqrt(12),
            "株と為替の相関": rl.corr(rf),
        }
    return pd.DataFrame(rows).T


# ---------------------------------------------------------------- グラフ


def style_axes(ax, title: str, ylabel: str = ""):
    ax.set_facecolor(SURFACE)
    ax.set_title(title, loc="left", color=TEXT_PRIMARY, fontsize=12, fontweight="bold")
    ax.set_ylabel(ylabel, color=TEXT_SECONDARY)
    ax.grid(axis="y", color=GRID, linewidth=0.8)
    ax.tick_params(colors=TEXT_SECONDARY, length=0)
    for side in ("top", "right", "left"):
        ax.spines[side].set_visible(False)
    ax.spines["bottom"].set_color(GRID)


def new_fig(rows: int = 1, height: float = 5.0):
    fig, axes = plt.subplots(rows, 1, figsize=(11, height * rows), squeeze=False)
    fig.patch.set_facecolor(SURFACE)
    return fig, axes[:, 0]


def label_line_ends(ax, series: dict, colors: dict, fmt, min_gap_px: float = 14):
    """線の右端に名前と値を置く。重なる場合は上下にずらす。"""
    ends = []
    for name, s in series.items():
        s = s.dropna()
        if not s.empty:
            ends.append((name, s.index[-1], s.iloc[-1]))
    if not ends:
        return
    ax.figure.canvas.draw()
    to_px = ax.transData.transform
    placed = sorted(((to_px((matplotlib.dates.date2num(x), y))[1], name, x, y) for name, x, y in ends))
    ys = [p[0] for p in placed]
    for i in range(1, len(ys)):
        ys[i] = max(ys[i], ys[i - 1] + min_gap_px)
    pt_per_px = 72 / ax.figure.dpi
    for (orig, name, x, y), new in zip(placed, ys):
        ax.annotate(f"{name} {fmt(y)}", (x, y), xytext=(8, (new - orig) * pt_per_px),
                    textcoords="offset points", va="center", fontsize=9, color=TEXT_PRIMARY)
        ax.plot(x, y, "o", ms=5, color=colors[name], mec=SURFACE, mew=1.5)


def log_axis(ax):
    ax.set_yscale("log")
    fmt = mticker.FuncFormatter(lambda v, _: f"{v:g}x")
    ax.yaxis.set_major_formatter(fmt)
    ax.yaxis.set_minor_formatter(mticker.FuncFormatter(lambda v, _: f"{v:g}x" if f"{v:g}"[0] in "25" else ""))


def pct_axis(ax):
    ax.yaxis.set_major_locator(mticker.MaxNLocator(steps=[1, 2, 5, 10]))
    ax.yaxis.set_major_formatter(mticker.PercentFormatter(1.0, decimals=0))


def finish(fig, ax, path: Path):
    ax.legend(frameon=False, loc="upper left", fontsize=9, labelcolor=TEXT_SECONDARY)
    fig.tight_layout()
    fig.savefig(path, dpi=130, facecolor=SURFACE)
    plt.close(fig)


def plot_growth(rets: pd.DataFrame, colors: dict, out: Path, title: str = "Growth of 1 JPY"):
    fig, (ax,) = new_fig()
    common_start = rets.dropna().index[0]
    series = {}
    for name in rets:
        r = rets[name].loc[common_start:].dropna()
        series[name] = (1 + r).cumprod()
        ax.plot(series[name].index, series[name].values, lw=2, color=colors[name], label=name)
    log_axis(ax)
    style_axes(ax, f"{title} (JPY-based, from {common_start:%Y-%m}, log scale)", "multiple")
    ax.margins(x=0.1)
    label_line_ends(ax, series, colors, lambda v: f"{v:.1f}x")
    finish(fig, ax, out)


def plot_rolling(rets: pd.DataFrame, years: int, colors: dict, out: Path):
    fig, (ax,) = new_fig()
    series = {name: rolling_cagr(rets[name], years) for name in rets}
    for name, s in series.items():
        ax.plot(s.index, s.values, lw=2, color=colors[name], label=name)
    ax.axhline(0, color=TEXT_SECONDARY, lw=1)
    pct_axis(ax)
    style_axes(ax, f"Rolling {years}-year annualized return (JPY), plotted at END of each window", "per year")
    ax.margins(x=0.1)
    label_line_ends(ax, series, colors, lambda v: f"{v:.1%}")
    finish(fig, ax, out)


def plot_drawdown(rets: pd.DataFrame, colors: dict, out: Path):
    fig, (ax,) = new_fig()
    for name in rets:
        dd = drawdown(rets[name])
        ax.plot(dd.index, dd.values, lw=2, color=colors[name], label=name)
    pct_axis(ax)
    style_axes(ax, "Drawdown from previous peak (JPY)", "")
    finish(fig, ax, out)


def plot_fx(local: pd.DataFrame, jpy: pd.DataFrame, fx_ret: pd.Series, assets: dict, out: Path):
    usd = [n for n, (_t, c) in assets.items() if c == "USD"]
    if not usd:
        return
    fig, axes = new_fig(len(usd), 4.5)
    parts = {"Local currency": PALETTE[0], "USD/JPY (FX)": PALETTE[1], "JPY-based": PALETTE[2]}
    for ax, name in zip(axes, usd):
        rl = local[name].dropna()
        series = {
            "Local currency": (1 + rl).cumprod(),
            "USD/JPY (FX)": (1 + fx_ret.reindex(rl.index).fillna(0)).cumprod(),
            "JPY-based": (1 + jpy[name].reindex(rl.index)).cumprod(),
        }
        for k, s in series.items():
            ax.plot(s.index, s.values, lw=2, color=parts[k], label=k)
        log_axis(ax)
        style_axes(ax, f"{name}: where the JPY return came from (stock vs. currency)", "multiple")
        ax.margins(x=0.12)
        label_line_ends(ax, series, parts, lambda v: f"{v:.1f}x")
        ax.legend(frameon=False, loc="upper left", fontsize=9, labelcolor=TEXT_SECONDARY)
    fig.tight_layout()
    fig.savefig(out, dpi=130, facecolor=SURFACE)
    plt.close(fig)


def plot_rebalance(rolling: dict, window_years: int, out: Path):
    if not rolling:
        return
    names = list(rolling)
    fig, axes = new_fig(len(names), 4.2)
    colors = {mode: PALETTE[i] for i, mode in enumerate(REBALANCE_METHODS[1:])}
    for ax, pname in zip(axes, names):
        series = rolling[pname]
        for mode, s in series.items():
            ax.plot(s.index, s.values, lw=2, color=colors[mode], label=mode)
        ax.axhline(0, color=TEXT_SECONDARY, lw=1)
        style_axes(ax, f"{pname}: Sharpe ratio minus buy-and-hold, rolling {window_years}y (above 0 = rebalancing helped)",
                   "Sharpe diff")
        ax.margins(x=0.1)
        label_line_ends(ax, series, colors, lambda v: f"{v:+.2f}")
        ax.legend(frameon=False, loc="upper left", fontsize=9, labelcolor=TEXT_SECONDARY)
    fig.tight_layout()
    fig.savefig(out, dpi=130, facecolor=SURFACE)
    plt.close(fig)


# ---------------------------------------------------------------- メイン


def parse_portfolio(text: str):
    """'US70JP30=US:0.7,JP:0.3' 形式"""
    name, spec = text.split("=", 1)
    weights = {}
    for part in spec.split(","):
        k, v = part.split(":")
        weights[k.strip()] = float(v)
    return name.strip(), weights


def parse_asset(text: str):
    """'EM=EEM:USD' 形式"""
    name, spec = text.split("=", 1)
    ticker, ccy = spec.rsplit(":", 1)
    ccy = ccy.upper()
    if ccy not in ("USD", "JPY"):
        raise ValueError("通貨は USD か JPY を指定してください")
    return name.strip(), (ticker.strip(), ccy)


def fmt_table(df: pd.DataFrame) -> str:
    def f(v):
        if isinstance(v, (float, np.floating)):
            return "-" if np.isnan(v) else f"{v:.1%}"
        return str(v)

    shown = df.copy()
    for c in shown.columns:
        if "年)" in c or "回数" in c:
            shown[c] = shown[c].map(lambda v: f"{v:.1f}")
        elif "シャープ" in c:
            shown[c] = shown[c].map(lambda v: f"{v:+.2f}" if "差" in c else f"{v:.2f}")
        elif "窓の数" in c:
            shown[c] = shown[c].map(lambda v: f"{int(v)}")
        else:
            shown[c] = shown[c].map(f)
    return shown.to_string()


def main(argv=None):
    ap = argparse.ArgumentParser(description="指数の長期検証（円建て）")
    ap.add_argument("--mode", choices=PRESETS, default="long", help="long: 1971年〜の価格指数 / etf: 2008年〜の配当込みETF")
    ap.add_argument("--start", help="開始日（例: 1990-01-01）。省略時はモードの既定値")
    ap.add_argument("--end", help="終了日（例: 2024-12-31）")
    ap.add_argument("--windows", type=int, nargs="+", default=[10, 15, 20], help="ローリング期間（年）")
    ap.add_argument("--asset", action="append", default=[], help="資産を追加 例: --asset EM=EEM:USD")
    ap.add_argument("--portfolio", action="append", default=[], help="配分を追加 例: --portfolio US70JP30=US:0.7,JP:0.3")
    ap.add_argument("--rebalance", choices=REBALANCE_METHODS, default="annual", help="配分の比較に使うリバランス方法")
    ap.add_argument("--band", type=float, default=0.05, help="band 方式のしきい値（0.05 = 目標から5%ポイントずれたら戻す）")
    ap.add_argument("--cost", type=float, default=0.0, help="売買コスト率（0.001 = 売買額の0.1%）")
    ap.add_argument("--rebalance-window", type=int, default=10, help="リバランス比較のローリング期間（年）")
    ap.add_argument("--no-hedge", action="store_true", help="為替ヘッジ比較を省略")
    ap.add_argument("--demo", action="store_true", help="ダミーデータで動作確認（ネット接続不要）")
    ap.add_argument("--out", default="output", help="出力フォルダ")
    args = ap.parse_args(argv)

    preset = PRESETS[args.mode]
    assets = dict(preset["assets"])
    for a in args.asset:
        k, v = parse_asset(a)
        assets[k] = v
    portfolios = dict(preset["portfolios"])
    for p in args.portfolio:
        k, v = parse_portfolio(p)
        portfolios[k] = v
    start = args.start or preset["start"]
    hedge = not args.no_hedge
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    print("データ取得")
    if args.demo:
        print("  ※ダミーデータです。結果は実際の市場とは無関係です。")
        prices, fx, rates = load_demo(assets, start)
    else:
        prices, fx, rates = load_real(assets, start)

    jpy, local, fx_ret = build_returns(prices, fx, rates, assets, hedge)
    sl = slice(start, args.end)
    jpy, local, fx_ret = jpy.loc[sl], local.loc[sl], fx_ret.loc[sl]

    for name, w in portfolios.items():
        missing = [k for k in w if k not in jpy]
        if missing:
            print(f"  配分 {name} は資産 {missing} が無いため省略")
            continue
        jpy[name] = simulate_portfolio(jpy, w, args.rebalance, args.band, args.cost)

    # 色はエンティティ名に固定で割り当て（グラフ間で同じ色）
    names = list(jpy.columns)
    ordered = [n for n in names if not n.endswith("_H")] + [n for n in names if n.endswith("_H")]
    colors = {n: PALETTE[i % len(PALETTE)] for i, n in enumerate(ordered)}

    if rates is not None:
        rf = (rates["JP"].reindex(jpy.index).ffill() / 100 / 12).fillna(0)
    else:
        rf = pd.Series(0.0, jpy.index)
    summary = summarize(jpy, rf, args.windows)
    fxdec = fx_decomposition(local, jpy, fx_ret, assets)
    summary.to_csv(out / "summary.csv", encoding="utf-8-sig")
    fxdec.to_csv(out / "fx_decomposition.csv", encoding="utf-8-sig")

    main_cols = [c for c in names if not c.endswith("_H")]
    plot_growth(jpy[main_cols], colors, out / "1_growth.png")
    for y in args.windows:
        if len(jpy) < y * 12 + 24:
            print(f"  データ期間が短いため {y}年ローリングのグラフは省略")
            continue
        plot_rolling(jpy[main_cols], y, colors, out / f"2_rolling_{y}y.png")
    plot_drawdown(jpy[[c for c in main_cols if c in assets]], colors, out / "3_drawdown.png")
    plot_fx(local, jpy, fx_ret, assets, out / "4_fx_decomposition.png")
    hedged = [c for c in names if c.endswith("_H")]
    if hedged:
        pairs = [c for h in hedged for c in (h[:-2], h)]
        plot_growth(jpy[pairs], colors, out / "5_hedged_vs_unhedged.png",
                    "FX-hedged (_H, approx.) vs. unhedged")

    valid = {k: v for k, v in portfolios.items() if all(c in jpy for c in v)}
    print("リバランスの比較を計算中…")
    rb_full, rb_wins, rb_rolling = compare_rebalance(jpy, rf, valid, args.rebalance_window, args.band, args.cost)
    rb_full.to_csv(out / "rebalance_full_period.csv", encoding="utf-8-sig")
    if not rb_wins.empty:
        rb_wins.to_csv(out / "rebalance_rolling.csv", encoding="utf-8-sig")
    plot_rebalance(rb_rolling, args.rebalance_window, out / "6_rebalance_sharpe.png")

    print()
    print(preset["note"])
    print(f"リバランス: {args.rebalance} / 名前の末尾 _H は為替ヘッジ付き（近似）")
    print()
    print("■ 円建ての成績（ローリングは『その期間どこで始めても』の分布）")
    print(fmt_table(summary))
    print()
    if not fxdec.empty:
        print("■ 為替の分解（円建て ≈ 現地通貨 × 為替）")
        corr = fxdec.pop("株と為替の相関")
        print(fmt_table(fxdec))
        print("  株と為替の相関:", ", ".join(f"{k} {v:+.2f}" for k, v in corr.items()),
              "（プラス＝株安のとき円高になりやすく、円建ての値動きが大きくなる）")
        print()
    print(f"■ リバランスの比較（無リスク金利＝円短期金利、band={args.band:.0%}, 売買コスト={args.cost:.2%}）")
    print("  全期間:")
    print(fmt_table(rb_full))
    if not rb_wins.empty:
        print()
        print(f"  {args.rebalance_window}年ローリング（毎月ずらした開始時点ごとに、買いっぱなしとシャープレシオを比較）:")
        print(fmt_table(rb_wins))
        print("  ※窓は互いに重なっているので独立な試行ではありません。勝率は『傾向』として読んでください。")
    print()
    print(f"グラフと CSV を {out.resolve()} に保存しました。")


if __name__ == "__main__":
    sys.exit(main())
