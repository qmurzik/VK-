package re.sova.qmods;

import android.os.Handler;
import android.os.Looper;

import androidx.recyclerview.widget.RecyclerView;

/**
 * Two crashes of VK's chat list found in a tester's log (BUG-0015 attachment), both "RecyclerView is computing a
 * layout or scrolling": a long press on a message while the list moves (MsgListVc.W1 turns the swipe-to-reply helper
 * on/off and removes its item decoration) and a dialog update arriving mid-layout (MsgListAdapter.R4 →
 * notifyDataSetChanged). RecyclerView forbids both during a layout pass, so patch 0016 catches the refusal and the
 * same call is repeated on the next main-loop turn, after the layout. A second refusal is recorded, not rethrown.
 */
public final class Qvk3ListFix {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private Qvk3ListFix() {
    }

    public static void notifyLater(RecyclerView.Adapter adapter) {
        if (adapter == null) return;
        MAIN.post(() -> {
            try {
                adapter.Sc();
            } catch (IllegalStateException e) {
                RuntimeFaults.record("qvk3.list.notify", e);
            }
        });
    }

    public static void swipeLater(xsna.ziy vc, boolean enable) {
        if (vc == null) return;
        MAIN.post(() -> {
            try {
                vc.W1(enable);
            } catch (IllegalStateException e) {
                RuntimeFaults.record("qvk3.list.swipe", e);
            }
        });
    }
}
