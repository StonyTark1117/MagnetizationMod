#!/usr/bin/env python3
"""Verify Gradle cleanup only stops this checkout's GameTest launch arguments."""
import hashlib,json,pathlib,subprocess,sys
root=pathlib.Path(__file__).resolve().parents[1];out=root/'build/compat-audit/cleanup-scope';out.mkdir(parents=True,exist_ok=True);moddev=root/'build/moddev'
commands={
 'owned_inline':['-Dneoforge.gameTestServer=true','@'+str(moddev/'regressionGameTestServerRunProgramArgs.txt')],
 'owned_argfile':['@'+str(moddev/'regressionGameTestServerRunVmArgs.txt'),'@'+str(moddev/'regressionGameTestServerRunProgramArgs.txt')],
 'foreign_inline':['-Dneoforge.gameTestServer=true','@/tmp/magnetization-cleanup-foreign-control/build/moddev/gameTestServerRunProgramArgs.txt'],
 'owned_non_gametest':['@'+str(moddev/'aircraftAuditServerRunVmArgs.txt'),'@'+str(moddev/'aircraftAuditServerRunProgramArgs.txt')],
}
processes={name:subprocess.Popen([sys.executable,'-c','import time; time.sleep(300)',*args]) for name,args in commands.items()}
try:
 with (out/'cleanup-scope-runner.log').open('w') as log:
  gate=subprocess.run(['scripts/run-gametest-gate.sh','runRegressionGameTestServer','run-regression-gametest','180'],stdout=log,stderr=subprocess.STDOUT,cwd=root)
 state={name:process.poll() for name,process in processes.items()}
 assert gate.returncode==0,gate.returncode
 assert state['owned_inline'] is not None and state['owned_argfile'] is not None,state
 assert state['foreign_inline'] is None and state['owned_non_gametest'] is None,state
 raw=(out/'cleanup-scope-runner.log').read_text()
 result={'status':'passed','scope':'Owned disposable Python processes exercise actual Gradle ProcessHandle cleanup with launch arguments; foreign-checkout and non-GameTest controls must remain alive. All probes are terminated by this harness afterward.','exit_statuses':state,'game_test_gate_exit':gate.returncode,'runner_sha256':hashlib.sha256((out/'cleanup-scope-runner.log').read_bytes()).hexdigest()}
 (out/'cleanup-scope-results.json').write_text(json.dumps(result,indent=2)+'\n');print(result)
finally:
 for process in processes.values():
  if process.poll() is None:process.terminate()
  process.wait()
