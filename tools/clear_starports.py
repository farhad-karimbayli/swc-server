import json,sys
sys.path.insert(0,"tools"); import gen_layouts as g
data={b["uid"]:b for b in g.load_buildings()}
live=json.load(open(sys.argv[1]))
XLO,XHI,ZLO,ZHI=-20,21,-21,21     # z=-21 is where the client itself let the user build
FIXED={"wall","shield_generator","HQ","champion_platform","starport"}
def size(u): return int(data[u]["sizex"]),int(data[u]["sizey"])
def typ(k): return data[pos[k][0]]["type"]
pos={k:[u,x,z] for k,u,x,z in live}
def inb(x,z): return XLO<=x<=XHI and ZLO<=z<=ZHI
def tiles(k):
    u,x,z=pos[k]; w,h=size(u)
    return [(cx,cz) for cx in range(x,x+w) for cz in range(z,z+h)]
def box(x,z,w,h,m=1):
    return [(cx,cz) for cx in range(x-m,x+w+m) for cz in range(z-m,z+h+m) if inb(cx,cz)]
def occ(skip=()):
    c={}
    for k in pos:
        if k not in skip:
            for t in tiles(k): c[t]=k
    return c
starports=[k for k in pos if typ(k)=="starport"]
def clear_rings(skip=()):
    r=set()
    for s in starports:
        if s in skip: continue
        u,x,z=pos[s]; r|=set(box(x,z,6,6))
    return r
moves={}
def place(k,reserved,ok=lambda x,z:True):
    u,x0,z0=pos[k]; w,h=size(u); best=None
    for x in range(XLO,XHI-w+2):
        for z in range(ZLO,ZHI-h+2):
            t=[(cx,cz) for cx in range(x,x+w) for cz in range(z,z+h)]
            if ok(x,z) and all(inb(*p) and p not in reserved for p in t):
                d=(x-x0)**2+(z-z0)**2
                if best is None or d<best[0]: best=(d,x,z)
    if best is None: raise SystemExit(f"no room for {k} {u}")
    pos[k][1:]=[best[1],best[2]]; moves[k]=(best[1],best[2])

# 1. The heavy droideka platform out of the starport, back inside the middle ring.
dist=lambda x,z: max(abs(x+2-0.5),abs(z+2-0.5))
c=occ(skip={"bld_429"}); place("bld_429", set(c)|clear_rings(), ok=lambda x,z: dist(x,z)<9)

# 2. Every starport gets a clear tile all round. A starport whose ring has a wall
#    or defence in it moves to the spot that evicts the fewest buffer buildings.
for s in starports:
    u,x,z=pos[s]; c=occ(skip={s})
    ring=[t for t in box(x,z,6,6) if t in c]
    if any(data[pos[c[t]][0]]["type"] in FIXED for t in ring):
        others=clear_rings(skip={s}); best=None
        for nx in range(XLO,XHI-4):
            for nz in range(ZLO,ZHI-4):
                b=box(nx,nz,6,6)
                if any(not inb(cx,cz) for cx in range(nx,nx+6) for cz in range(nz,nz+6)): continue
                hit={c[t] for t in b if t in c}
                if any(typ(k) in FIXED for k in hit) or any(t in others for t in b): continue
                cost=len(hit)*1000+(nx-x)**2+(nz-z)**2
                if best is None or cost<best[0]: best=(cost,nx,nz)
        pos[s][1:]=[best[1],best[2]]; moves[s]=(best[1],best[2])
    # whatever buffer building is now in the ring or under the starport goes elsewhere
    u,x,z=pos[s]; c=occ(skip={s})
    for k in sorted({c[t] for t in box(x,z,6,6) if t in c}, key=lambda k: -size(pos[k][0])[0]*size(pos[k][0])[1]):
        place(k, set(occ(skip={k}))|clear_rings())

print("moved:")
for k,v in moves.items(): print(" ",k,pos[k][0],v)
c={}; bad=[]
for k in pos:
    for t in tiles(k):
        if not inb(*t): bad.append(("out",k))
        if t in c: bad.append(("overlap",k,c[t]))
        c[t]=k
for s in starports:
    u,x,z=pos[s]; b=box(x,z,6,6); ring=[t for t in b if not(x<=t[0]<x+6 and z<=t[1]<z+6)]
    print(s,(x,z),"free around:",sum(t not in c for t in ring),"/",len(ring))
print("problems:",bad)
json.dump({"next":int(sys.argv[3]),"buildings":[{"key":k,"x":x,"z":z,"uid":u} for k,(u,x,z) in pos.items()]},open(sys.argv[2],"w"),indent=0)
