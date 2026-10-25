import json, uuid, base64, io, random, math
import numpy as np
from PIL import Image, ImageDraw
random.seed(7)
OUT = "/home/user/Reya_3ddd/orbital_strider_model/"
T = 32
TILES = {"hull":(0,0),"head":(1,0),"leg":(2,0),"foot":(3,0),"cannon":(4,0),"glow":(5,0),"eye":(6,0)}
base = {"hull":(46,50,64),"head":(36,40,54),"leg":(58,62,78),"foot":(30,32,42),"cannon":(24,26,34),"glow":(60,225,255),"eye":(255,255,255)}
img = Image.new("RGBA",(256,256),(0,0,0,255))
d = ImageDraw.Draw(img)
for n,(c,r) in TILES.items():
    x0,y0=c*T,r*T
    b=base[n]
    for y in range(T):
        for x in range(T):
            k=random.randint(-7,7)
            img.putpixel((x0+x,y0+y),tuple(max(0,min(255,v+k)) for v in b)+(255,))
    if n in("hull","head","leg","foot","cannon"):
        dark=tuple(int(v*.55) for v in b)+(255,); light=tuple(min(255,int(v*1.3)) for v in b)+(255,)
        d.rectangle([x0,y0,x0+T-1,y0+T-1],outline=dark)
        d.line([x0+1,y0+1,x0+T-2,y0+1],fill=light)
        d.line([x0,y0+T//2,x0+T-1,y0+T//2],fill=dark)
        d.line([x0+T//2,y0,x0+T//2,y0+T//2],fill=dark)
        for px,py in((3,3),(T-4,3),(3,T-4),(T-4,T-4)):
            d.point((x0+px,y0+py),fill=light)
        if n in("hull","head"):
            d.rectangle([x0+6,y0+20,x0+T-7,y0+22],fill=(60,225,255,255))
    if n=="glow":
        d.rectangle([x0+8,y0+8,x0+T-9,y0+T-9],fill=(210,255,255,255))
buf=io.BytesIO(); img.save(buf,"PNG"); png=buf.getvalue()
open(OUT+"orbital_strider.png","wb").write(png)

def uid(): return str(uuid.uuid4())
def faces(tile):
    c,r=TILES[tile]; uv=[c*T,r*T,c*T+T,r*T+T]
    return {f:{"uv":uv,"texture":0} for f in("north","east","south","west","up","down")}
cubes=[]
def cube(name,frm,to,tile,origin=(0,0,0),rot=(0,0,0)):
    u=uid()
    cubes.append({"name":name,"box_uv":False,"rescale":False,"locked":False,"render_order":"default",
      "allow_mirror_modeling":True,"from":[round(float(v),3) for v in frm],"to":[round(float(v),3) for v in to],"autouv":0,"color":0,
      "origin":[round(float(v),3) for v in origin],"rotation":[round(float(v),3) for v in rot],"uuid":u,"faces":faces(tile),"type":"cube"})
    return u
def rot_xyz(rx,ry,rz):
    rx,ry,rz=map(math.radians,(rx,ry,rz))
    Rx=np.array([[1,0,0],[0,math.cos(rx),-math.sin(rx)],[0,math.sin(rx),math.cos(rx)]])
    Ry=np.array([[math.cos(ry),0,math.sin(ry)],[0,1,0],[-math.sin(ry),0,math.cos(ry)]])
    Rz=np.array([[math.cos(rz),-math.sin(rz),0],[math.sin(rz),math.cos(rz),0],[0,0,1]])
    return Rx@Ry@Rz
UP,LO=80,96
rotU=lambda sx,sz:(-sz*38,0,sx*38)
rotL=lambda sx,sz:(sz*10,0,-sx*10)
hx0,hz0=72,72
kneeOff=rot_xyz(*rotU(1,1))@np.array([0,-UP,0])
footOff=kneeOff+rot_xyz(*rotL(1,1))@np.array([0,-LO,0])
H=float(-footOff[1]+12)
legs=[]
for name,sx,sz in(("front_left",1,-1),("front_right",-1,-1),("back_left",1,1),("back_right",-1,1)):
    hip=np.array([sx*hx0,H,sz*hz0])
    u1=cube(name+"_upper",(hip[0]-14,hip[1]-UP,hip[2]-14),(hip[0]+14,hip[1],hip[2]+14),"leg",hip,rotU(sx,sz))
    knee=hip+rot_xyz(*rotU(sx,sz))@np.array([0,-UP,0])
    u2=cube(name+"_lower",(knee[0]-11,knee[1]-LO,knee[2]-11),(knee[0]+11,knee[1],knee[2]+11),"leg",knee,rotL(sx,sz))
    foot=knee+rot_xyz(*rotL(sx,sz))@np.array([0,-LO,0])
    u3=cube(name+"_foot",(foot[0]-26,0,foot[2]-26),(foot[0]+26,12,foot[2]+26),"foot")
    u4=cube(name+"_knee",(knee[0]-18,knee[1]-18,knee[2]-18),(knee[0]+18,knee[1]+18,knee[2]+18),"cannon")
    legs.append({"name":"leg_"+name,"origin":[float(v) for v in hip],"rotation":[0,0,0],"color":0,"uuid":uid(),"export":True,"isOpen":False,"children":[u1,u2,u4,u3]})
body=cube("body",(-96,H-24,-96),(96,H+72,96),"hull")
plate=cube("belly_plate",(-110,H-36,-110),(110,H-24,110),"foot")
seam=cube("seam",(-97,H+20,-97),(97,H+28,97),"glow")
hd=cube("head",(-48,H+72,-48),(48,H+112,48),"head")
e1=cube("eye_left",(18,H+84,-52),(40,H+98,-48),"eye")
e2=cube("eye_right",(-40,H+84,-52),(-18,H+98,-48),"eye")
corners=((1,1),(1,-1),(-1,1),(-1,-1))
sp=[cube("spire_%d"%i,(sx*60-8,H+72,sz*60-8),(sx*60+8,H+130,sz*60+8),"leg") for i,(sx,sz) in enumerate(corners)]
tips=[cube("spire_tip_%d"%i,(sx*60-5,H+130,sz*60-5),(sx*60+5,H+142,sz*60+5),"glow") for i,(sx,sz) in enumerate(corners)]
cn=cube("cannon_barrel",(-40,H-76,-40),(40,H-36,40),"cannon")
ring=cube("cannon_ring",(-48,H-60,-48),(48,H-52,48),"glow")
core=cube("cannon_core",(-26,H-80,-26),(26,H-76,26),"glow")
G=lambda n,o,ch,op=True:{"name":n,"origin":o,"rotation":[0,0,0],"color":0,"uuid":uid(),"export":True,"isOpen":op,"children":ch}
outl=[G("body",[0,H,0],[body,plate,seam]),G("head",[0,H+72,0],[hd,e1,e2]+sp+tips),G("belly_cannon",[0,H-36,0],[cn,ring,core])]+legs
model={"meta":{"format_version":"4.9","creation_time":1761400000,"model_format":"free","box_uv":False},
 "name":"orbital_strider","model_identifier":"","visible_box":[8,8,0],"variable_placeholders":"","variable_placeholder_buttons":[],
 "timeline_setups":[],"unhandled_root_fields":{},"resolution":{"width":256,"height":256},
 "elements":cubes,"outliner":outl,
 "textures":[{"path":"","name":"orbital_strider.png","folder":"","namespace":"","id":"0","width":256,"height":256,"uv_width":256,"uv_height":256,
   "particle":False,"use_as_default":False,"layers_enabled":False,"sync_to_project":"","render_mode":"default","render_sides":"auto",
   "frame_time":1,"frame_order_type":"loop","frame_order":"","frame_interpolate":False,"visible":True,"internal":True,"saved":True,
   "uuid":uid(),"relative_path":"orbital_strider.png","source":"data:image/png;base64,"+base64.b64encode(png).decode()}]}
json.dump(model,open(OUT+"orbital_strider.bbmodel","w"))
print("H",H,"cubes",len(cubes),"min y",min(c["from"][1] for c in cubes))
