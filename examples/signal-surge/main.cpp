#include <iostream>
#include <algorithm>
#include <vector>

using namespace std;

int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int n;
    if (!(cin >> n)) return 0;
    long long first;
    cin >> first;
    long long max_so_far = first;
    long long curr_max = first;
    for (int i = 1; i < n; i++) {
        long long x;
        cin >> x;
        curr_max = max(x, curr_max + x);
        max_so_far = max(max_so_far, curr_max);
    }
    cout << max_so_far << "\n";
    return 0;
}
