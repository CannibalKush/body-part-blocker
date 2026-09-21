#!/usr/bin/env python3
"""Read-only check for the repeated ImageReader replacement failure. No screen pixels or app content."""
import argparse, subprocess, re, sys, time
p=argparse.ArgumentParser(); p.add_argument('--serial',required=True); p.add_argument('--adb',default='adb'); a=p.parse_args()
base=[a.adb,'-s',a.serial]
def adb(*args): return subprocess.check_output(base+list(args),text=True,timeout=15)
pid=adb('shell','pidof','dev.bodyblock.prototype').strip().split()[0]
projection=adb('shell','dumpsys','media_projection')
if 'packageName: dev.bodyblock.prototype' not in projection:
 print('INCONCLUSIVE: start BodyBlock single-app protection first.'); sys.exit(2)
def sample():
 lines=adb('logcat','-d','--pid='+pid,'-v','brief','-t','1000')
 return set(re.findall(r'ImageReader-(\d+x\d+)f\d+m\d+-'+pid+r'-(\d+)',lines))
first=sample(); time.sleep(3); second=sample(); new=second-first
print('Capture active; new abandoned ImageReader instances in 3 seconds:',len(new))
if new: print('Affected dimensions:',', '.join(sorted({size for size,_ in new})))
if len(new)>=5:
 print('FAIL: capture buffers are being replaced repeatedly; frames cannot stabilise.'); sys.exit(1)
print('PASS: no rapid capture-buffer replacement observed. This does not certify detection accuracy.')
