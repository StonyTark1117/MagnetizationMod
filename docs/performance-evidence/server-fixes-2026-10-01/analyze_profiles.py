# Recreate summaries from decoded Spark captures under build/reports.
import json,hashlib
from pathlib import Path
ROOT=Path(__file__).resolve().parents[3]
raw=ROOT/'build/reports/server-fixes-2026-10-01';dest=ROOT/'docs/performance-evidence/server-fixes-2026-10-01'
if not raw.exists():
 raise SystemExit('Raw local evidence is missing: '+str(raw))
prefix='com.stonytark.magnetization.'
allprofiles=[];allhotspots=[];manifest=[];sprints={}
for stage in ['baseline','candidate','baseline-updated','candidate-v2']:
 base=raw/stage
 if not base.exists():continue
 if (base/'summary.json').exists():sprints[stage]=json.loads((base/'summary.json').read_text())
 for path in sorted(base.glob('profile-*.spark.json')):
  name=path.name.removeprefix('profile-').removesuffix('.spark.json');j=json.loads(path.read_text())
  before=json.loads((base/('profile-'+name+'-before.json')).read_text());after=json.loads((base/('profile-'+name+'-after.json')).read_text())
  sha=hashlib.sha256(path.with_suffix('').read_bytes()).hexdigest();assert after['sha256']==sha
  meta=j['metadata'];ticks=int(meta['numberOfTicks']);assert abs(int(meta['startTime'])-before['started_ms'])<5000;assert int(meta['interval'])==4000
  assert ticks >= (1000 if name.startswith('bulk_') else 3500);assert int(meta['platformStatistics']['playerCount'])==0
  duration=int(meta['endTime'])-int(meta['startTime']);assert abs(duration-(120000 if name.startswith('bulk_') else 180000))<5000
  if name=='dense_external':
   assert '1024 block emitters' in before['stats'] and '1024 block emitters' in after['stats']
  if name.startswith(('shafts_','ships_')):
   count=int(name.split('_')[-1])
   for counts in [before['counts'],after['counts']]:
    assert counts['loaded_shafts']==2*count and counts['driven_shafts']==count
    assert counts['receiving_shafts']==count if name.startswith('shafts_') else counts['moving_ships']==count
  nodes=next(t for t in j['threads'] if t['name']=='Server thread')['children'];parents={r:i for i,n in enumerate(nodes) for r in n['childrenRefs']}
  def prod(n):
   c=n.get('className','');return c.startswith(prefix) and not any(c.startswith(prefix+'physics.'+s) for s in ['PerformanceFixture','PerformanceScalingFixture','PerformanceDiagnostics'])
  def named(n):return n.get('className','')+'.'+n.get('methodName','')
  def cost(n):return sum(n['times']) if n['times'] else n.get('time',0)
  def ancestor(i,pred):
   while i in parents:
    i=parents[i]
    if pred(nodes[i]):return True
   return False
  def roots(pred):return sorted([(named(n),cost(n)) for i,n in enumerate(nodes) if pred(n) and not ancestor(i,pred)],key=lambda x:(-x[1],x[0]))
  rt=roots(prod);wins=list(j['timeWindowStatistics'].values());window_ticks=sum(w['ticks'] for w in wins);assert abs(window_ticks-ticks)<=1;assert all(w['players']==0 for w in wins)
  entry={'stage':stage,'scenario':name,'measurement_kind':before.get('measurement_kind','steady state'),'url':after['url'],'server_file':after.get('server_file'),'ticks':ticks,'window_ticks':window_ticks,'tick_count_discrepancy':window_ticks-ticks,'duration_ms':duration,'sampler_engine':meta['samplerEngine'],'sampler_mode':meta['samplerMode'],'interval_us':meta['interval'],'production_mod_sampled_ms_per_tick':sum(v for _,v in rt)/ticks,'production_roots_sampled_ms':rt,'windows':wins,'final_minute_mspt':meta['platformStatistics']['mspt']['last1m'],'player_count':int(meta['platformStatistics']['playerCount']),'before_counts':before['counts'],'after_counts':after['counts']}
  allprofiles.append(entry)
  manifest.append({'stage':stage,'scenario':name,'sha256':sha,'bytes':path.with_suffix('').stat().st_size,'start_epoch_ms':int(meta['startTime']),'requested_start_epoch_ms':before['started_ms'],'ticks':ticks,'window_ticks':window_ticks,'tick_count_discrepancy':window_ticks-ticks,'url':after['url'],'server_file':after.get('server_file')})
  # Per-method totals exclude recursive/overloaded same-name ancestors; different methods still overlap.
  methods={named(n) for n in nodes if prod(n)}
  hot=sorted([{'method':method,'sampled_ms':sum(v for _,v in roots(lambda n:named(n)==method))} for method in methods],key=lambda x:(-x['sampled_ms'],x['method']))
  allhotspots.append({'stage':stage,'scenario':name,'ticks':ticks,'methods':hot})
  print(stage,name,'mod',round(entry['production_mod_sampled_ms_per_tick'],4),'medians',[round(w['msptMedian'],3) for w in wins],'worst',round(max(w['msptMax'] for w in wins),2))
for name,data in [('profiles',allprofiles),('hotspots',allhotspots),('profile-manifest',manifest),('sprint-summary',sprints)]:
 (dest/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n')
comparisons=[]
candidate_stage='candidate-v2' if 'candidate-v2' in sprints else 'candidate'
if candidate_stage in sprints:
 baseline_stage='baseline-updated' if candidate_stage=='candidate-v2' else 'baseline'
 for name,b in sprints[baseline_stage].items():
  if name not in sprints[candidate_stage]:continue
  c=sprints[candidate_stage][name];comparisons.append({'scenario':name,'baseline_stage':baseline_stage,'candidate_stage':candidate_stage,'baseline_median_mspt':b['median_mspt'],'candidate_median_mspt':c['median_mspt'],'change_percent':(c['median_mspt']/b['median_mspt']-1)*100,'baseline_samples_mspt':b['samples_mspt'],'candidate_samples_mspt':c['samples_mspt']})
 (dest/'comparison.json').write_text(json.dumps(comparisons,indent=2)+'\n')
 for c in comparisons:print(c['scenario'],c['baseline_median_mspt'],'->',c['candidate_median_mspt'],round(c['change_percent'],1),'%')
