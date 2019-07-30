public class JavaIsCancerChangeMyMind {
	public static double moduloIsCancer(double n, double m) {
		n = n % m;
		if (n < 0) n = m + n;
		return n;
	}
}