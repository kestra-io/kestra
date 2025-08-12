echo "start at $(date)"

for i in $(seq 1 33); do
  echo "i $i - $(date)"
  sleep 60
done

echo "end at $(date)"