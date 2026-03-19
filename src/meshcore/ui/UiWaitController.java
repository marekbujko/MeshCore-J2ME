package meshcore.ui;

import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;

/**
 * Small helper to run "waiting dots" animation + timeout logic for Canvas screens.
 *
 * Uses a generation token so repeated restarts (e.g. Refresh) won't keep old threads updating UI.
 */
public final class UiWaitController {

    private UiWaitController() {}

    public interface WaitState {
        int getGeneration();
        boolean isResolved();
        boolean isWaiting();
        void onDot(int dotIndex);
        void onTimeout();
    }

    public static void startWaitingDots(final Display display,
                                          final Displayable screen,
                                          final WaitState state,
                                          final int periodMs,
                                          final int dotModulo) {
        if (display == null || screen == null || state == null) return;
        final int gen = state.getGeneration();

        new Thread(new Runnable() {
            public void run() {
                int i = 0;
                while (display != null) {
                    if (gen != state.getGeneration()) break;
                    if (state.isResolved()) break;
                    if (!state.isWaiting()) break;

                    final int d = (dotModulo > 0) ? (i % dotModulo) : 0;
                    display.callSerially(new Runnable() {
                        public void run() {
                            if (display == null) return;
                            if (gen != state.getGeneration()) return;
                            if (screen != display.getCurrent()) return;
                            if (state.isResolved()) return;
                            if (!state.isWaiting()) return;
                            state.onDot(d);
                        }
                    });

                    i++;
                    try { Thread.sleep(periodMs); } catch (InterruptedException e) { break; }
                }
            }
        }).start();
    }

    public static void startTimeoutWatcher(final Display display,
                                            final Displayable screen,
                                            final WaitState state,
                                            final long timeoutMs,
                                            final int pollMs) {
        if (display == null || screen == null || state == null) return;
        final int gen = state.getGeneration();
        final long start = System.currentTimeMillis();

        new Thread(new Runnable() {
            public void run() {
                while (display != null) {
                    if (gen != state.getGeneration()) break;
                    if (state.isResolved()) break;
                    if (!state.isWaiting()) break;
                    if (System.currentTimeMillis() - start > timeoutMs) break;
                    try { Thread.sleep(pollMs); } catch (InterruptedException e) { break; }
                }

                if (display == null) return;
                display.callSerially(new Runnable() {
                    public void run() {
                        if (display == null) return;
                        if (gen != state.getGeneration()) return;
                        if (screen != display.getCurrent()) return;
                        if (state.isResolved()) return;
                        if (!state.isWaiting()) return;
                        state.onTimeout();
                    }
                });
            }
        }).start();
    }
}

