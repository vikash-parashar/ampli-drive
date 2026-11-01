import os, re, sys
root = sys.argv[1]  # app/src/main/java
base = os.path.join(root, 'com/vikash/ampli/drive')
# Startup path: the process start, MainActivity, DriveRoot and the four Home layouts.
startup_dirs = ['', 'ui/theme', 'prefs', 'location', 'map', 'system', 'config', 'i18n', 'media', 'car', 'cameras', 'nav', 'bluetooth', 'fastag', 'tpms', 'obd', 'billing']
startup_ui = ['DriveRoot', 'HomeScreen', 'HomeAlerts', 'Components', 'ThemeDecor', 'ThemedControls', 'MapPane',
              'MapPlaceholder', 'ShellUi', 'MusicCard', 'MoreSheet', 'VoiceOverlay', 'VoiceAnswerCard', 'SavedPlacesUi',
              'WelcomeUi', 'CrashOverlayUi', 'SpeedVolumeCard', 'Disclosure']
decl = re.compile(r'^(\s*)(?:(?:@\w+(?:\([^)]*\))?\s+)*)(?:(?:public|private|internal|protected|data|sealed|enum|abstract|open|inner|value|annotation|fun|inline|companion)\s+)*(class|object|interface)\s+(\w+)')
topfun = re.compile(r'^(?:(?:@\w+(?:\([^)]*\))?\s*)*)(?:(?:public|private|internal|inline|suspend|operator|infix|const|lateinit|tailrec)\s+)*(fun|val|var)\s')
def classes(path, pkg):
    name = os.path.basename(path)[:-3]
    out = []
    stack = []  # (indent, name)
    hasTop = False
    composable = False
    for line in open(path, encoding='utf-8'):
        if line.startswith('@Composable') or '@Composable' in line: composable = True
        if topfun.match(line): hasTop = True
        m = decl.match(line)
        if not m: continue
        ind = len(m.group(1).expandtabs(4))
        if m.group(2) == 'object' and m.group(3) == 'companion': continue
        while stack and stack[-1][0] >= ind: stack.pop()
        full = '$'.join([s[1] for s in stack] + [m.group(3)])
        out.append(full)
        stack.append((ind, m.group(3)))
    if hasTop: out.append(name + 'Kt')
    if composable: out.append('ComposableSingletons$' + name + 'Kt')
    return [pkg + '/' + c for c in dict.fromkeys(out)]
hot, post = [], []
for dirpath, _, files in os.walk(base):
    rel = os.path.relpath(dirpath, base)
    rel = '' if rel == '.' else rel
    pkg = 'com/vikash/ampli/drive' + ('/' + rel if rel else '')
    for f in sorted(files):
        if not f.endswith('.kt'): continue
        cs = classes(os.path.join(dirpath, f), pkg)
        if rel in startup_dirs or (rel == 'ui' and f[:-3] in startup_ui):
            hot += cs
        else:
            post += cs
lines = ['# Baseline profile for Car Hub (hand-written, regenerate with: python3 scripts/gen_baseline_profile.py app/src/main/java > app/src/main/baseline-prof.txt).',
         '# ART compiles these ahead of time on install (profileinstaller applies it on sideloaded test APKs too),',
         '# so the cold start and Home run compiled instead of interpreted on slow head units.',
         '# H = hot, S = used during start-up, P = used after start-up. Rules for missing classes are ignored.',
         '# Start-up path: the app, MainActivity, DriveRoot, Home and what they touch.']
for c in sorted(set(hot)):
    lines.append('HSPL%s;->**(**)**' % c)
    lines.append('L%s;' % c)
lines.append('# Every other screen and helper: compiled for use after start-up.')
for c in sorted(set(post) - set(hot)):
    lines.append('HPL%s;->**(**)**' % c)
print('\n'.join(lines))
