import sys

def solve():
    data = sys.stdin.read().split()
    if not data:
        return
    n = int(data[0])
    max_so_far = int(data[1])
    curr_max = max_so_far
    for i in range(2, n + 1):
        x = int(data[i])
        curr_max = max(x, curr_max + x)
        max_so_far = max(max_so_far, curr_max)
    print(max_so_far)

if __name__ == "__main__":
    solve()
