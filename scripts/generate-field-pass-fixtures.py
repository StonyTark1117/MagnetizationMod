#!/usr/bin/env python3
"""Extend the disposable stress pack with bounded field/fluid comparison cases."""
import argparse, json, subprocess, sys, zipfile
from pathlib import Path

p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);a=p.parse_args()
root=Path(__file__).resolve().parent
subprocess.run([sys.executable,str(root/'generate-performance-stress-pack.py'),'--output',str(a.output),'--grid-size','4'],check=True)
f=a.output/'data/magnetization_stress/function'
manifest=json.loads((a.output/'stress-manifest.json').read_text())
extra_cleanup=['forceload add 128 128 143 143','fill -5 239 -5 4 242 4 minecraft:air','fill 127 79 127 136 82 136 minecraft:air']
for name in manifest['scenarios']:
 path=f/(name+'.mcfunction');text=path.read_text().replace('kill @e[type=minecraft:item]\n','kill @e[type=minecraft:item]\n'+'\n'.join(extra_cleanup)+'\n');path.write_text(text)
common=(f/'empty_start.mcfunction').read_text().split('say MAG_STRESS_READY_')[0].splitlines()

def item(x,y,z,kind='dirt'):
 return f'summon minecraft:item {x} {y} {z} {{Tags:["mag_stress"],NoGravity:1b,Age:-32768s,PickupDelay:32767s,Item:{{id:"minecraft:{kind}",count:1}}}}'
def pool(y,x=-4,z=-4):
 return [f'fill {x-1} {y-1} {z-1} {x+8} {y+2} {z+8} minecraft:glass hollow',f'fill {x} {y} {z} {x+7} {y} {z+7} magnetization:magnetized_ferrofluid']
def cages(block,occupied):
 commands=[]
 for x in range(-14,15,4):
  for z in range(-14,15,4):
   commands += [f'fill {x-1} 81 {z-1} {x+1} 84 {z+1} minecraft:glass',f'setblock {x} 81 {z} minecraft:redstone_block',f'setblock {x} 83 {z} minecraft:air',f'setblock {x} 82 {z} magnetization:{block}']
   if occupied:commands.append(item(x+.5,82.3,z+.5))
 return commands
extra={
 'gallium_empty': cages('gallium',False)+['setblock 0 88 0 magnetization:permanent_magnet'],
 'gallium_occupied_no_field': cages('gallium',True)+['setblock 0 88 0 magnetization:permanent_magnet'],
 'gallium_no_sources': cages('gallium',True),
 'magfluid_separated':pool(80)+pool(240)+[item(.5,160,.5,'iron_ingot')],
 'magfluid_far_queries':pool(80,128,128)+['magperf player armor'],
 'mr_powered':cages('mr_fluid',False),
 'vertical_external':['fill -8 80 -8 7 80 7 create_new_age:magnetite_block',item(.5,180,.5,'iron_ingot')],
}
for name,commands in extra.items():
 (f/(name+'.mcfunction')).write_text('\n'.join(common+commands+['magperf reindex 36','say MAG_STRESS_READY_'+name])+'\n')
manifest['scenarios']+=list(extra);manifest['field_pass_extra_cells']=64
(a.output/'stress-manifest.json').write_text(json.dumps(manifest,indent=2)+'\n')
# All global ticks are confined to the disposable lab dimension.
tick=f/'tick.mcfunction';tick.write_text('\n'.join('execute in magnetization_stress:lab run '+s for s in tick.read_text().splitlines())+'\n')
dimension=a.output/'data/magnetization_stress/dimension/lab.json';dimension.parent.mkdir(parents=True,exist_ok=True)
dimension.write_text(json.dumps({'type':'minecraft:overworld','generator':{'type':'minecraft:flat','settings':{'biome':'minecraft:plains','features':False,'lakes':False,'layers':[{'block':'minecraft:bedrock','height':1}]}}})+'\n')
archive=a.output.with_suffix('.zip')
with zipfile.ZipFile(archive,'w',zipfile.ZIP_DEFLATED) as z:
 for path in a.output.rglob('*'):
  if path.is_file():z.write(path,path.relative_to(a.output))
print(archive)
