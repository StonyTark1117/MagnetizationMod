#!/usr/bin/env python3
"""Two real clients on private Xvfb displays, connected to a disposable loopback server.

Requires Xvfb, Java 21 and the private Slugterra jar. Write 'flight' or 'hud' to
build/slugterra-audit/server/control.txt; create build/slugterra-audit/stop to exit.
No audit source or private jar is included in the published mod.
"""
import argparse
import json
import os
from pathlib import Path
import signal
import subprocess
import tempfile
import time

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--slugterra-jar', type=Path, required=True)
args = parser.parse_args()
jar = args.slugterra_jar.resolve()
if not jar.is_file():
    parser.error('slugterra jar does not exist')
root = Path(__file__).resolve().parents[1]
os.chdir(root)
run = root / 'build/slugterra-audit'
run.mkdir(parents=True, exist_ok=True)
server_directory = run / 'server'
if server_directory.is_symlink():
    print(f'Preserving previous runtime at {server_directory.resolve()}', flush=True)
    server_directory.unlink()
elif server_directory.exists():
    server_directory.rename(run / f'server-previous-{time.time_ns()}')
# Sable's synchronous sublevel save can stall an otherwise idle test server on
# the development filesystem. Keep this disposable world on Linux tmpfs.
server_directory.symlink_to(Path(tempfile.mkdtemp(prefix='magnetization-slugterra-live-', dir='/tmp')), target_is_directory=True)
for role in ('server', 'client', 'observer'):
    (run / role).mkdir(parents=True, exist_ok=True)
    previous = run / role / 'logs/latest.log'
    if previous.exists():
        previous.rename(previous.with_name(f'previous-{time.time_ns()}.log'))
(run / 'stop').unlink(missing_ok=True)
(run / 'server/eula.txt').write_text('eula=true\n')
(run / 'server/server.properties').write_text('''server-ip=127.0.0.1
server-port=25586
online-mode=false
enforce-secure-profile=false
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"biome":"minecraft:plains","features":false,"lakes":false}
view-distance=4
simulation-distance=4
max-tick-time=-1
spawn-protection=0
sync-chunk-writes=false
''')
for role in ('client', 'observer'):
    (run / role / 'options.txt').write_text('''version:3955
narrator:0
onboardAccessibility:false
tutorialStep:none
pauseOnLostFocus:false
renderDistance:4
simulationDistance:4
maxFps:30
guiScale:2
graphicsMode:0
particles:2
mipmapLevels:0
entityDistanceScaling:2.0
soundCategory_master:0.0
''')
processes = []

def launch(command, log, environment=None):
    with (run / log).open('w') as output:
        process = subprocess.Popen(command, stdout=output, stderr=subprocess.STDOUT,
                                   env=environment, start_new_session=True)
    processes.append(process)
    return process

def wait_for_log(path, marker, process, timeout=300):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        if path.exists() and marker in path.read_text(errors='replace'):
            return
        if process.poll() is not None:
            raise RuntimeError(f'Process exited {process.returncode}: {path}')
        time.sleep(1)
    raise TimeoutError(f'Missing {marker}: {path}')

def stop_owned_jvms():
    signature = str(root / 'build/moddev/slugterraAudit').encode()
    for proc in Path('/proc').iterdir():
        if not proc.name.isdigit():
            continue
        try:
            command = (proc / 'cmdline').read_bytes()
            if command.split(b'\0', 1)[0].endswith(b'/java') and signature in command:
                os.kill(int(proc.name), signal.SIGTERM)
        except (FileNotFoundError, ProcessLookupError, PermissionError):
            pass

flags = ['-PslugterraAudit=true', f'-PslugterraJar={jar}', '--max-workers=2']
try:
    server = launch(['./gradlew', 'runSlugterraAuditServer', *flags], 'server-launch.log')
    wait_for_log(run / 'server/logs/latest.log', 'Done (', server)
    displays = {}
    for role, task in (('client', 'runSlugterraAuditClient'), ('observer', 'runSlugterraAuditObserver')):
        display = next(i for i in range(183, 500) if not Path(f'/tmp/.X11-unix/X{i}').exists())
        launch(['Xvfb', f':{display}', '-screen', '0', '1280x720x24', '-nolisten', 'tcp'], f'{role}-xvfb.log')
        time.sleep(0.5)
        environment = os.environ.copy()
        environment.pop('WAYLAND_DISPLAY', None)
        environment.update(DISPLAY=f':{display}', LIBGL_ALWAYS_SOFTWARE='1', XDG_SESSION_TYPE='x11', ALSOFT_DRIVERS='null')
        client = launch(['./gradlew', task, *flags], f'{role}-launch.log', environment)
        displays[role] = f':{display}'
        wait_for_log(run / role / 'logs/latest.log', 'Connecting to 127.0.0.1', client)
        time.sleep(5)
    wait_for_log(run / 'server/logs/latest.log', 'AUDIT_READY', server)
    (run / 'processes.json').write_text(json.dumps({'pids': [p.pid for p in processes], 'displays': displays}, indent=2))
    print(f'Live audit ready: {displays}. Write flight or hud to {run}/server/control.txt', flush=True)
    while not (run / 'stop').exists():
        if any(p.poll() is not None for p in processes):
            raise RuntimeError('An audit process exited; inspect launch logs')
        if 'Encountered an unexpected exception' in (run / 'server/logs/latest.log').read_text(errors='replace'):
            raise RuntimeError('Audit server crashed; inspect latest.log')
        time.sleep(2)
finally:
    stop_owned_jvms()
    for process in reversed(processes):
        try:
            os.killpg(process.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    for process in processes:
        try:
            process.wait(timeout=10)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(process.pid, signal.SIGKILL)
            except ProcessLookupError:
                pass
