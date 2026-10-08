"""Prepare an isolated server using unmodified installed sources and required libraries."""
import argparse, io, shutil, tomllib, zipfile
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument('--mods', type=Path, required=True)
parser.add_argument('--base-only', action='store_true')
args = parser.parse_args()
root = Path(__file__).resolve().parents[1]
run = root / ('build/evolution-absent-run' if args.base_only else 'build/evolution-smoke-run')
out = run / 'mods'
out.mkdir(parents=True, exist_ok=True)
providers = {}
metadata = {}

def scan(archive, parent):
    for name in ('META-INF/neoforge.mods.toml', 'META-INF/mods.toml'):
        if name not in archive.namelist():
            continue
        data = tomllib.loads(archive.read(name).decode('utf-8-sig'))
        for mod in data.get('mods', []):
            providers.setdefault(mod['modId'], parent)
        metadata.setdefault(parent, []).append(data)
        break
    for member in archive.namelist():
        if member.startswith('META-INF/jarjar/') and member.endswith('.jar'):
            with zipfile.ZipFile(io.BytesIO(archive.read(member))) as nested:
                scan(nested, parent)

for jar in args.mods.glob('*.jar'):
    with zipfile.ZipFile(jar) as archive:
        scan(archive, jar)
# Prefer an explicitly installed newer library to a source's older embedded copy.
for jar, docs in metadata.items():
    for mod in docs[0].get('mods', []):
        providers[mod['modId']] = jar
selected = set()
provided = {'minecraft', 'neoforge', 'cobblemon', 'kotlinforforge', 'entitybattle'}
def include(mod):
    if mod in provided:
        return
    jar = providers.get(mod)
    if jar is None:
        raise ValueError('Missing source dependency: ' + mod)
    if jar in selected:
        return
    selected.add(jar)
    for data in metadata[jar]:
        for dependencies in data.get('dependencies', {}).values():
            for dep in dependencies:
                if (dep.get('type') == 'required' or dep.get('mandatory')) and dep.get('side') != 'CLIENT':
                    include(dep['modId'])

if not args.base_only:
    for mod in ('twilightforest', 'aether', 'deep_aether', 'legendarymonuments'):
        include(mod)
for jar in selected:
    shutil.copy2(jar, out / jar.name)
(run / 'eula.txt').write_text('eula=true\n')
(run / 'server.properties').write_text('server-port=25582\nonline-mode=false\nview-distance=2\nsimulation-distance=2\nspawn-protection=0\n')
print('Prepared isolated evolution server:', sorted(p.name for p in selected))
