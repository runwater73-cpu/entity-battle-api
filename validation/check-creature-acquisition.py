"""Check that the documented routes match native data and cover ordinary no-catch species."""
import json
from pathlib import Path
root=Path(__file__).resolve().parents[1]
data=root/'src/main/resources/data/entitybattle'
def load(path):
    return json.loads(path.read_text(encoding='utf-8'))
profiles=[]
for path in (data/'battle_profiles').glob('*.json'):
    value=load(path)
    profiles.extend(value if isinstance(value,list) else [value])
routes=load(root/'validation/creature-acquisition.json')
byid={profile['entity']:profile for profile in profiles}
reachable={p['entity'] for p in profiles if p['catchable'] or p.get('boss')}
for route in routes:
    assert byid[route['source']]['species']==route['sourceSpecies'],route
    assert byid[route['target']]['species']==route['targetSpecies'],route
    assert not byid[route['target']].get('boss') and route['target']!='twilightforest:quest_ram',route
    assert route['source'] in reachable,route
    reachable.add(route['target'])
    path=data/'species_additions/acquisition'/(route['sourceSpecies'].split(':')[1]+'.json')
    addition=load(path)
    evolution=addition['evolutions'][0]
    assert addition['target']==route['sourceSpecies'] and evolution['result']==route['targetSpecies'],route
    assert evolution['requirements'][0]['minLevel']==route['level'],route
    assert evolution['requiredContext']=='#entitybattle:transformation_powders',route
    target=load(data/'species'/(route['targetSpecies'].split(':')[1]+'.json'))
    assert target['preEvolution']==route['sourceSpecies'],route
blocked={p['entity'] for p in profiles if not p['catchable'] and not p.get('boss')}
assert blocked <= {r['target'] for r in routes},blocked
print('ACQUISITION_DATA PASS:',len(profiles),'profiles;',len(routes),'routes; ordinary no-catch species covered; reachable sources; no Boss or quest bypass')
