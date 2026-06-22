#!/system/bin/sh
# CamOverlay system-app module. The APK is mounted into /system/app by Magisk,
# so PackageManager scans it as a FLAG_SYSTEM app at boot and honours
# android:persistent="true" — the process is kept alive by the framework and is
# spared by the OEM background/force-stop/resource killers. This script only
# (re)grants the overlay app-op, which is not auto-granted for system apps.
LOG=/data/adb/camoverlay-systemapp.log
echo "service.sh $(date)" >> $LOG
i=0
while [ $i -lt 60 ]; do
  pm path com.local.camoverlay >/dev/null 2>&1 && break
  sleep 2
  i=$((i+1))
done
appops set com.local.camoverlay SYSTEM_ALERT_WINDOW allow >> $LOG 2>&1
echo "appop rc=$?" >> $LOG
