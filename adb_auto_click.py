#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
ADB 自动点击脚本（电脑控制手机的备选方案，手机无需安装 App）

前提：
  1. 电脑装好 adb 并加入 PATH（Android SDK platform-tools）
  2. 手机开启「开发者选项 → USB 调试」，用数据线连接并授权
  3. 验证：adb devices 能看到你的设备

用法示例：
  # 单点连点，间隔 1000ms±200ms，无限循环
  python3 adb_auto_click.py --points "540,1600" --interval 1000 --jitter 200

  # 三个点轮流点，间隔 800ms，执行 10 轮后停止
  python3 adb_auto_click.py --points "300,400 700,900 540,1600" --interval 800 --repeat 10

  # 先录制坐标（手机上手点一下，脚本读取 getevent 坐标），再连点
  python3 adb_auto_click.py --record --interval 1000

按 Ctrl+C 可随时停止。
"""

import argparse
import random
import subprocess
import sys
import time


def run(cmd):
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        return out.stdout.strip()
    except FileNotFoundError:
        print("[错误] 找不到 adb，请先安装 platform-tools 并加入 PATH")
        sys.exit(1)
    except subprocess.TimeoutExpired:
        return ""


def adb(serial, args):
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    return run(cmd + args)


def check_device(serial):
    out = adb(serial, ["devices"])
    lines = [l for l in out.splitlines() if "\tdevice" in l]
    if not lines:
        print("[错误] 没有检测到已授权的设备，请检查 USB 调试与授权弹窗")
        print(out)
        sys.exit(1)
    if not serial and len(lines) > 1:
        print("[提示] 检测到多台设备，建议用 --serial 指定：")
        for l in lines:
            print("   ", l)
    return lines[0].split("\t")[0]


def tap(serial, x, y, press_ms):
    # input swipe 起止同点 = 一次带时长按压，比 input tap 更可控
    adb(serial, ["shell", "input", "swipe", str(x), str(y), str(x), str(y), str(press_ms)])


def record(serial, count):
    """简易录制：监听 /dev/input 事件流，抓取手指按下的屏幕坐标。
    兼容大多数机型；若抓不到，请改用 App 内的录制功能或手动填坐标。"""
    print(f"[录制] 请在手机上依次点击 {count} 个位置（每次点击后稍等一下）...")
    print("[录制] 若本方式在你的机型上无效，请手动用 --points 传入坐标")
    proc = subprocess.Popen(
        ["adb"] + (["-s", serial] if serial else []) + ["shell", "getevent", "-l"],
        stdout=subprocess.PIPE, text=True, bufsize=1
    )
    points = []
    cur = {}
    try:
        for line in proc.stdout:
            line = line.strip()
            if "ABS_MT_POSITION_X" in line:
                cur["x"] = int(line.split()[-1], 16)
            elif "ABS_MT_POSITION_Y" in line:
                cur["y"] = int(line.split()[-1], 16)
            elif "SYN_REPORT" in line and "x" in cur and "y" in cur:
                p = (cur.pop("x"), cur.pop("y"))
                if not points or p != points[-1]:
                    points.append(p)
                    print(f"  已记录：{p}")
                    if len(points) >= count:
                        break
    except KeyboardInterrupt:
        pass
    finally:
        proc.terminate()
    if not points:
        print("[错误] 未能捕获坐标，请改用 --points 手动指定")
        sys.exit(1)
    return points


def main():
    ap = argparse.ArgumentParser(description="ADB 自动点击器（多点循环 / 随机间隔 / 限定次数）")
    ap.add_argument("--serial", default="", help="设备序列号，多设备时必填")
    ap.add_argument("--points", default="", help='点击坐标，如 "540,1600 300,400"（空格分隔多个点）')
    ap.add_argument("--interval", type=int, default=1000, help="点击间隔（毫秒），默认 1000")
    ap.add_argument("--jitter", type=int, default=200, help="随机浮动 ±毫秒（防检测），默认 200")
    ap.add_argument("--repeat", type=int, default=0, help="重复轮数，0 表示无限循环，默认 0")
    ap.add_argument("--press", type=int, default=60, help="单次按住时长（毫秒），默认 60")
    ap.add_argument("--delay", type=int, default=3, help="启动前倒计时秒数，默认 3")
    ap.add_argument("--record", action="store_true", help="录制模式：在手机上点击以记录坐标")
    ap.add_argument("--record-count", type=int, default=1, help="录制几个点，默认 1")
    args = ap.parse_args()

    serial = check_device(args.serial)

    if args.record:
        points = record(serial, args.record_count)
    elif args.points:
        points = []
        for item in args.points.split():
            if "," not in item:
                print(f"[错误] 坐标格式应为 x,y，收到：{item}")
                sys.exit(1)
            a, b = item.split(",")[:2]
            points.append((int(a), int(b)))
    else:
        raw = input("请输入点击坐标（格式 x,y，多个点用空格分隔）：").strip()
        points = []
        for item in raw.split():
            a, b = item.split(",")[:2]
            points.append((int(a), int(b)))

    print(f"[就绪] 设备 {serial} ｜ 点数 {len(points)} ｜ 间隔 {args.interval}±{args.jitter}ms "
          f"｜ 轮数 {'∞' if args.repeat == 0 else args.repeat} ｜ 坐标 {points}")
    for i in range(args.delay, 0, -1):
        print(f"  {i} 秒后开始，请切到目标界面（Ctrl+C 取消）", end="\r", flush=True)
        time.sleep(1)
    print("\n[开始] 运行中，按 Ctrl+C 停止")

    round_no = 0
    try:
        while args.repeat == 0 or round_no < args.repeat:
            for idx, (x, y) in enumerate(points):
                tap(serial, x, y, args.press)
                print(f"  第{round_no + 1}轮 第{idx + 1}点 -> ({x}, {y})", end="\r", flush=True)
                wait = max(30, args.interval + random.randint(-args.jitter, args.jitter))
                time.sleep(wait / 1000.0)
            round_no += 1
        print(f"\n[完成] 已执行 {round_no} 轮")
    except KeyboardInterrupt:
        print(f"\n[停止] 已执行 {round_no} 轮（未满一轮不计）")


if __name__ == "__main__":
    main()
