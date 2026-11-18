import re, subprocess, sys, time
def dump():
    subprocess.run(["adb","shell","uiautomator","dump","/sdcard/u.xml"],capture_output=True)
    return subprocess.run(["adb","shell","cat","/sdcard/u.xml"],capture_output=True,text=True).stdout
def tap_text(t, contains=False):
    x=dump()
    for m in re.finditer(r'<node [^>]*>', x):
        n=m.group(0); tm=re.search(r'text="([^"]*)"',n)
        if tm and ((t in tm.group(1)) if contains else tm.group(1)==t):
            a,b,c,d=map(int,re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"',n).groups())
            subprocess.run(["adb","shell","input","tap",str((a+c)//2),str((b+d)//2)]); return True
    return False
def texts():
    return [t for t in re.findall(r'text="([^"]+)"',dump())]
if __name__=="__main__":
    cmd=sys.argv[1]
    if cmd=="tap": print(tap_text(sys.argv[2], len(sys.argv)>3))
    elif cmd=="texts": print("\n".join(texts()))
