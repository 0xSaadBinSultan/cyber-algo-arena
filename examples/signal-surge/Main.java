import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.StringTokenizer;

public class Main {
    public static void main(String[] args) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        String line = reader.readLine();
        while (line != null && line.isBlank()) {
            line = reader.readLine();
        }
        if (line == null) return;
        StringTokenizer st = new StringTokenizer(line);
        while (!st.hasMoreTokens()) {
            line = reader.readLine();
            if (line == null) return;
            st = new StringTokenizer(line);
        }
        int n = Integer.parseInt(st.nextToken());
        while (!st.hasMoreTokens()) {
            line = reader.readLine();
            if (line == null) return;
            st = new StringTokenizer(line);
        }
        long first = Long.parseLong(st.nextToken());
        long maxSoFar = first;
        long currMax = first;
        for (int i = 1; i < n; i++) {
            while (!st.hasMoreTokens()) {
                line = reader.readLine();
                if (line == null) break;
                st = new StringTokenizer(line);
            }
            long x = Long.parseLong(st.nextToken());
            currMax = Math.max(x, currMax + x);
            maxSoFar = Math.max(maxSoFar, currMax);
        }
        System.out.println(maxSoFar);
    }
}
