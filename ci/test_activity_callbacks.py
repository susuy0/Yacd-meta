"""Run LaunchActivity's actual leave-hint method against the AndroidX callback contract."""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(sys.argv[1])
source = (root / 'TMessagesProj/src/main/java/org/telegram/ui/LaunchActivity.java').read_text()
start = source.index('protected void onUserLeaveHint()')
opening = source.index('{', start)
depth = 1
end = opening + 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
method = source[start:end]
assert 'public void addOnUserLeaveHintListener' not in source
assert 'public void removeOnUserLeaveHintListener' not in source

base = '''
import java.util.*;
class ComponentActivity {
    private final List<Runnable> listeners = new ArrayList<>();
    public final void addOnUserLeaveHintListener(Runnable value) { listeners.add(value); }
    public final void removeOnUserLeaveHintListener(Runnable value) { listeners.remove(value); }
    protected void onUserLeaveHint() { for (Runnable value : listeners) value.run(); }
}
public class CallbackHarness extends ComponentActivity {
    private static String trace = "";
    private static class Target {
        final String name;
        Target(String name) { this.name = name; }
        void onUserLeaveHint() { trace += name + "|"; }
    }
    private Target pipActivityHandler = new Target("pip");
    private Target actionBarLayout = new Target("layout");
    private List<Runnable> onUserLeaveHintListeners = new ArrayList<>();
    ACTUAL_METHOD
    public static void main(String[] args) {
        CallbackHarness activity = new CallbackHarness();
        Runnable callback = () -> trace += "listener|";
        activity.addOnUserLeaveHintListener(callback);
        activity.onUserLeaveHint();
        if (!trace.equals("pip|listener|layout|")) throw new AssertionError(trace);
        trace = "";
        activity.removeOnUserLeaveHintListener(callback);
        activity.onUserLeaveHint();
        if (!trace.equals("pip|layout|")) throw new AssertionError(trace);
        trace = ""; activity.actionBarLayout = null;
        activity.onUserLeaveHint();
        if (!trace.equals("pip|")) throw new AssertionError(trace);
        System.out.println("PASS: inherited AndroidX registration, once-only leave dispatch, removal and null layout");
    }
    OLD_OVERRIDES
}
'''.replace('ACTUAL_METHOD', method)

with tempfile.TemporaryDirectory() as work:
    path = Path(work) / 'CallbackHarness.java'
    path.write_text(base.replace('OLD_OVERRIDES', ''))
    subprocess.run(['javac', str(path)], check=True)
    subprocess.run(['java', '-cp', work, 'CallbackHarness'], check=True)
    path.write_text(base.replace('OLD_OVERRIDES', '''
        public void addOnUserLeaveHintListener(Runnable value) { onUserLeaveHintListeners.add(value); }
        public void removeOnUserLeaveHintListener(Runnable value) { onUserLeaveHintListeners.remove(value); }
    '''))
    result = subprocess.run(['javac', str(path)], capture_output=True, text=True)
    assert result.returncode != 0 and 'overridden method is final' in result.stderr, result.stderr
    print('PASS: restoring Telegram callbacks reproduces the original AndroidX compile conflict')
