"""Reader and writer for the client's JOE format, the binary form of a patch
JSON (trp.json, base.json, ...) that sits as a TextAsset inside a patch bundle.

Layout, all counts as big-endian 7-bit varints (high bit = more bytes) unless
noted:
  "JOE\0", uint32 string count, then each string as varint length + bytes;
  uint32 count + int32 table; uint32 count + float32 table;
  uint32 count + arrays, each a varint count of varint refs;
  varint sheet count + sheet name refs;
  per sheet: varint column count, columns as (type byte, name ref),
  varint cell count (rows x columns), then every cell as a varint, row by row.
A string cell is its string index + 1, 0 is empty. A bool cell is 1 false,
2 true. write(parse(raw), raw[end:]) gives back the input byte for byte.
"""
import struct
def vlq(raw,p):
    v=0
    while True:
        b=raw[p]; p+=1; v=(v<<7)|(b&0x7f)
        if b<0x80: return v,p
def parse(raw):
    assert raw[:4]==b"JOE\x00"
    p=4; n=struct.unpack("<I",raw[p:p+4])[0]; p+=4; S=[]
    for i in range(n):
        L,p=vlq(raw,p); S.append(raw[p:p+L]); p+=L
    ni=struct.unpack("<I",raw[p:p+4])[0]; ints=list(struct.unpack(f"<{ni}i",raw[p+4:p+4+4*ni])); p+=4+4*ni
    nf=struct.unpack("<I",raw[p:p+4])[0]; floats=list(struct.unpack(f"<{nf}f",raw[p+4:p+4+4*nf])); p+=4+4*nf
    na=struct.unpack("<I",raw[p:p+4])[0]; p+=4; arrays=[]
    for i in range(na):
        c,p=vlq(raw,p); a=[]
        for j in range(c):
            v,p=vlq(raw,p); a.append(v)
        arrays.append(a)
    nsh,p=vlq(raw,p); names=[]
    for i in range(nsh):
        v,p=vlq(raw,p); names.append(v)
    sheets=[]
    for si in range(nsh):
        nc,p=vlq(raw,p); cols=[]
        for i in range(nc):
            t=raw[p]; p+=1; v,p=vlq(raw,p); cols.append([t,v])
        cnt,p=vlq(raw,p); cells=[]
        for k in range(cnt):
            v,p=vlq(raw,p); cells.append(v)
        sheets.append({"name":names[si],"cols":cols,"cells":cells})
    return {"S":S,"ints":ints,"floats":floats,"arrays":arrays,"sheets":sheets,"end":p,"len":len(raw)}
def enc_vlq(v):
    out=[v&0x7f]; v>>=7
    while v: out.append(0x80|(v&0x7f)); v>>=7
    return bytes(reversed(out))
def write(d, tail=b""):
    o=bytearray(b"JOE\x00"); o+=struct.pack("<I",len(d["S"]))
    for s in d["S"]: o+=enc_vlq(len(s))+s
    o+=struct.pack("<I",len(d["ints"]))+struct.pack(f"<{len(d['ints'])}i",*d["ints"])
    o+=struct.pack("<I",len(d["floats"]))+struct.pack(f"<{len(d['floats'])}f",*d["floats"])
    o+=struct.pack("<I",len(d["arrays"]))
    for a in d["arrays"]:
        o+=enc_vlq(len(a))
        for v in a: o+=enc_vlq(v)
    o+=enc_vlq(len(d["sheets"]))
    for sh in d["sheets"]: o+=enc_vlq(sh["name"])
    for sh in d["sheets"]:
        o+=enc_vlq(len(sh["cols"]))
        for t,v in sh["cols"]: o+=bytes([t])+enc_vlq(v)
        o+=enc_vlq(len(sh["cells"]))
        for c in sh["cells"]: o+=enc_vlq(c)
    return bytes(o)+tail
