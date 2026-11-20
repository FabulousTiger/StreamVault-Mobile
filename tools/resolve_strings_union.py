import re
import subprocess
import sys

root = sys.argv[1]
out = subprocess.run(
    ["git", "-c", "safe.directory=*", "-C", root, "diff", "--name-only", "--diff-filter=U"],
    capture_output=True, text=True, check=True).stdout.split()
done = 0
for rel in out:
    if not rel.endswith("strings.xml"):
        continue
    path = f"{root}/{rel}"
    text = open(path, encoding="utf-8").read()
    fixed = re.sub(r"^<<<<<<< [^\n]*\n|^=======\n|^>>>>>>> [^\n]*\n", "", text, flags=re.M)
    names = re.findall(r'<string name="([^"]+)"', fixed)
    dups = {n for n in names if names.count(n) > 1}
    if dups:
        print("DUP", rel, sorted(dups))
        continue
    open(path, "w", encoding="utf-8").write(fixed)
    done += 1
print("resolved", done)
