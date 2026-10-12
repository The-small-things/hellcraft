"""sortimports.py <java files>: sorts each import block (others A-Z, then java.*), drops unused imports.
It eats the blank line after "package"; put it back with:  sed -i '1{n;/^$/!s/^/\n/}' <file>"""
import re,sys,glob
# Sorts each file's import block: project/library imports alphabetically, then java.* after a blank line; drops unused imports.
for p in sys.argv[1:]:
    s=open(p).read()
    m=re.search(r'\n((?:import [^\n]+;\n|\n)+)', s)
    if not m: continue
    block=m.group(1)
    imps=sorted(set(l for l in block.split('\n') if l.startswith('import ')))
    body=s[m.end():]
    keep=[]
    for i in imps:
        name=i[len('import '):-1].split('.')[-1]
        if i.startswith('import static') or name=='*' or re.search(r'\b%s\b'%re.escape(name), body):
            keep.append(i)
    other=[i for i in keep if not i.startswith('import java')]
    java=[i for i in keep if i.startswith('import java')]
    new='\n'.join(other)+'\n'+('\n'+'\n'.join(java)+'\n' if java else '')+'\n'
    s=s[:m.start()]+'\n'+new+body.lstrip('\n')
    open(p,'w').write(s)
