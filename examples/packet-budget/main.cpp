#include <iostream>
using namespace std;
int main() {
    ios::sync_with_stdio(false);
    cin.tie(nullptr);
    int n;
    cin >> n;
    long long total = 0;
    for (int i = 0; i < n; ++i) {
        long long bytes;
        cin >> bytes;
        total += bytes;
    }
    cout << total << '\n';
}
