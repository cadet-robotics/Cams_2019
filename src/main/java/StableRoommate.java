import java.util.ArrayList;
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

    // The number of objects we're matching
    private int len;
    // The number of objects in each ranking list (len - 1)
    private int lenMinusOne;
    // The last index in each object's ranking list (len - 2)
    private int lenMinusTwo;
    // Whether this problem is done
    private boolean isDone = false;
    // What this problem will return
    private int[] ret;
    // Whether an object has been matched yet
    private boolean[] isMatched;
    // The matrix used to store match and reject information
    private byte[] matches;
    // How every object ranks every other object
    private int[] ranking;
    // The current positions objects are at on their ranking lists
    private int[] posCache;
    // The highest/first position in every object's list that isn't rejected, identical to posCache until step two starts
    private int[] highest;
    // The number of objects that have been matched
    private int numMatched = 0;

    /**
     * For internal use
     * Creates a new StableRoommate object
     *
     * @param nObjects The number of objects we're matching
     * @param rankingIn All object's ranking lists, as a table
     */
    private StableRoommate(int nObjects, int[] rankingIn) {
        len = nObjects;
        lenMinusOne = len - 1;
        lenMinusTwo = len - 2;
        int tSize = len * lenMinusOne;
        ret = new int[nObjects];
        isMatched = new boolean[nObjects];
        matches = new byte[tSize];
        ranking = rankingIn;
        posCache = new int[nObjects];
        highest = new int[len];
    }

    /**
     * Do step one
     * Make sure everyone has successful proposals
     */
    private void stepOne() {
        for (int i = 0; i < len; i++) {
            propose(i);
            if (numMatched >= len) {
                isDone = true;
                return;
            }
        }
    }

    private void stepTwo() {
        // Set all ranking list positions to the end
        int lenMinusTwo = lenMinusOne - 1;
        for (int i = 0; i < len; i++) {
            if (isMatched[i]) continue;
            posCache[i] = lenMinusTwo;
            // Make sure we ignore rejections
            int iAddr = i * lenMinusOne + lenMinusTwo;
            while (matches[iAddr] == REJECTED) { // Trusting step one to have matched everything without possible matches
                posCache[i]--;
                iAddr--;
            }
        }
        // Start finding stable loops
        int i = 0;
        main:
        while (i < len) {
            if ()
        }
    }

    private ArrayList<Integer> findLoop(int start) {
        ArrayList<Integer> loop = new ArrayList<>();
        loop.add(start);
        int prevValue = start;
        while (true) {
            int prevAddr = prevValue * lenMinusOne + posCache[prevValue];
            int nextValue = ranking[prevAddr];
            loop.add(nextValue);
            if (nextValue == start) return loop;
            prevValue = nextValue;
        }
    }

    private void decPos(int i) {

    }

    /**
     * Propose as an object, working down that object's ranking list
     *
     * @param as The object we're proposing as
     * @return Whether this object has been matched yet
     */
    private void propose(int as) {
        if (isMatched[as]) return;
        int asRowOffset = as * lenMinusOne;
        int pos = posCache[as];
        int asAddr = asRowOffset + pos;
        int target = ranking[asAddr];
        if (isMatched[target]) matches[asAddr] = REJECTED;
        int targetRowOffset = target * lenMinusOne;
        int targetAddr = targetRowOffset;
        while (ranking[targetAddr] != as) targetAddr++;
        if (matches[targetAddr] == REJECTED) matches[asAddr] = REJECTED;
        while (true) {
            switch (matches[asAddr]) {
                case ASKED_BY: // We match perfectly
                    match(as, target); // No need to update pos
                    // We don't need a return statement, because switch statements are pretty fun
                case ASKING: // Why did you even call this method?
                    return; // By definition this must have been our first loop, so no need to update pos
                case NONE: // Request a match
                    matches[asAddr] = ASKING;
                    matches[targetAddr] = ASKED_BY;
                    highest[as] = posCache[as] = pos; // Store pos
                    // Reject everyone below us
                    int loopBound = targetRowOffset + lenMinusOne;
                    for (int rejectAddr = targetAddr + 1; rejectAddr < loopBound; rejectAddr++) {
                        if (matches[rejectAddr] == ASKED_BY) { // Someone needs to be rejected
                            matches[rejectAddr] = REJECTED;
                            propose(target); // They need to find someone new
                            break; // We can stop now, as they rejected every object below them
                        } else matches[rejectAddr] = REJECTED;
                    }
                    return;
                case REJECTED: // We've been rejected
                    if ((++pos) >= lenMinusOne) { // No one wants us
                        takeout(as);
                        return;
                    }
                    asAddr++;
                    // Loop again
            }
        }
    }

    /**
     * Takes an object out of the running
     * This object won't be matched with anything
     *
     * @param object The object to take out
     */
    private void takeout(int object) {
        isMatched[object] = true;
        ret[object] = -1;
        numMatched++;
    }

    /**
     * Matches up two objects
     * Don't use as a general form of takeout()
     *
     * @param object The first object
     * @param with The second object
     */
    private void match(int object, int with) {
        isMatched[object] = true;
        ret[object] = with + 1;
        isMatched[with] = true;
        ret[with] = object + 1;
        numMatched += 2;
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

        // Start to solve the problem
        StableRoommate problem = new StableRoommate(len, ranking);

        problem.stepOne();

        if (!problem.isDone) problem.stepTwo();

        // Start second step
        {
            ArrayList<Integer> stableLoop = new ArrayList<>(); // Stores "stable loops"
            rowOffset = i * lenMinusOne;
            int firstChoicePos = 0; // The position of this object's first choice
            while (true) {
                if (matches[lastChoicePos] != REJECTED) break;
                lastChoicePos++;
            }
            main:
            for (int i = 0; i < len; i++) {
                if (isMatched[i]) {
                    if ((++i) >= lenMinusOne) i = 0;
                    continue;
                }
                rowOffset = i * lenMinusOne;
                // Find initial last choice
                int lastChoicePos = rowOffset + lenMinusOne - 1; // Includes row offset
                while (true) {
                    if (matches[lastChoicePos] != REJECTED) break;
                    lastChoicePos--;
                }
                int v = ranking[lastChoicePos];
                stableLoop.add(v);
                // Add to loop
                int j = v;
                while (true) {
                    rowOffset = j * lenMinusOne;
                    int lastChoicePos = rowOffset + lenMinusOne - 1; // Includes row offset
                    while (true) {
                        if (matches[lastChoicePos] != REJECTED) break;
                        lastChoicePos--;
                    }
                }
            }
        }
    }
}