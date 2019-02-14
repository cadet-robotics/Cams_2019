import java.util.function.BiFunction;

public class StableRoommate {
    private static final int NONE = 0;
    private static final int ASKING = 1;
    private static final int ASKED_BY = 2;
    private static final int REJECTED = 3;

    public static int getMapping(int x, int y) {
        if (x == y) return -1;
        if (x > y) {
            int t = x;
            x = y;
            y = t;
        }
        // Average rows 1 and y - 1, then multiply by the number of rows between them inclusively
        //int beforeRow = (1 + (y - 1)) / 2 * (y - 1);
        int beforeRow = (y / 2) * (y - 1);
        // Adds column
        return beforeRow + x;
    }

    public static <T> int[] runProblem(T[] data, BiFunction<T, T, Double> cmp) {
        int len = data.length;
        int lenMinusOne = len - 1;
        int t; // Used to store len * row
        // Generates a score table
        // Size is a (len - 1) by (len - 1) "triangle"
        // Taken from getMapping(x, y)
        double[] scores = new double[(len / 2) * (len - 1)];
        {
            int x;
            int i = 0;
            for (int y = 1; y < len; y++) {
                for (x = 0; x < y; y++) {
                    scores[i++] = cmp.apply(data[x], data[y]);
                }
            }
        }
        // Generates a proposal ranking for every item
        // It's (len) by (len - 1)
        int[] ranking = new int[len * (len - 1)];
        {
            int tSwap; // Used as temporary variable during swap
            for (int i = 0; i < len; i++) {
                t = i * (len - 1);
                // Generate list
                int n = 0;
                for (int j = 0; j < len; j++) {
                    if (j == i) continue;
                    ranking[t + (n++)] = j;
                }
                // Sort
                boolean c = true;
                while (c) {
                    c = false;
                    for (int j = 0; j < lenMinusOne; j++) {
                        if (scores[ranking[t + j]] < scores[ranking[t + j + 1]]) {
                            c = true;
                            tSwap = ranking[t + j];
                            ranking[t + j] = ranking[t + j + 1];
                            ranking[t + j + 1] = tSwap;
                        }
                    }
                }
            }
        }
        // Starts proposing and rejecting
        // ret is filled with zeros, so we just say that the true value of ret[i] is ((ret[i] > 0) ? (ret[i] - 1) : ret[i])
        int[] ret = new int[len];
        boolean[] isMatched = new boolean[len];
        int checkNext = 0;
        // What position we should start asking at in our ranking list
        int[] currentAskPos = new int[lenMinusOne];
        // matches is initialized with 0s because java, so it's already filled with NONEs
        byte[] matches = new byte[len * lenMinusOne];
        // lowest means highest, as our lists are in descending order
        int[] lowestAsk = new int[len];
        for (int i = 0; i < len; i++) lowestAsk[i] = Integer.MAX_VALUE;
        {
            int i = 0;
            while (true) {
                // We efficiently check to see if we're done
                if (checkNext >= len) {
                    for (int j = 0; j < len; j++) {
                        if (ret[j] != -1) ret[j]--;
                    }
                    return ret;
                } else if (isMatched[checkNext]) {
                    checkNext++;
                    continue;
                }
                // We're already matched, pick the next one
                if (isMatched[i]) {
                    if ((++i) >= len) i = 0;
                    continue;
                }
                // I wish this was c code
                t = i * lenMinusOne;
                int pos = currentAskPos[i];
                switch (matches[t + pos]) {
                    case REJECTED:
                        if ((++pos) >= lenMinusOne) {
                            // No one wants us
                            ret[i] = -1;
                            // We have a girlfriend (future maintainers may prefer "boyfriend"/etc.) in Canada
                            isMatched[i] = true;
                        }
                        currentAskPos[i] = pos;
                        break;
                    case ASKED_BY:
                        // Let's match them up with us then
                        int v = ranking[t + pos];
                        isMatched[v] = true;
                        ret[v] = i;
                        // And now we're matched up
                        isMatched[i] = true;
                        ret[i] = v;
                        break;
                    case NONE:
                        v = ranking[t + pos];
                        if (v == 1)
                        matches[t + pos] = ASKING;
                    case ASKING:
                }
                if (currentAskPos[])
                matches[t + pos] = ASKING;
                while (true) {
                    if ()
                    break;
                }
            }
        }
    }
}