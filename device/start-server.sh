#!/system/bin/sh
# Chạy helper injector ở CHẾ ĐỘ SERVER (dùng với APK overlay).
# Helper lắng nghe 127.0.0.1:47820, nhận lệnh cử chỉ từ APK rồi inject phím.
DIR=/data/local/tmp/edgegest
LOG=$DIR/edgegest.log

PID=$(pidof edgegest)
if [ -n "$PID" ]; then
  kill $PID
  sleep 1
fi

setsid sh -c "
  CLASSPATH=$DIR/edgegest.dex app_process /system/bin --nice-name=edgegest com.lcd.edgegest.Main --server >>$LOG 2>&1
  cmd role get-role-holders android.app.role.HOME | grep -q com.miui.home || cmd settings put global force_fsg_nav_bar 0
  echo \"\$(date) server stopped -> navbar restored\" >>$LOG
" </dev/null >/dev/null 2>&1 &

i=0
while [ $i -lt 40 ]; do
  if grep -q "command server started" $LOG 2>/dev/null; then
    cmd settings put global force_fsg_nav_bar 1
    echo "Helper server đang chạy (pid $(pidof edgegest)) trên 127.0.0.1:47820"
    exit 0
  fi
  if grep -q "fatal" $LOG 2>/dev/null; then break; fi
  sleep 0.2
  i=$((i+1))
done
echo "Helper server khởi động thất bại:"
tail -5 $LOG
exit 1
