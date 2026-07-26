package io.github.ieshishinjin.splice;

/**
 * 简易终端进度条。
 */
public class ProgressBar {

    private final int total;
    private final String label;
    private int current;
    private long startTime;

    public ProgressBar(String label, int total) {
        this.label = label;
        this.total = total;
        this.current = 0;
        this.startTime = System.currentTimeMillis();
        render();
    }

    public synchronized void tick() {
        current++;
        if (current % Math.max(1, total / 40) == 0 || current == total) {
            render();
        }
    }

    public synchronized void done() {
        current = total;
        render();
        System.out.println();
    }

    private void render() {
        int barWidth = 30;
        int done = total > 0 ? (int) ((long) current * barWidth / total) : 0;
        int remain = barWidth - done;
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;

        StringBuilder sb = new StringBuilder("\r  [34m▐");  // ANSI blue
        sb.append("█".repeat(Math.max(0, done)));
        if (remain > 0) sb.append("[0m─".repeat(remain));
        sb.append("[0m ");
        sb.append(current).append("/").append(total);
        sb.append("  ").append(label);
        if (elapsed > 0) sb.append("  (").append(elapsed).append("s)");
        System.out.print(sb.toString());
    }
}
