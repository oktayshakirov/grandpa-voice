import os, subprocess, time, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from ui import texts
STATES=["Тук съм за теб.","Свързвам се…","Слушам те…","Само момент…","Говоря…","Довиждане!","Връзката прекъсна.","Нещо се обърка","Сметката в OpenAI няма средства.","Приложението не е настроено правилно."]
t0=time.time(); last=None; shots=0
dur=float(sys.argv[1]) if len(sys.argv)>1 else 45
while time.time()-t0<dur:
    tx=texts(); s=next((x for x in tx if any(x.startswith(k) for k in STATES)), "?")
    if s!=last:
        print(f"{time.time()-t0:5.1f}s  {s}", flush=True); last=s
        name=f"state-{shots:02d}.png"; shots+=1
        subprocess.run(f"adb exec-out screencap -p > {name}",shell=True)
