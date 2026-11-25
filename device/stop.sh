#!/system/bin/sh
# Dừng EdgeGest; wrapper trong start.sh sẽ tự trả lại thanh 3 nút.
PID=$(pidof edgegest)
if [ -n "$PID" ]; then
  kill $PID
  sleep 1
  echo "Đã dừng EdgeGest"
else
  echo "EdgeGest không chạy"
fi
cmd role get-role-holders android.app.role.HOME | grep -q com.miui.home || cmd settings put global force_fsg_nav_bar 0
