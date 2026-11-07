import sys
values = list(map(int, sys.stdin.buffer.read().split()))
n = values[0]
print(sum(values[1:n + 1]))
