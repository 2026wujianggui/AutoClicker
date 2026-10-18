#!/data/data/com.termux/files/usr/bin/bash
# -*- coding: utf-8 -*-
# ============================================================
# 手机自己点自己 —— Termux 一键连点脚本（无需电脑）
# 原理：安卓 11+ 的「无线调试」+ 本机 adb 自己连自己
# 支持：多点循环 / 可调间隔 / 随机浮动防检测 / 限定轮数
# ============================================================

POINTS=""        # 点击坐标，如 "540,1600 300,400"
INTERVAL=1000    # 点击间隔（毫秒）
JITTER=200       # 随机浮动 ±毫秒（防检测）
REPEAT=0         # 重复轮数，0 = 无限循环
PRESS=60         # 单次按住时长（毫秒）
DELAY=3          # 启动倒计时（秒）
RECORD=0         # 1 = 录制坐标模式
RCOUNT=1         # 录制几个点

RED='\033[0;31m'; GRN='\033[0;32m'; YEL='\033[1;33m'; NC='\033[0m'
say()  { echo -e "${GRN}$*${NC}"; }
warn() { echo -e "${YEL}$*${NC}"; }
err()  { echo -e "${RED}$*${NC}"; }

usage() {
    cat <<EOF
用法：
  ./termux_auto_click.sh -p "540,1600" -i 1000 -j 200
  ./termux_auto_click.sh -p "300,400 700,900 540,1600" -i 800 -r 10
  ./termux_auto_click.sh --record -n 3        # 录制 3 个坐标后连点

参数：
  -p, --points   点击坐标（x,y，多个点用空格分隔，引号包起来）
  -i, --interval 点击间隔（毫秒，默认 1000）
  -j, --jitter   随机浮动 ±毫秒，防检测（默认 200）
  -r, --repeat   重复轮数，0 = 无限循环（默认 0）
  -s, --press    单次按住时长（毫秒，默认 60）
  -d, --delay    启动倒计时秒数（默认 3）
  -n, --count    录制点数（配合 --record，默认 1）
  -h, --help     显示本帮助

运行中按 Ctrl+C 停止。
EOF
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        -p|--points)   POINTS="$2"; shift 2;;
        -i|--interval) INTERVAL="$2"; shift 2;;
        -j|--jitter)   JITTER="$2"; shift 2;;
        -r|--repeat)   REPEAT="$2"; shift 2;;
        -s|--press)    PRESS="$2"; shift 2;;
        -d|--delay)    DELAY="$2"; shift 2;;
        -n|--count)    RCOUNT="$2"; shift 2;;
        --record)      RECORD=1; shift;;
        -h|--help)     usage; exit 0;;
        *) err "未知参数：$1"; usage; exit 1;;
    esac
done

# ---------- 环境检查 ----------
command -v adb >/dev/null 2>&1 || {
    err "没找到 adb，请先执行：pkg install android-tools"
    exit 1
}

DEVICE=$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')
if [[ -z "$DEVICE" ]]; then
    err "adb 尚未连接到本机。"
    warn "请先完成一次配对："
    echo "  1) 设置 → 开发者选项 → 无线调试 → 打开"
    echo "  2) 点「使用配对码配对」，记下 IP:端口 和 配对码"
    echo "  3) 回到 Termux 执行： adb pair <IP>:<配对端口>"
    echo "  4) 再执行： adb connect <无线调试页面显示的 IP:端口>"
    echo "  5) 出现授权弹窗点「允许」，然后重新运行本脚本"
    exit 1
fi
say "已连接设备：$DEVICE"

# ---------- 录制坐标 ----------
record_points() {
    warn "请在手机上依次点击目标位置（共 $RCOUNT 个）…"
    warn "若抓不到坐标，请改用开发者选项里的「指针位置」手动查看坐标，再用 -p 传入。"
    local pts=() x="" y=""
    while IFS= read -r line; do
        if [[ "$line" == *ABS_MT_POSITION_X* ]]; then
            x=$((16#${line##* }))
        elif [[ "$line" == *ABS_MT_POSITION_Y* ]]; then
            y=$((16#${line##* }))
        elif [[ "$line" == *SYN_REPORT* && -n "$x" && -n "$y" ]]; then
            pts+=("$x,$y")
            say "  已记录：($x, $y)"
            x=""; y=""
            [[ ${#pts[@]} -ge $RCOUNT ]] && break
        fi
    done < <(timeout 60 adb shell getevent -l 2>/dev/null)
    [[ ${#pts[@]} -eq 0 ]] && { err "未捕获坐标，请改用 -p 手动指定"; exit 1; }
    echo "${pts[@]}"
}

if [[ $RECORD -eq 1 ]]; then
    POINTS=$(record_points)
elif [[ -z "$POINTS" ]]; then
    read -r -p "请输入点击坐标（x,y，多个点用空格分隔）： " POINTS
    [[ -z "$POINTS" ]] && { err "坐标不能为空"; exit 1; }
fi

# ---------- 开跑 ----------
[[ -n "$JITTER" && "$JITTER" -lt 0 ]] && JITTER=0
termux-wake-lock 2>/dev/null && say "已获取 CPU 唤醒锁（防止息屏后被暂停）"

say "点数：$(echo $POINTS | wc -w) ｜ 间隔：${INTERVAL}±${JITTER}ms ｜ 轮数：$([ "$REPEAT" -eq 0 ] && echo ∞ || echo "$REPEAT")"
say "坐标：$POINTS"
for ((i=DELAY; i>0; i--)); do
    printf "\r  %d 秒后开始，请切到目标界面（Ctrl+C 取消）  " "$i"
    sleep 1
done
echo -e "\n${GRN}开始点击，按 Ctrl+C 停止${NC}"

round=0
cleanup() {
    echo ""
    warn "已停止，共执行 $round 轮"
    termux-wake-unlock 2>/dev/null
    exit 0
}
trap cleanup SIGINT SIGTERM

while [[ "$REPEAT" -eq 0 || $round -lt "$REPEAT" ]]; do
    idx=0
    for pt in $POINTS; do
        x=${pt%,*}; y=${pt#*,}
        adb shell input swipe "$x" "$y" "$x" "$y" "$PRESS" >/dev/null 2>&1
        printf "\r  第%d轮 第%d点 -> (%s, %s)   " "$((round+1))" "$((idx+1))" "$x" "$y"
        idx=$((idx+1))
        if [[ "$JITTER" -gt 0 ]]; then
            off=$(( RANDOM % (JITTER*2+1) - JITTER ))
        else
            off=0
        fi
        wait_ms=$(( INTERVAL + off ))
        [[ $wait_ms -lt 30 ]] && wait_ms=30
        sleep "$(echo "scale=3; $wait_ms/1000" | bc 2>/dev/null || echo 1)"
    done
    round=$((round+1))
done

warn "已按设定完成 $round 轮"
termux-wake-unlock 2>/dev/null
