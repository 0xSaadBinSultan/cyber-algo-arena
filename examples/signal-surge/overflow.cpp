#include <iostream>
#include <algorithm>

using namespace std;

// Deliberately uses 32-bit signed int to demonstrate hidden-test overflow failure
int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int n;
    if (!(cin >> n)) return 0;
    int first;
    cin >> first;
    int max_so_far = first;
    int curr_max = first;
    for (int i = 1; i < n; i++) {
        int x;
        cin >> x;
        curr_max = max(x, curr_max + x);
        max_so_far = max(max_so_far, curr_max);
    }
    cout << max_so_far << "\n";
    return 0;
}
