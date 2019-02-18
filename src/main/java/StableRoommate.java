import java.util.function.BiFunction;

public class StableRoommate {
    private static final byte NONE = 0;
    private static final byte ASKING = 1;
    private static final byte ASKED_BY = 2;
    private static final byte REJECTED = 3;

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

    // I wish this was c code
    public static <T> int[] runProblem(T[] data, BiFunction<T, T, Double> cmp) {
        if (data.length == 0) return new int[0];
        else if (data.length == 1) return new int[] {-1};
        else if (data.length == 2) return new int[] {1, 0};
        int len = data.length;
        int lenMinusOne = len - 1;
        int rowOffset; // Used to store len * row
        // Generates a score table
        double[] scores = new double[len * (len + 1) / 2];
        {
            int x;
            int i = 0;
            for (int y = 1; y < len; y++) {
                for (x = 0; x < y; x++) {
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
                rowOffset = i * lenMinusOne;
                // Generate list
                int n = 0;
                for (int j = 0; j < len; j++) {
                    if (j == i) continue;
                    ranking[rowOffset + (n++)] = j;
                }
                // Sort
                /*
                List<Double> scoreList = Arrays.asList(scores);
                Collections.sort(scoreList);
                scores = scoreList.stream().mapToDouble(Double::doubleValue).toArray();
                 */
                boolean c = true;
                while (c) {
                    c = false;
                    for (int j = 0; j < lenMinusOne; j++) {
                        if (scores[getMapping(i, ranking[rowOffset + j])] < scores[getMapping(i, ranking[rowOffset + j])]) {
                            c = true;
                            tSwap = ranking[rowOffset + j];
                            ranking[rowOffset + j] = ranking[rowOffset + j + 1];
                            ranking[rowOffset + j + 1] = tSwap;
                        }
                    }
                }
            }
        }

        // Starts proposing and rejecting - first step in algorithm
        // ret is filled with zeros, so we just say that the true value of ret[i] is ((ret[i] > 0) ? (ret[i] - 1) : ret[i])
        int[] ret = new int[len];
        boolean[] isMatched = new boolean[len];
        int checkNext = 0;

        // What position we should start asking at in our ranking list
        int[] currentAskPos = new int[len];

        // matches is initialized with 0s because java, so it's already filled with NONEs
        byte[] matches = new byte[len * lenMinusOne];
        for (int i = 0; i < len; i++) {
            rowOffset = i * lenMinusOne;
            int pos = currentAskPos[i];
            int currentTarget = ranking[rowOffset + pos];
            switch (matches[rowOffset + pos]) {
                case REJECTED:
                    if ((++pos) >= lenMinusOne) {
                        // No one wants us
                        ret[i] = -1;
                        // We have a girlfriend in Canada
                        // (future maintainers may prefer "boyfriend"/etc.)
                                isMatched[i] = true;
                            }
                            currentAskPos[i] = pos;
                            break;
                        case ASKED_BY:
                            // Let's match them up with us then
                            isMatched[currentTarget] = true;
                            ret[currentTarget] = i + 1;
                            // And now we're matched up
                            isMatched[i] = true;
                            ret[i] = currentTarget + 1;
                            break;
                        case NONE:
                            // Do they like someone else better who also wants them?
                            int theirRowOffset = currentTarget * lenMinusOne;
                            boolean ok = true;
                            int ourPosInTheirList = 0;
                            for (; ourPosInTheirList < lenMinusOne; ourPosInTheirList++) {
                                if (ranking[theirRowOffset + ourPosInTheirList] == i) break;
                                else if (matches[theirRowOffset + ourPosInTheirList] == ASKED_BY) {
                                    ok = false;
                                    break;
                                }
                            }
                            if (ok) {
                                matches[rowOffset + pos] = ASKING;
                                matches[theirRowOffset + ourPosInTheirList] = ASKED_BY;
                                // Did you hear that other objects? NO ONE WANTS YOU
                                // HAHAHAHAHAHAHA
                                // *rejected next round*
                                for (int j = ourPosInTheirList + 1; j < lenMinusOne; j++) {
                                    if (matches[theirRowOffset + j] == ASKED_BY) {
                                        matches[theirRowOffset + j] = REJECTED;
                                        // Tell the object asking them to go away
                                        int otherAskerOffset = ranking[theirRowOffset + j] * lenMinusOne;
                                        int askeePosInTheirList = 0;
                                        while (askeePosInTheirList < lenMinusOne) {
                                            if (ranking[otherAskerOffset + askeePosInTheirList] == currentTarget) {
                                                matches[otherAskerOffset + askeePosInTheirList] = REJECTED;
                                                break;
                                            }
                                            askeePosInTheirList++;
                                        }
                                    }
                                }
                            } else {
                                // We're rejected
                                matches[rowOffset + pos] = REJECTED;
                                // You can't reject me, I REJECT YOU
                                // HAHAHAHAHAHA
                                matches[theirRowOffset + ourPosInTheirList] = REJECTED;
                            }
                        case ASKING:
                    }
        }
    }
}