import java.io.BufferedInputStream;
public class Main {
    static BufferedInputStream in = new BufferedInputStream(System.in);
    static long nextLong() throws Exception {
        int c;
        do { c = in.read(); } while (c <= 32 && c != -1);
        long x = 0;
        while (c > 32 && c != -1) {
            x = x * 10 + c - '0';
            c = in.read();
        }
        return x;
    }
    public static void main(String[] args) throws Exception {
        int n = (int) nextLong();
        long total = 0;
        for (int i = 0; i < n; i++) total += nextLong();
        System.out.println(total);
    }
}
