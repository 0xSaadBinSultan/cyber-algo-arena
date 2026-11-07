// Deliberately incorrect: samples pass, but a hidden sum exceeds a 32-bit int.
#include <iostream>
using namespace std;
int main() {
    int n, x, total = 0;
    cin >> n;
    while (n--) { cin >> x; total += x; }
    cout << total << '\n';
}
