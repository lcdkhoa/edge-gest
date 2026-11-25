#!/system/bin/sh
# Khởi động EdgeGest. Tham số được chuyển tiếp cho Main (vd: --debug).
DIR=/data/local/tmp/edgegest
LOG=$DIR/edgegest.log

PID=$(pidof edgegest)
if [ -n "$PID" ]; then
  kill $PID
  sleep 1
fi

if cmd role get-role-holders android.app.role.HOME | grep -q com.miui.home; then
  echo "HOME đang là MIUI launcher -> gesture gốc đã dùng được, không cần EdgeGest."
  exit 1
fi

: > $LOG
# Wrapper: khi tiến trình Java thoát/crash -> trả lại thanh 3 nút để không bị kẹt điều hướng.
setsid sh -c "
  CLASSPATH=$DIR/edgegest.dex app_process /system/bin --nice-name=edgegest com.lcd.edgegest.Main $* >>$LOG 2>&1
  cmd role get-role-holders android.app.role.HOME | grep -q com.miui.home || cmd settings put global force_fsg_nav_bar 0
  echo \"\$(date) stopped -> navbar restored\" >>$LOG
" </dev/null >/dev/null 2>&1 &

# Chỉ ẩn thanh 3 nút sau khi các dải cảm ứng đã được tạo thành công.
i=0
while [ $i -lt 40 ]; do
  if grep -q "reader started" $LOG 2>/dev/null; then
    cmd settings put global force_fsg_nav_bar 1
    echo "EdgeGest đang chạy (pid $(pidof edgegest))"
    exit 0
  fi
  if grep -q "fatal" $LOG 2>/dev/null; then break; fi
  sleep 0.2
  i=$((i+1))
done
echo "EdgeGest khởi động thất bại:"
cat $LOG
exit 1
