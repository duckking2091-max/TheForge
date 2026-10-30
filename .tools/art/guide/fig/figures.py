import json,math,os,sys
from PIL import Image,ImageDraw
sys.path.insert(0,'/tmp/claude-0/art/vill');sys.path.insert(0,'/tmp/claude-0/art/anvil')
V='/tmp/claude-0/vanilla/assets/minecraft/textures/'
P='/tmp/claude-0/build/proj/resource-pack/assets/blastforge/textures/item/'
# ---------------- icons from the real textures ----------------
def tex16(path):
    im=Image.open(path).convert('RGBA')
    if im.height>im.width:im=im.crop((0,0,im.width,im.width))            # animated strips: first frame
    if im.width==16:return im
    k=im.width//16;out=Image.new('RGBA',(16,16))
    for y in range(16):
        for x in range(16):
            px=[im.getpixel((x*k+a,y*k+b)) for a in range(k) for b in range(k)]
            op=[p for p in px if p[3]>40]
            if len(op)*2>=len(px) or op:
                if len(op)<len(px)/4:continue
                c=tuple(sum(p[i] for p in op)//len(op) for i in range(3));out.putpixel((x,y),(*c,255))
    return out
def block_icon(top,side,side2=None):
    """Small isometric cube like an inventory block icon (from real block faces)."""
    T=tex16(top);S=tex16(side);S2=tex16(side2 or side);im=Image.new('RGBA',(16,16))
    for y in range(16):
        for x in range(16):
            # top face (rhombus), left face, right face
            u=(x/16);v=(y/16)
            if y<8:
                a=(x-8)/8;b=(y-4)/4
                if abs(a)+abs(b)<=1.0:
                    tu=int(((a+b)+1)/2*15.99);tv=int(((b-a)+1)/2*15.99);im.putpixel((x,y),T.getpixel((max(0,min(15,tu)),max(0,min(15,tv)))))
            if y>=4:
                if x<8 and y>=4+x*0.5 and y<12+x*0.5:
                    tu=int(x/8*15.99);tv=int((y-4-x*0.5)/8*15.99);c=S.getpixel((tu,max(0,min(15,tv))));im.putpixel((x,y),(int(c[0]*.8),int(c[1]*.8),int(c[2]*.8),255))
                if x>=8 and y>=8-(x-8)*0.5 and y<16-(x-8)*0.5:
                    tu=int((x-8)/8*15.99);tv=int((y-8+(x-8)*0.5)/8*15.99);c=S2.getpixel((tu,max(0,min(15,tv))));im.putpixel((x,y),(int(c[0]*.6),int(c[1]*.6),int(c[2]*.6),255))
    return im
def anvil_icon():
    # A vanilla-looking anvil silhouette (inventory 3/4 view) shaded with the real anvil texture colours
    T=tex16(V+'block/anvil.png');im=Image.new('RGBA',(16,16))
    def c(x,y,k):p=T.getpixel((x%16,y%16));return (min(255,int(p[0]*k)),min(255,int(p[1]*k)),min(255,int(p[2]*k)),255)
    rows=[(2,0,15,1.45),(3,0,15,1.35),(4,1,14,1.0),(5,1,14,0.85),(6,3,12,0.7),   # head: top face, front, horn shadow
          (7,5,10,0.8),(8,5,10,0.75),(9,5,10,0.7),                                     # waist
          (10,3,12,1.2),(11,2,13,1.0),(12,2,13,0.9),(13,2,13,0.75)]                    # feet
    for y,a,b,k in rows:
        for x in range(a,b+1):im.putpixel((x,y),c(x,y,k))
    for y,a,b,k in rows:   # dark outline at the ends of each row
        im.putpixel((a,y),c(a,y,0.45));im.putpixel((b,y),c(b,y,0.45))
    for x in range(0,16):
        if im.getpixel((x,2))[3]:im.putpixel((x,2),c(x,2,1.6))
    return im
ICON={}
def icon(name):
    if name in ICON:return ICON[name]
    if name=='iron_block':im=block_icon(V+'block/iron_block.png',V+'block/iron_block.png')
    elif name=='anvil':im=anvil_icon()
    elif name=='forging_book':im=tex16(V+'item/enchanted_book.png')
    elif os.path.exists(P+name+'.png'):im=tex16(P+name+'.png')
    elif os.path.exists(V+'item/'+name+'.png'):im=tex16(V+'item/'+name+'.png')
    else:raise SystemExit('missing icon '+name)
    ICON[name]=im;return im
# ---------------- Minecraft-style GUI parts ----------------
PANEL=(198,198,198);SLOT=(139,139,139);DARK=(55,55,55);LIGHT=(255,255,255);EDGE=(0,0,0)
def panel(w,h):
    im=Image.new('RGBA',(w,h),(*PANEL,255));d=ImageDraw.Draw(im)
    d.rectangle((0,0,w-1,h-1),outline=EDGE);d.line((1,1,w-3,1),fill=LIGHT);d.line((1,1,1,h-3),fill=LIGHT)
    d.line((w-2,2,w-2,h-2),fill=(85,85,85));d.line((2,h-2,w-2,h-2),fill=(85,85,85))
    for (x,y) in((0,0),(w-1,0),(0,h-1),(w-1,h-1)):im.putpixel((x,y),(*PANEL,255))   # rounded corners, still opaque
    return im,d
def slot(im,x,y,item=None,big=False):
    s=18 if not big else 26;d=ImageDraw.Draw(im)
    d.rectangle((x,y,x+s-1,y+s-1),fill=SLOT);d.line((x,y,x+s-2,y),fill=DARK);d.line((x,y,x,y+s-2),fill=DARK)
    d.line((x+1,y+s-1,x+s-1,y+s-1),fill=LIGHT);d.line((x+s-1,y+1,x+s-1,y+s-1),fill=LIGHT)
    if item:im.alpha_composite(icon(item),(x+(s-16)//2,y+(s-16)//2))
def arrow(im,x,y,w=14,col=(139,139,139)):
    d=ImageDraw.Draw(im);d.rectangle((x,y+3,x+w-6,y+6),fill=col);d.polygon([(x+w-6,y),(x+w-1,y+4),(x+w-1,y+5),(x+w-6,y+9)],fill=col)
def plus(im,x,y,col=(139,139,139)):
    d=ImageDraw.Draw(im);d.rectangle((x,y+3,x+8,y+5),fill=col);d.rectangle((x+3,y,x+5,y+8),fill=col)
FIG={}
def save(name,im):
    assert im.width<=99,(name,im.width)
    im.save(f'out/{name}.png');FIG[name]=im.size
# ---------------- figure types ----------------
def crafting(name,grid,result,count=1):
    w,h=98,60;im,d=panel(w,h);x0,y0=4,4
    for r in range(3):
        for c in range(3):slot(im,x0+c*18,y0+r*18,grid[r][c] if r<len(grid) and c<len(grid[r]) else None)
    arrow(im,62,25);slot(im,78,22,result)
    if count>1:
        d.text((88,32),str(count),fill=(255,255,255))
    save(name,im)
def flow(name,steps,signs):
    """slots with + / -> between them, e.g. template + gear + ingot -> result"""
    n=len(steps);w=4+n*18+sum(14 if s=='>' else 11 for s in signs)+4
    im,d=panel(min(100,w),26);x=4
    for i,it in enumerate(steps):
        slot(im,x,4,it);x+=18
        if i<len(signs):
            if signs[i]=='>':arrow(im,x+1,8,12);x+=14
            else:plus(im,x+1,9);x+=11
    save(name,im)
def row(name,items,gap=2):
    w=9+len(items)*16+(len(items)-1)*gap
    im,d=panel(w,24);x=4
    for it in items:im.alpha_composite(icon(it),(x,4));x+=16+gap
    save(name,im)
def chain(name,items):
    """icons joined by little arrows (progression)"""
    w=7+len(items)*16+(len(items)-1)*3
    im,d=panel(w,24);x=4
    for i,it in enumerate(items):
        im.alpha_composite(icon(it),(x,4));x+=16
        if i<len(items)-1:
            for k in range(2):d.line((x+k,10+k,x+k,13-k),fill=(90,90,90))
            x+=3
    save(name,im)
def ritual(name):
    # The real Dark Ritual slots: (row,col) for book, wardenite ingot, enderite stick, dragon heart, echo shard, warden heart, bat wing, netherite ingot; centre = scythe
    pos=[(0,5),(1,6),(3,6),(4,5),(4,3),(3,2),(1,2),(0,3)]
    items=['fire_book','wardenite_ingot','enderite_stick','dragon_heart','echo_shard','warden_heart','bat_wing','netherite_ingot']
    cell=18;w,h=4+5*cell+4,4+5*cell+4
    im=Image.new('RGBA',(w,h),(34,24,44,255));d=ImageDraw.Draw(im)
    d.rectangle((0,0,w-1,h-1),outline=(14,8,20));d.rectangle((1,1,w-2,h-2),outline=(110,50,140))
    cx,cy=4+2*cell+9,4+2*cell+9
    for r,a in((40,0),(34,1)):
        for t in range(0,360,3):
            x=cx+r*math.cos(math.radians(t));y=cy+r*math.sin(math.radians(t))
            if 0<x<w and 0<y<h:im.putpixel((int(x),int(y)),(170,70,210,255) if a==0 else (90,40,120,255))
    for (r,c),it in zip(pos,items):slot(im,4+(c-2)*cell,4+r*cell,it)
    slot(im,4+2*cell,4+2*cell,'legendary_scythe')
    save(name,im)
def portrait(name,prof,parts,bg):
    import renderlib,preview
    os.chdir('/tmp/claude-0/art/vill')
    M=preview.scene(prof,parts)
    os.chdir('/tmp/claude-0/art/figs')
    img=renderlib.render(M,(-.7,-.3,.8),(0,1,0),70,90,1.55,center=(0,21,0))
    w,h=98,62;im=Image.new('RGBA',(w,h),(*bg,255));d=ImageDraw.Draw(im)
    for y in range(h):
        for x in range(w):
            if (x+y)%7==0:im.putpixel((x,y),tuple(int(v*.9) for v in bg)+(255,))
    bb=img.getbbox();img=img.crop(bb)
    if img.height>h-6:img=img.resize((img.width*(h-6)//img.height,h-6),Image.NEAREST)
    im.alpha_composite(img,((w-img.width)//2,h-3-img.height))
    d.rectangle((0,0,w-1,h-1),outline=(40,30,20));d.rectangle((1,1,w-2,h-2),outline=(200,170,110))
    save(name,im)
def anvil_render(name):
    import importlib;sys.path.insert(0,'/tmp/claude-0/art/anvil')
    os.chdir('/tmp/claude-0/art/anvil');import render as AR
    TF={'blastforge:item/rune_hearth_anvil':'rune_hearth_anvil.png','blastforge:item/rune_hearth_core':'rune_hearth_core.png'}
    a=AR.load('enchanting_anvil.json',TF);c=AR.load('anvil_core.json',TF)
    img=AR.render([(a[0],a[1],False),(c[0],c[1],True)],(1,-0.8,1),(0,1,0),140,110,2.6)
    os.chdir('/tmp/claude-0/art/figs')
    w,h=98,62;im=Image.new('RGBA',(w,h),(58,40,30,255));d=ImageDraw.Draw(im)
    for y in range(h):
        for x in range(w):
            k=max(0,1-math.hypot(x-49,y-40)/60);im.putpixel((x,y),(int(58+120*k*k),int(40+50*k*k),int(30+10*k*k),255))
    img=img.crop(img.getbbox());img=img.resize((img.width*(h-8)//img.height,h-8),Image.NEAREST) if img.height>h-8 else img
    im.alpha_composite(img,((w-img.width)//2,(h-img.height)//2))
    d.rectangle((0,0,w-1,h-1),outline=(20,14,10));d.rectangle((1,1,w-2,h-2),outline=(190,130,60))
    save(name,im)
def smith(name,tpl,gear,ing,res):
    im,d=panel(98,26)
    for i,it in enumerate((tpl,gear,ing)):slot(im,4+i*18,4,it)
    arrow(im,62,8,13);slot(im,77,4,res);save(name,im)
def forge_gui(name):
    """The Forging tab: item to keep + second item -> result (like the real screen)."""
    flow(name,['siftie_sword','enchanted_book','siftie_sword'],['+','>'])
os.makedirs('out',exist_ok=True)
# ---- recipes (from MaterialRecipes / GuideBook) ----
crafting('recipe_anvil',[['iron_block','forging_book','iron_block'],[None,'anvil',None],['diamond','diamond','diamond']],'enchanting_anvil')
crafting('recipe_siftie_template',[['siftie_ingot','blaze_rod','siftie_ingot'],['blaze_rod','netherite_upgrade_smithing_template','blaze_rod'],['siftie_ingot','blaze_rod','siftie_ingot']],'siftie_template')
crafting('recipe_enderite_template',[[None,'enderite_scrap',None],['enderite_scrap','siftie_template','enderite_scrap'],[None,'enderite_scrap',None]],'enderite_template')
crafting('recipe_enderite_stick',[[None,'enderite_ingot',None],[None,'enderite_ingot',None],[None,None,None]],'enderite_stick')
crafting('recipe_wardenite_ingot',[['wardenite_scrap']*3,['wardenite_scrap','echo_shard','wardenite_scrap'],['wardenite_scrap']*3],'wardenite_ingot')
crafting('recipe_darkness_book',[['fire_book','water_book',None],['earth_book','wind_book',None],[None,None,None]],'darkness_book')
crafting('recipe_guide',[['book','iron_ingot',None],[None,None,None],[None,None,None]],'written_book')
# ---- upgrades (template + gear + ingot -> result, the Upgrading tab's order) ----
smith('upgrade_netherite','netherite_upgrade_smithing_template','diamond_pickaxe','netherite_ingot','netherite_pickaxe')
smith('upgrade_siftie','siftie_template','netherite_pickaxe','siftie_ingot','siftie_pickaxe')
smith('upgrade_enderite','enderite_template','siftie_pickaxe','enderite_ingot','enderite_pickaxe')
smith('upgrade_wardenite','wardenite_template','enderite_pickaxe','wardenite_ingot','wardenite_pickaxe')
# ---- materials ----
flow('ore_siftie',['siftie_ore','raw_siftie','siftie_ingot'],['>','>'])
flow('ore_enderite',['enderite_ore','enderite_scrap','enderite_ingot'],['>','>'])
flow('source_wardenite',['wardenite_scrap','echo_shard','wardenite_ingot'],['+','>'])
chain('progression',['diamond','netherite_ingot','siftie_ingot','enderite_ingot','wardenite_ingot'])
row('templates',['netherite_upgrade_smithing_template','siftie_template','enderite_template','wardenite_template'])
row('books',['fire_book','water_book','earth_book','wind_book','darkness_book'])
row('miner_trades',['iron_ingot','raw_gold','coal','diamond_pickaxe','forging_book'])
row('gear_siftie',['siftie_sword','siftie_pickaxe','siftie_axe','siftie_helmet','siftie_chestplate'])
row('gear_enderite',['enderite_sword','enderite_pickaxe','enderite_axe','enderite_helmet','enderite_chestplate'])
row('gear_wardenite',['wardenite_sword','wardenite_pickaxe','wardenite_axe','wardenite_helmet','wardenite_chestplate'])
row('ritual_drops',['dragon_heart','warden_heart','bat_wing'])
forge_gui('forge_slots')
ritual('ritual_layout')
anvil_render('anvil')
portrait('miner',"toolsmith",[('miner_helmet','head'),('miner_backpack','body')],(70,52,36))
portrait('magician',"librarian",[('magician_hat','head'),('magician_robes','body')],(44,26,60))
json.dump(FIG,open('out/figures.json','w'),indent=1)
print(len(FIG),'figures')
