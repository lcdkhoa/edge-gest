#!/system/bin/sh
# Giả lập một cú vuốt trên touchscreen (protocol B) để test EdgeGest.
# Dùng: sim.sh <x0> <y0> <x1> <y1> [steps] [sleep_ms]
D=/dev/input/event7
X0=$1; Y0=$2; X1=$3; Y1=$4
STEPS=${5:-5}
SLP=${6:-0}

sendevent $D 3 47 0        # ABS_MT_SLOT 0
sendevent $D 3 57 1234     # ABS_MT_TRACKING_ID
sendevent $D 3 53 $X0      # X
sendevent $D 3 54 $Y0      # Y
sendevent $D 1 330 1       # BTN_TOUCH down
sendevent $D 0 0 0         # SYN

i=1
while [ $i -le $STEPS ]; do
  X=$((X0 + (X1 - X0) * i / STEPS))
  Y=$((Y0 + (Y1 - Y0) * i / STEPS))
  sendevent $D 3 53 $X
  sendevent $D 3 54 $Y
  sendevent $D 0 0 0
  if [ "$SLP" != "0" ]; then sleep 0.0$SLP 2>/dev/null; fi
  i=$((i + 1))
done

sendevent $D 3 57 4294967295  # TRACKING_ID -1 (lift)
sendevent $D 1 330 0          # BTN_TOUCH up
sendevent $D 0 0 0            # SYN
