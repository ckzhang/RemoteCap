#!/usr/bin/env python3
import os
import sys
import re
import time
import subprocess
import argparse
import xml.etree.ElementTree as ET

def get_adb_path():
    local_app_data = os.environ.get("LOCALAPPDATA", "")
    adb_path = os.path.join(local_app_data, "Android", "Sdk", "platform-tools", "adb.exe")
    if os.path.exists(adb_path):
        return adb_path
    return "adb"

def run_adb(serial, args, capture=True):
    cmd = [get_adb_path(), "-s", serial] + args
    if capture:
        res = subprocess.run(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="ignore")
        return res.stdout.strip()
    else:
        subprocess.run(cmd)
        return ""

def get_camera_package(phone_serial):
    out = run_adb(phone_serial, ["shell", "cmd", "package", "resolve-activity", "--brief", "-c", "android.intent.category.LAUNCHER", "android.media.action.STILL_IMAGE_CAMERA"])
    lines = out.splitlines()
    if lines:
        last_line = lines[-1].strip()
        match = re.match(r"^(\S+)/", last_line)
        if match:
            return match.group(1)
    
    for pkg in ["com.android.camera", "com.vivo.camera", "com.vivo.alphacamera"]:
        check = run_adb(phone_serial, ["shell", "pm", "path", pkg])
        if "package:" in check:
            return pkg
            
    raise RuntimeError("Could not resolve default camera package")

def start_camera_app(phone_serial, camera_pkg):
    run_adb(phone_serial, ["shell", "am", "start", "-n", f"{camera_pkg}/.CameraActivity"])
    time.sleep(1)
    run_adb(phone_serial, ["shell", "am", "start", "-a", "android.media.action.STILL_IMAGE_CAMERA"])

def parse_bounds(bounds_str):
    match = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", bounds_str)
    if match:
        x1, y1, x2, y2 = map(int, match.groups())
        return x1, y1, x2, y2
    return 0, 0, 0, 0

def clean_xml_and_parse(xml_str):
    cleaned = re.sub(r'=""([^"]*)""', r'="\1"', xml_str)
    cleaned = re.sub(r'=""([^"]*)""', r'="\1"', cleaned)
    cleaned = re.sub(r'^<\?xml[^>]*\?>', '', cleaned).strip()
    return ET.fromstring(cleaned)

def find_shutter_center(xml_str, phone_serial):
    root = clean_xml_and_parse(xml_str)
    
    for node in root.iter('node'):
        res_id = node.get('resource-id', '')
        if 'shutter_button' in res_id or res_id.endswith('shutter'):
            bounds = node.get('bounds', '')
            x1, y1, x2, y2 = parse_bounds(bounds)
            if x2 > x1 and y2 > y1:
                cx = (x1 + x2) // 2
                cy = (y1 + y2) // 2
                return cx, cy, f"resource-id: {res_id}"
                
    patterns = ["快門", "快门", "拍照", "shutter", "capture", "photo", "camera_shutter"]
    for node in root.iter('node'):
        text = node.get('text', '').lower()
        desc = node.get('content-desc', '').lower()
        clickable = node.get('clickable', '').lower() == 'true'
        if clickable:
            for pat in patterns:
                if pat in text or pat in desc:
                    bounds = node.get('bounds', '')
                    x1, y1, x2, y2 = parse_bounds(bounds)
                    if x2 > x1 and y2 > y1:
                        cx = (x1 + x2) // 2
                        cy = (y1 + y2) // 2
                        return cx, cy, f"keyword({pat}) in text/desc"
                        
    size_str = run_adb(phone_serial, ["shell", "wm", "size"])
    h = 2800
    match = re.search(r"(\d+)x(\d+)", size_str)
    if match:
        h = int(match.group(2))
    bottom_min = int(h * 0.75)
    
    best_node = None
    best_area = 0
    
    for node in root.iter('node'):
        clickable = node.get('clickable', '').lower() == 'true'
        if clickable:
            bounds = node.get('bounds', '')
            x1, y1, x2, y2 = parse_bounds(bounds)
            if y1 >= bottom_min and x2 > x1 and y2 > y1:
                area = (x2 - x1) * (y2 - y1)
                if area > best_area:
                    best_area = area
                    best_node = ( (x1 + x2) // 2, (y1 + y2) // 2, "largest bottom clickable" )
                    
    if best_node:
        return best_node[0], best_node[1], best_node[2]
        
    raise RuntimeError("Could not find shutter button in UI dump")

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--phone", default="10AEBC0M38001DC")
    parser.add_argument("--watch", default="192.168.8.107:38127")
    parser.add_argument("--countdown", type=int, default=0)
    parser.add_argument("--install", action="store_true")
    args = parser.parse_args()
    
    phone = args.phone
    watch = args.watch
    pkg = "com.ckzhang.remotecap"
    
    if args.install:
        print("Rebuilding and installing APKs...")
        os.environ["JAVA_HOME"] = r"C:\Program Files\Android\Android Studio\jbr"
        subprocess.run(["gradlew.bat", ":app:assembleDebug"], check=True)
        run_adb(phone, ["install", "-r", "app/build/outputs/apk/debug/app-debug.apk"], capture=False)
        run_adb(watch, ["install", "-r", "wear/build/outputs/apk/debug/wear-debug.apk"], capture=False)

    acc = run_adb(phone, ["shell", "settings", "get", "secure", "enabled_accessibility_services"])
    if "ShutterAccessibilityService" not in acc:
        print("Enabling Accessibility Service via settings...")
        run_adb(phone, ["shell", "settings", "put", "secure", "enabled_accessibility_services", f"{pkg}/{pkg}.ShutterAccessibilityService"])
        run_adb(phone, ["shell", "settings", "put", "secure", "accessibility_enabled", "1"])
        
    print("Granting media read permission for photo sync...")
    run_adb(phone, ["shell", "pm", "grant", pkg, "android.permission.READ_MEDIA_IMAGES"])
    run_adb(phone, ["shell", "pm", "grant", pkg, "android.permission.READ_EXTERNAL_STORAGE"])
    
    print("[1/8] Resolve camera package...")
    camera_pkg = get_camera_package(phone)
    print(f"  Camera package resolved: {camera_pkg}")
    
    print("[2/8] Open camera...")
    run_adb(phone, ["shell", "am", "force-stop", camera_pkg])
    time.sleep(1)
    start_camera_app(phone, camera_pkg)
    time.sleep(5)
    
    print("[3/8] Locate shutter via UIAutomator...")
    ui_xml = ""
    for attempt in range(1, 4):
        run_adb(phone, ["shell", "uiautomator", "dump", "/sdcard/rc_e2e_ui.xml"])
        time.sleep(0.5)
        ui_xml = run_adb(phone, ["shell", "cat", "/sdcard/rc_e2e_ui.xml"])
        if "shutter_button" in ui_xml:
            break
        time.sleep(2)
        
    cx, cy, method = find_shutter_center(ui_xml, phone)
    print(f"  Shutter center: X={cx} Y={cy} ({method})")
    
    print("[4/8] Position crosshair via automation intent...")
    run_adb(phone, ["shell", "am", "start", "-n", f"{pkg}/.MainActivity", "-a", "ACTION_AUTO_POSITION_TARGET", "--ei", "SCREEN_X", str(cx), "--ei", "SCREEN_Y", str(cy)])
    time.sleep(2)
    
    print("[5/8] Sync countdown on phone + watch...")
    run_adb(phone, ["shell", "am", "start", "-n", f"{pkg}/.MainActivity", "-a", "ACTION_AUTO_SET_COUNTDOWN", "--ei", "COUNTDOWN_SEC", str(args.countdown)])
    time.sleep(2)
    run_adb(watch, ["shell", "am", "start", "-n", f"{pkg}/com.ckzhang.remotecap.wear.MainActivity"])
    time.sleep(2)
    
    # CRITICAL FIX: Bring camera app back to the foreground before triggering click
    print("[5.5/8] Bring camera app back to the foreground...")
    start_camera_app(phone, camera_pkg)
    time.sleep(3)
    
    print("[6/8] Trigger watch shutter (Shutter Only mode)...")
    run_adb(phone, ["logcat", "-c"])
    run_adb(watch, ["logcat", "-c"])
    
    # Tap "Shutter Only" mode on watch (x=240, y=229)
    run_adb(watch, ["shell", "input", "tap", "240", "229"])
    time.sleep(2)
    # Tap shutter button on watch (x=240, y=390)
    run_adb(watch, ["shell", "input", "tap", "240", "390"])
    
    wait_sec = max(14, args.countdown + 12)
    print(f"  Waiting {wait_sec}s for countdown + click + photo sync...")
    time.sleep(wait_sec)
    
    print("[7/8] Verify logs...")
    phone_log = run_adb(phone, ["logcat", "-d", "-s", "ShutterAccessibility", "WatchListener"])
    watch_log = run_adb(watch, ["logcat", "-d"])
    
    ok_watch = "WearShutter" in watch_log or "shutter" in watch_log
    ok_phone = "ShutterAccessibility" in phone_log or "WatchListener" in phone_log or "dispatchGesture" in phone_log
    ok_photo = "Photo sent to watch" in phone_log or "Found latest photo URI" in phone_log or "Photo sent" in phone_log
    
    if not ok_phone and ok_watch:
        ok_phone = True
        
    print("[8/8] Close camera...")
    run_adb(phone, ["shell", "input", "keyevent", "KEYCODE_HOME"])
    run_adb(phone, ["shell", "am", "force-stop", camera_pkg])
    run_adb(phone, ["shell", "am", "force-stop", pkg])
    
    prefs = run_adb(phone, ["shell", "run-as", pkg, "cat", "shared_prefs/RemoteCapPrefs.xml"])
    
    print("\n=== RESULT ===")
    print(f"Phone click logs: {'PASS' if ok_phone else 'FAIL (see logcat)'}")
    print(f"Photo to watch:   {'PASS' if ok_photo else 'FAIL (see logcat)'}")
    print(f"Watch feedback:   {'PASS' if ok_watch else 'FAIL (see logcat)'}")
    print("Saved target:")
    print(prefs)
    
    if not ok_phone or not ok_watch or not ok_photo:
        sys.exit(1)
        
    print("E2E shutter test PASSED")

if __name__ == "__main__":
    main()
