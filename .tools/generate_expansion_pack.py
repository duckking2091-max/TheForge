"""Author and validate BlastForge's exact Minecraft 1.21.11 resource pack.

All custom art/geometry below is original pixel/cuboid artwork. The vanilla
client is only read for unchanged fallback definitions/reference validation.
Run with the bundled Python (Pillow); no external asset service is required.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import argparse, hashlib, json, math, os, random, re, tempfile, urllib.request, zipfile

ROOT = Path(__file__).resolve().parents[1]
PACK = ROOT / "resource-pack"
OUT = ROOT / "release" / "expansion"
ART = ROOT / "docs" / "expansion" / "previews"
CLIENT_SHA = "ba2df812c2d12e0219c489c4cd9a5e1f0760f5bd"
CLIENT = Path(tempfile.gettempdir()) / "blastforge-research-minecraft-1.21.11-client.jar"
TIER = {"siftie": ((39,143,157),(110,247,230),(8,57,70)), "enderite": ((102,53,151),(216,163,255),(29,15,57)), "wardenite": ((14,67,74),(56,228,215),(6,20,28))}
GOLD=(213,168,66);INK=(10,15,25);WHITE=(217,242,244)
PIECES=["sword","pickaxe","axe","shovel","hoe","helmet","chestplate","leggings","boots"]

def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,indent=2)+"\n",encoding="utf-8")

def save_image(path,image):
    path.parent.mkdir(parents=True,exist_ok=True);image.save(path)

def catalog():
    text=(ROOT/"src/main/java/dev/duckmc/blastforge/expansion/ItemModels.java").read_text()
    initial=text.split("new ArrayList<>(List.of(",1)[1].split("));",1)[0]
    ids=re.findall(r'"([a-z_]+)"',initial)
    ids += [f"{tier}_{piece}" for tier in TIER for piece in PIECES]
    ids += ["anvil_hammer","anvil_tools","magician_robes","miner_helmet","miner_backpack"]
    source=(ROOT/"src/main/java/dev/duckmc/blastforge/expansion/ExpansionItems.java").read_text()
    carriers={id:mat.lower() for id,mat in re.findall(r'map.put\("([a-z_]+)",Material.([A-Z_]+)\)',source)}
    carriers.update({f"{book}_book":"enchanted_book" for book in ["fire","water","earth","wind","darkness"]})
    carriers.update({f"{tier}_{piece}":f"netherite_{piece}" for tier in TIER for piece in PIECES})
    carriers.update({id:"paper" for id in ids[-5:]})
    # Appended later (ItemModels keeps earlier numbers stable): spears for each tier.
    ids += [f"{tier}_spear" for tier in TIER];carriers.update({f"{tier}_spear":"netherite_spear" for tier in TIER})
    ids += ["magician_hat"];carriers["magician_hat"]="paper"   # appended later: the Magician's hat (head slot)
    tabs=["tab_forging","tab_upgrading","tab_ritual","tab_rename","tab_close"];ids += tabs;carriers.update({t:"paper" for t in tabs})   # workshop tab icons
    ids += ["guide_button"];carriers["guide_button"]="paper"   # invisible buttons of the open-book guide
    assert len(ids)==65 and len(set(ids))==65
    assert carriers["raw_siftie"]=="prismarine_crystals"
    assert all(id in carriers for id in ids)
    return [(id,9000+i,carriers[id]) for i,id in enumerate(ids)]

ART_DIR=Path(__file__).resolve().parent/"art"
def pixel_image(id):
    # Hand-made 16x16 art in .tools/art/<id>.png wins over the generated sprite (ingots, raw ores, ore faces).
    if (ART_DIR/f"{id}.png").exists():return Image.open(ART_DIR/f"{id}.png").convert("RGBA")
    return generated_pixel_image(id)
def generated_pixel_image(id):
    """32px handmade silhouettes, bevels, seams, rivets and individual motifs."""
    im=Image.new("RGBA",(32,32));d=ImageDraw.Draw(im)
    tier=next((t for t in TIER if id.startswith(t+"_")),"siftie");base,light,dark=TIER[tier]
    def poly(points,fill=base):d.polygon(points,fill=fill,outline=INK)
    def line(points,fill=light,width=1):d.line(points,fill=fill,width=width)
    piece=id.rsplit("_",1)[-1]
    if piece in PIECES:
        if piece in PIECES[:5]:
            line([(6,27),(23,10)],INK,5);line([(6,27),(23,10)],(83,55,50),3);line([(7,26),(23,10)],GOLD)
            if piece=="sword":poly([(11,17),(12,12),(23,2),(28,2),(28,7),(18,17),(16,18)]);line([(14,14),(25,4)]);line([(9,17),(15,23)],GOLD,3)
            elif piece=="pickaxe":poly([(9,7),(14,4),(24,6),(29,12),(29,17),(24,12),(19,10),(12,10)]);line([(13,6),(23,8),(27,12)])
            elif piece=="axe":poly([(16,4),(26,3),(29,8),(27,17),(22,20),(20,13),(14,12)]);line([(24,5),(27,8),(25,15)])
            elif piece=="shovel":poly([(21,3),(28,3),(29,10),(23,16),(17,10)]);line([(23,5),(25,5),(26,9),(23,12)])
            else:poly([(12,5),(25,5),(28,9),(26,16),(23,16),(22,10),(12,9)]);line([(14,6),(24,7)])
            line([(8,24),(10,26)],tier=="enderite" and (151,91,207) or GOLD,2)
        elif piece=="helmet":
            poly([(7,9),(11,5),(22,5),(26,9),(26,23),(21,25),(21,17),(11,17),(11,25),(6,22)]);d.rectangle((11,10,21,14),fill=dark);line([(9,9),(12,7),(22,7),(24,10)]);d.rectangle((14,5,17,18),fill=light);d.rectangle((15,7,16,14),fill=base)
        elif piece=="chestplate":
            poly([(8,5),(12,8),(19,8),(23,5),(28,9),(26,17),(23,16),(23,27),(8,27),(8,16),(5,18),(3,10)]);line([(6,11),(8,9),(13,12),(19,12),(24,9)]);line([(9,23),(22,23)],GOLD,2);d.polygon([(16,13),(20,17),(16,21),(12,17)],fill=light);d.point((16,17),fill=WHITE)
        elif piece=="leggings":
            poly([(7,5),(24,5),(25,12),(23,27),(17,27),(16,17),(14,27),(7,27),(6,12)]);line([(8,7),(23,7)],GOLD,2);line([(9,14),(9,24)]);line([(21,14),(21,24)]);d.rectangle((12,5,18,9),fill=dark);d.rectangle((14,6,16,8),fill=GOLD)
        else:
            poly([(5,8),(13,8),(13,19),(15,22),(14,27),(3,27),(3,22),(5,19)]);poly([(20,8),(27,8),(27,20),(30,23),(29,27),(18,27),(18,22),(20,19)]);line([(6,11),(11,11)],GOLD,2);line([(21,11),(25,11)],GOLD,2);line([(5,23),(11,23)]);line([(20,23),(26,23)])
        # Distinct structural inlays across tiers, rather than a uniform recolor.
        if tier=="enderite":d.polygon([(17,14),(19,16),(17,18),(15,16)],fill=(223,161,255))
        if tier=="wardenite":line([(9,14),(11,16),(10,18)],(45,239,217));line([(23,19),(21,21),(22,23)],(45,239,217))
    elif id.endswith("_book"):
        color={"fire":(222,74,31),"water":(34,139,220),"earth":(87,146,64),"wind":(156,222,221),"darkness":(104,54,167),"forging":(45,182,216)}[id.split("_")[0]]
        poly([(6,5),(22,3),(27,7),(27,26),(11,29),(5,24)],(64,37,57));poly([(6,5),(22,3),(25,6),(10,9)],(213,203,170));poly([(9,9),(26,6),(26,25),(10,28)],color);line([(11,11),(24,8)],GOLD);line([(12,25),(23,23)],GOLD);line([(6,8),(6,22)],color,2)
        sig=id.split("_")[0]
        if sig=="fire":poly([(16,12),(20,16),(20,20),(16,23),(13,20),(13,17),(16,18)],(255,197,56))
        elif sig=="water":poly([(18,12),(23,20),(20,23),(15,21),(14,18)],(149,240,255))
        elif sig=="earth":poly([(13,21),(17,13),(20,16),(24,22)],(223,225,157))
        elif sig=="wind":line([(13,15),(22,15),(23,17),(16,19),(14,21),(22,21)],(245,255,255),2)
        elif sig=="darkness":d.ellipse((14,13,23,22),fill=(205,144,249));d.ellipse((18,11,25,19),fill=color)
        else:line([(14,19),(20,13)],(230,246,253),3);line([(13,13),(19,19)],GOLD,2)
    elif id.endswith("_ore"):
        poly([(4,6),(22,3),(29,9),(27,26),(9,29),(3,23)],(55,64,73) if tier=="siftie" else (56,47,75));line([(5,7),(21,5),(26,10)],(93,102,109));line([(9,28),(27,25),(28,11)],(20,26,35));
        for x,y in [(8,11),(17,8),(23,15),(12,21),(21,24)]:d.polygon([(x,y),(x+3,y-1),(x+4,y+2),(x+1,y+4)],fill=light);d.point((x+1,y),fill=WHITE)
    elif id.endswith("_ingot"):
        poly([(4,15),(10,7),(24,7),(29,16),(23,24),(8,24)],dark);poly([(4,15),(10,8),(24,8),(28,15),(22,19),(9,19)]);line([(10,10),(23,10)],light,2);line([(10,21),(22,21)],light);d.polygon([(14,12),(17,10),(20,12),(17,15)],fill=GOLD)
    elif id.endswith("_scrap"):
        poly([(5,12),(12,6),(19,9),(25,5),(28,17),(23,25),(15,22),(8,26)],dark);line([(8,13),(13,9),(17,13),(23,10),(24,16),(19,21)],light,2);line([(11,19),(15,16),(19,18)],base,2)
    elif id=="raw_siftie":
        for x,y,r in [(13,11,7),(21,18,7),(10,23,6)]:poly([(x-r,y),(x-2,y-r),(x+4,y-r+1),(x+r,y+3),(x,y+r)],base);line([(x-2,y-r+2),(x,y),(x+3,y+3)],light,2)
    elif id.endswith("_template"):
        poly([(7,4),(24,4),(27,8),(27,27),(5,27),(5,8)],(91,82,76));d.rectangle((8,7,23,24),fill=dark,outline=GOLD);d.polygon([(16,9),(22,16),(18,16),(18,22),(13,22),(13,16),(9,16)],fill=light);line([(8,25),(24,25)],(181,168,136))
    elif id=="enderite_stick":
        line([(8,27),(23,5)],INK,6);line([(8,27),(23,5)],(63,35,92),4);line([(9,26),(23,5)],(175,95,232),1)
        for x,y in [(12,22),(17,15),(21,9)]:line([(x-2,y-1),(x+2,y+1)],(225,172,255),2)
    elif id in ["dragon_heart","warden_heart"]:
        color=(104,45,149) if id=="dragon_heart" else (15,91,92);glow=(212,110,255) if id=="dragon_heart" else (82,247,221)
        poly([(16,9),(21,5),(27,8),(29,14),(26,21),(16,29),(6,21),(3,14),(5,8),(11,5)],color);line([(16,9),(14,14),(18,17),(14,20),(16,26)],glow,2);line([(7,10),(6,14),(9,19)],glow);line([(22,8),(25,10)],glow,2)
        if id=="dragon_heart":poly([(5,10),(2,5),(9,7)],(29,20,40));poly([(23,7),(29,4),(27,12)],(29,20,40))
    elif id=="bat_wing":
        poly([(3,9),(9,7),(16,3),(25,5),(30,12),(27,25),(22,18),(18,25),(14,20),(8,27),(8,18),(3,21)],(70,41,74));line([(5,10),(16,6),(25,8),(28,13)],(150,96,149));line([(16,7),(14,18),(8,24)],(111,65,117));line([(16,7),(23,17),(26,23)],(111,65,117))
    elif id=="enchanting_anvil":
        poly([(3,10),(23,7),(29,11),(24,16),(18,17),(18,22),(25,25),(20,28),(7,28),(3,24),(10,21),(10,16),(3,14)],dark);poly([(3,10),(23,7),(29,11),(20,14),(9,14)],base);line([(5,10),(22,9)],light,2);line([(8,25),(21,25)],GOLD,2)
    elif id=="anvil_hammer":
        line([(10,27),(17,11)],(91,52,41),4);poly([(9,5),(25,5),(27,11),(22,15),(11,13)],(83,126,149));line([(12,6),(23,6)],(158,226,238),2);line([(12,23),(15,24)],GOLD,2)
    elif id=="anvil_tools":
        line([(8,27),(13,5)],(114,78,48),3);line([(20,27),(23,7)],(114,78,48),3);poly([(9,3),(15,3),(16,9),(11,11)],(118,158,169));line([(18,5),(20,11),(25,11),(28,5)],(174,192,197),2)
    elif id=="magician_robes":
        poly([(9,5),(14,9),(18,9),(23,5),(29,14),(24,17),(26,28),(5,28),(7,17),(2,14)],(80,38,123));line([(11,10),(15,14),(20,10)],GOLD,2);line([(8,24),(24,24)],GOLD);d.polygon([(16,14),(19,18),(16,21),(13,18)],fill=(202,137,254))
    elif id=="miner_helmet":
        poly([(5,13),(8,7),(23,7),(26,13),(28,14),(28,20),(3,20),(3,15)],(175,115,35));line([(8,10),(12,8),(22,9)],GOLD,2);d.rectangle((12,10,20,18),fill=(51,60,66),outline=INK);d.rectangle((14,12,18,16),fill=(252,242,167))
    elif id=="miner_backpack":
        poly([(7,6),(11,3),(22,3),(26,7),(26,27),(5,27),(5,8)],(105,66,40));d.rectangle((8,10,23,19),fill=(139,91,51),outline=GOLD);d.rectangle((9,21,23,26),fill=(75,47,31));line([(10,5),(10,26)],(175,139,77),2);line([(21,5),(21,26)],(175,139,77),2);d.rectangle((13,11,18,14),fill=GOLD)
    elif id=="legendary_scythe":
        preview=scythe_pixels().resize((21,32),Image.Resampling.NEAREST);im.alpha_composite(preview,(5,0))
    else:raise ValueError(id)
    return im

def scythe_pixels():
    im=Image.new("RGBA",(128,192));d=ImageDraw.Draw(im)
    # Original silhouette based on the supplied tall hooked scythe reference.
    shaft=[(41,30),(47,31),(47,91),(54,116),(57,146),(49,173),(44,185),(37,183),(45,167),(50,144),(47,117),(40,92)]
    d.polygon(shaft,fill=(20,18,26),outline=(7,7,11))
    blade=[(45,21),(32,15),(19,18),(26,12),(17,13),(28,5),(38,4),(35,10),(48,8),(44,3),(37,0),(51,3),(67,13),(66,8),(77,16),(79,12),(94,23),(94,19),(106,34),(114,53),(118,74),(114,94),(113,73),(108,57),(101,46),(90,38),(78,35),(69,28),(63,32),(55,28),(49,35),(42,32),(36,35),(40,26),(26,30),(32,22),(20,24),(28,18)]
    d.polygon(blade,fill=(24,22,30),outline=(7,7,10))
    d.line([(45,5),(58,11),(65,17),(73,20),(84,25),(93,32),(103,47),(110,64),(113,83)],fill=(79,44,111),width=2)
    d.line([(31,15),(37,17),(39,24),(44,24),(47,32),(46,50),(43,55),(46,60),(43,64),(46,72),(44,84)],fill=(104,50,145),width=1)
    for p in [[(54,17),(59,15),(63,22),(70,21),(75,27)],[(84,29),(87,34),(94,36),(98,43)],[(47,100),(51,105),(48,110)],[(51,134),(55,141),(50,147)]]:d.line(p,fill=(139,73,194),width=1)
    for y in [97,101,109,160,165,176]:d.line([(45 if y<115 else 46,y),(51 if y<115 else 51,y+3)],fill=(102,52,142),width=2)
    d.line([(40,182),(33,183),(30,181),(31,186),(37,189),(45,188),(48,186)],fill=(29,23,37),width=3)
    # Tapered edge highlights remain sparse, never a solid neon silhouette.
    d.line([(99,42),(107,58),(112,77)],fill=(149,84,191),width=1)
    # Inward hooked tip, as in the reference: the blade end curls back toward the shaft.
    d.polygon([(113,88),(116,96),(113,103),(107,106),(102,105),(108,101),(111,96)],fill=(24,22,30),outline=(7,7,10))
    d.line([(114,92),(113,99),(108,103)],fill=(139,73,194),width=1)
    return im

def material_texture(id):
    tier=next((t for t in TIER if id.startswith(t+"_")),"siftie");base,light,dark=TIER[tier]
    if id in ["magician_robes"]:base,light,dark=(73,32,109),(150,87,195),(32,14,57)
    if id in ["miner_helmet"]:base,light,dark=(169,113,37),(237,194,92),(72,48,27)
    if id in ["miner_backpack","anvil_hammer","anvil_tools"]:base,light,dark=(91,65,48),(179,141,83),(37,27,27)
    if id=="legendary_scythe":base,light,dark=(22,19,29),(125,63,170),(8,7,12)
    if id=="enchanting_anvil":return diamond_texture()
    im=Image.new("RGBA",(32,32));d=ImageDraw.Draw(im);rng=random.Random(id)
    for y in range(32):
        for x in range(32):
            n=rng.randint(-8,8);c=tuple(max(0,min(255,v+n)) for v in base);im.putpixel((x,y),(*c,255))
    d.line([(0,0),(31,0),(31,31)],fill=light);d.line([(0,1),(0,31),(30,31)],fill=dark)
    # Gold fastening seams and enamel glints on dedicated texture UV regions.
    d.rectangle((1,1,6,6),fill=GOLD);d.rectangle((9,1,14,6),fill=light);d.rectangle((17,1,22,6),fill=dark);d.rectangle((25,1,30,6),fill=(229,226,185))
    return im

def diamond_texture():
    """32x32 sheet. uv (0,0,4,4) deep teal engraving, (4,0,8,4) bright edge; uv rows 8-16 are the faceted diamond surface."""
    im=Image.new("RGBA",(32,32));d=ImageDraw.Draw(im);rng=random.Random("diamond-anvil")
    palette=[(44,169,163),(74,237,217),(104,244,226),(161,251,232),(214,255,248)]
    for y in range(32):
        for x in range(32):
            band=((x+y)//5+(x-y)//7)%5;c=palette[min(4,max(0,band+rng.randint(-1,1)))];im.putpixel((x,y),(*c,255))
    for x0 in range(0,32,8):d.line([(x0,16),(x0+7,31)],fill=(214,255,248));d.line([(x0+7,16),(x0,31)],fill=(44,169,163))
    d.rectangle((0,16,31,31),outline=(14,90,84))
    d.rectangle((0,0,7,7),fill=(18,78,74));d.rectangle((8,0,15,7),fill=(230,255,252))
    return im

def cube(a,b,uv=(0,0,16,16),texture="#body",emission=0):
    node={"from":list(a),"to":list(b),"faces":{face:{"uv":list(uv),"texture":texture} for face in ["north","south","east","west","up","down"]}}
    if emission:node["light_emission"]=emission
    return node

def geometry(id):
    if id in ("siftie_ore","enderite_ore"):
        # A full block with the ore texture on every face; the plugin lays it over the vanilla carrier block.
        return {"parent":"minecraft:block/cube_all","textures":{"all":f"blastforge:item/{id}","particle":f"blastforge:item/{id}"}}
    textures={"body":f"blastforge:item/{id}_surface","particle":f"blastforge:item/{id}_surface"}
    elements=[]
    if id=="enchanting_anvil":
        # Diamond anvil. Long axis is x. Every part is 0.25-0.4 px larger than the vanilla anvil part it covers,
        # so the placed display hides the real anvil block underneath (checked against the vanilla anvil model).
        for a,b,uv in [((1.6,0,1.6),(14.4,4.3,14.4),(0,8,16,16)),((2.6,4.3,3.6),(13.4,5.4,12.4),(0,8,16,16)),((3.6,5.4,5.6),(12.4,10.3,10.4),(0,8,16,16)),
                       ((-0.4,10.3,2.6),(16.4,16.4,13.4),(0,8,16,16)),((-3.4,12.4,4.6),(-0.4,15.6,11.4),(8,8,16,16)),((16.4,12.8,5.6),(18.2,15.2,10.4),(8,8,16,16))]:elements.append(cube(a,b,uv))
        # Engraved hammer emblem on both long sides and a bright top edge, so it reads as a forging station.
        for z in (2.5,13.5):
            elements.append(cube((5.5,12.2,z-0.05),(10.5,13.4,z+0.05),(0,0,4,4)))
            elements.append(cube((6.2,11.2,z-0.05),(7.2,14.6,z+0.05),(0,0,4,4)))
        elements.append(cube((-0.45,15.9,2.55),(16.45,16.5,13.45),(4,0,8,4)))
    elif id=="anvil_hammer":
        # Lies flat: the underside is model y 8, so the station places it straight onto the anvil's top face.
        elements=[cube((0.5,8,7.2),(10.5,9.6,8.8),(8,8,12,12)),cube((0.2,7.95,6.9),(2.6,9.9,9.1),(1,1,3,3)),cube((10,8,4),(14.5,12,12),(9,1,11,3)),
                  cube((14.5,8.4,4.6),(15.5,11.6,11.4),(5,1,7,3)),cube((9.5,8.2,4.5),(10,11.8,11.5),(1,1,3,3))]
    elif id=="anvil_tools":
        elements=[cube((13,4,12),(14,15,13)),cube((11,14,11),(16,17,14),(4,0,8,4)),cube((2,3,12),(3,13,13)),cube((1,12,11),(2,17,12),(4,0,8,4)),cube((3,12,11),(4,17,12),(4,0,8,4)),cube((0,2,13),(16,3,14),(0,0,4,4))]
    elif id=="magician_robes":
        # Visible panels leave the face, crossed arms and legs unobstructed.
        elements=[cube((3,3,4.6),(13,13,11.4)),cube((2.6,.5,4),(13.4,4,12)),cube((3,6,4.3),(13,7,4.6),(0,0,4,4)),cube((7.3,9,4.1),(8.7,11,4.4),(4,0,8,4)),cube((3,11,4.3),(4,14,10)),cube((12,11,4.3),(13,14,10))]
    elif id=="miner_helmet":
        elements=[cube((3,6,3),(13,9,13)),cube((4,9,4),(12,12,12)),cube((2,5,2),(14,6,14)),cube((6,7,1),(10,10,3),(8,0,12,4)),cube((6.5,7.5,.8),(9.5,9.5,1),(12,0,16,4),emission=12)]
    elif id=="miner_backpack":
        elements=[cube((4,2,4),(12,14,11)),cube((3.5,3,10.8),(12.5,8,13)),cube((5,8,11),(11,12,12)),cube((5.5,2,12.8),(6.5,14,13.1),(0,0,4,4)),cube((9.5,2,12.8),(10.5,14,13.1),(0,0,4,4)),cube((7,10,12),(9,11.5,12.4),(0,0,4,4))]
    elif id=="legendary_scythe":
        pixels=scythe_pixels();textures["art"]="blastforge:item/legendary_scythe_art"
        # Merge contiguous silhouette spans by row; front/back map back to the authored art.
        # Coordinates stay within vanilla's [-16,32] element bounds; depth is real.
        for y in range(192):
            x=0
            while x<128:
                if not pixels.getpixel((x,y))[3]:x+=1;continue
                left=x
                while x<128 and pixels.getpixel((x,y))[3]:x+=1
                scale=44/192;a=(left*scale-4,30-(y+1)*scale,7.55);b=(x*scale-4,30-y*scale,8.45)
                # Thin side faces sample a plain dark patch of the surface texture, not the whole
                # swatch sheet, so edges never show squashed gold/enamel specks.
                node=cube(a,b,(4,10,5,11));uv=[left/8,y/12,x/8,(y+1)/12]
                for face in ["north","south"]:node["faces"][face]={"uv":uv,"texture":"#art"}
                elements.append(node)
        # Extra crown ridge and grip bands make the profile read in oblique views.
        for y in [-9,-5,5,9]:elements.append(cube((5.8,y,7.3),(7.5,y+.35,8.7),(4,0,8,4)))
    else:return None
    display={"gui":{"rotation":[18,-28,0],"translation":[0,0,0],"scale":[.7,.7,.7]},"fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]},"ground":{"rotation":[0,0,0],"translation":[0,3,0],"scale":[.5,.5,.5]},"thirdperson_righthand":{"rotation":[0,-90,0],"translation":[0,1,0],"scale":[.65,.65,.65]},"firstperson_righthand":{"rotation":[0,-90,10],"translation":[1,0,0],"scale":[.65,.65,.65]}}
    if id=="legendary_scythe":
        # Held views are turned 180 degrees so the blade's hooked tip points away from the player.
        display.update({"gui":{"rotation":[0,0,-12],"translation":[-1,0,0],"scale":[.38,.38,.38]},"firstperson_righthand":{"rotation":[0,98,8],"translation":[1,2,-1],"scale":[.8,.8,.8]},"thirdperson_righthand":{"rotation":[0,90,-8],"translation":[0,4,0],"scale":[.95,.95,.95]},"ground":{"rotation":[0,0,0],"translation":[0,4,0],"scale":[.3,.3,.3]}})
        display["firstperson_lefthand"]={"rotation":[0,-98,-8],"translation":[1,2,-1],"scale":[.8,.8,.8]};display["thirdperson_lefthand"]={"rotation":[0,-90,8],"translation":[0,4,0],"scale":[.95,.95,.95]}
    if id=="enchanting_anvil":
        # The old hand view used scale .65 on a 21-px-wide model and filled the screen.
        display.update({"gui":{"rotation":[30,225,0],"translation":[0,-1,0],"scale":[.55,.55,.55]},"firstperson_righthand":{"rotation":[0,45,0],"translation":[2,1,0],"scale":[.3,.3,.3]},"thirdperson_righthand":{"rotation":[75,45,0],"translation":[0,2.5,0],"scale":[.3,.3,.3]},"ground":{"rotation":[0,0,0],"translation":[0,3,0],"scale":[.25,.25,.25]},"fixed":{"rotation":[0,0,0],"translation":[0,0,0],"scale":[1,1,1]}})
        display["firstperson_lefthand"]={"rotation":[0,225,0],"translation":[2,1,0],"scale":[.3,.3,.3]};display["thirdperson_lefthand"]={"rotation":[75,225,0],"translation":[0,2.5,0],"scale":[.3,.3,.3]}
    return {"ambientocclusion":False,"textures":textures,"elements":elements,"display":display}

def armor(tier,leggings=False):
    base,light,dark=TIER[tier];im=Image.new("RGBA",(64,32));d=ImageDraw.Draw(im)
    # Vanilla biped UV islands: cap, torso, right leg/boot, right arm.
    boxes=[(0,0,32,16),(16,16,40,32),(0,16,16,32),(40,16,56,32)]
    for i,(x0,y0,x1,y1) in enumerate(boxes):
        d.rectangle((x0,y0,x1-1,y1-1),fill=base)
        for y in range(y0,y1):
            for x in range(x0,x1):
                n=((x*13+y*7+i*3)%7)-3;im.putpixel((x,y),(*tuple(max(0,min(255,c+n)) for c in base),255))
        d.line((x0,y0,x1-1,y0),fill=light);d.line((x0,y1-2,x1-1,y1-2),fill=GOLD)
        for x in range(x0+2,x1-1,4):d.rectangle((x,y0+3,x+1,y0+5),fill=dark)
    if not leggings:
        d.rectangle((8,8,15,11),fill=dark);d.line((8,7,15,7),fill=light);d.rectangle((20,20,27,26),fill=dark);d.polygon([(24,20),(27,23),(24,26),(21,23)],fill=light)
    else:d.line((0,18,15,18),fill=GOLD,width=2);d.line((3,23,3,29),fill=light);d.line((11,23,11,29),fill=light)
    if tier=="enderite":
        for x,y in [(4,4),(23,18),(44,23),(9,22)]:d.line([(x,y),(x+2,y+2),(x,y+4)],fill=(225,163,255))
    if tier=="wardenite":
        for x,y in [(5,5),(23,20),(45,21),(9,23)]:d.line([(x,y),(x+2,y+2),(x+1,y+4)],fill=(67,250,223))
    return im

def forge_screen(sxy,inventory_dark):
    """Blacksmith's forge: dark brick wall, carved stone border, glowing furnace mouth, iron anvil plate, sparks."""
    W,H=176,222;rng=random.Random("forge");im=Image.new("RGBA",(W,H));d=ImageDraw.Draw(im)
    # Brick wall
    d.rectangle((0,0,W-1,H-1),fill=(30,22,20))
    for row in range(0,H,6):
        off=0 if (row//6)%2==0 else 6
        for x in range(-off,W,12):
            s=rng.randint(-8,8);d.rectangle((x+1,row+1,x+11,row+5),fill=(70+s,44+s//2,36+s//2))
            d.line((x+1,row+1,x+10,row+1),fill=(86+s,56+s//2,46+s//2))
    # Carved stone frame, as on the Dark Ritual screen
    d.rectangle((0,0,W-1,H-1),outline=(20,19,18));d.rectangle((1,1,W-2,H-2),outline=(78,75,71));d.rectangle((2,2,W-3,H-3),outline=(52,50,47));d.rectangle((3,3,W-4,H-4),outline=(30,29,28))
    for i in range(6,W-6,7):d.line([(i,2),(i+3,4)],fill=(25,24,23));d.line([(i,H-5),(i+3,H-3)],fill=(25,24,23))
    # Furnace mouth under the anvil: stone arch, dark inside, fire at the bottom
    fx0,fx1,fy0,fy1=58,118,78,106
    d.rectangle((fx0-4,fy0-2,fx1+4,fy1+2),fill=(58,56,54));d.pieslice((fx0-4,fy0-14,fx1+4,fy0+14),180,360,fill=(58,56,54))
    d.rectangle((fx0,fy0,fx1,fy1),fill=(14,8,6));d.pieslice((fx0,fy0-10,fx1,fy0+10),180,360,fill=(14,8,6))
    for y in range(fy1-12,fy1+1):
        t=(y-(fy1-12))/12
        for x in range(fx0+1,fx1):
            flick=rng.random()
            if flick<0.25+0.7*t:
                c=(255,int(90+130*t*flick),int(20*t)) if flick<0.6 else (255,200,60)
                im.putpixel((x,y),(*c,255))
    for k in range(18):x=rng.randint(fx0+3,fx1-3);y=rng.randint(fy0-6,fy1-14);im.putpixel((x,y),(255,rng.randint(140,220),40,255))
    # Iron anvil plate the slots sit on
    px0,px1,py0,py1=36,160,48,76
    d.rectangle((px0,py0,px1,py1),fill=(72,74,80),outline=(20,20,24));d.line((px0+1,py0+1,px1-1,py0+1),fill=(130,134,142));d.line((px0+1,py1-1,px1-1,py1-1),fill=(44,45,50))
    for x in (px0+3,px1-3):
        for y in (py0+3,py1-3):d.rectangle((x-1,y-1,x+1,y+1),fill=(150,154,160),outline=(35,35,40))
    def iron_slot(x,y,edge=(40,40,46)):
        d.rectangle((x-1,y-1,x+16,y+16),fill=(34,34,38));d.line((x-1,y-1,x+15,y-1),fill=(18,18,20));d.line((x-1,y-1,x-1,y+15),fill=(18,18,20))
        d.line((x+16,y,x+16,y+16),fill=(110,112,120));d.line((x,y+16,x+16,y+16),fill=(110,112,120))
    for slot in (20,22):iron_slot(*sxy(slot))
    rx,ry=sxy(25)
    for r,c in [(4,(120,40,10)),(3,(200,80,20)),(2,(255,140,40))]:d.rectangle((rx-1-r,ry-1-r,rx+16+r,ry+16+r),outline=c)
    iron_slot(rx,ry)
    for k in range(14):a=rng.random()*6.283;dist=rng.randint(13,22);d.point((rx+7.5+dist*math.cos(a),ry+7.5+dist*math.sin(a)),fill=(255,rng.randint(170,240),60))
    # Glowing-metal plus and arrow
    x,y=sxy(21);d.rectangle((x+2,y+6,x+13,y+9),fill=(230,120,40));d.rectangle((x+6,y+2,x+9,y+13),fill=(230,120,40));d.rectangle((x+3,y+7,x+12,y+8),fill=(255,200,90));d.rectangle((x+7,y+3,x+8,y+12),fill=(255,200,90))
    x0,y=sxy(23);x1=sxy(24)[0]+12;d.rectangle((x0+1,y+5,x1-8,y+10),fill=(230,120,40));d.polygon([(x1-9,y),(x1,y+7),(x1-9,y+15)],fill=(230,120,40));d.rectangle((x0+1,y+7,x1-8,y+8),fill=(255,200,90))
    # Tools hanging on the wall: hammer (left) and tongs (right)
    d.line([(14,22),(30,40)],fill=(110,72,38),width=3);d.polygon([(22,16),(34,28),(29,33),(17,21)],fill=(150,152,160),outline=(30,30,34))
    d.line([(128,20),(138,42)],fill=(60,60,66),width=2);d.line([(136,20),(130,42)],fill=(60,60,66),width=2);d.ellipse((130,18,136,24),outline=(90,90,96))
    # Utility slots and inventory, carved like the ritual screen
    for slot in (8,45,46,47,53):iron_slot(*sxy(slot))
    d.line([(8,130),(167,130)],fill=(20,15,14))
    inventory_dark(d)
    return im

def gui(theme):
    """176x222 backgrounds drawn over a 6-row chest. Slot (r,c) sits at x=8+18c, y=18+18r."""
    W,H=176,222
    def sxy(slot):return 8+18*(slot%9),18+18*(slot//9)
    def vslot(d,x,y,fill=(139,139,139)):
        d.rectangle((x-1,y-1,x+16,y+16),fill=fill);d.line((x-1,y-1,x+15,y-1),fill=(55,55,55));d.line((x-1,y-1,x-1,y+15),fill=(55,55,55))
        d.line((x+16,y,x+16,y+16),fill=(255,255,255));d.line((x,y+16,x+16,y+16),fill=(255,255,255))
    def panel(d,box,bg=(198,198,198)):
        x0,y0,x1,y1=box;d.rectangle((x0+1,y0+1,x1-1,y1-1),fill=bg)
        d.line((x0+2,y0,x1-2,y0),fill=(0,0,0));d.line((x0+2,y1,x1-2,y1),fill=(0,0,0));d.line((x0,y0+2,x0,y1-2),fill=(0,0,0));d.line((x1,y0+2,x1,y1-2),fill=(0,0,0))
        d.point([(x0+1,y0+1),(x1-1,y0+1),(x0+1,y1-1),(x1-1,y1-1)],fill=(0,0,0))
        d.line((x0+1,y0+2,x0+1,y1-3),fill=(255,255,255));d.line((x0+2,y0+1,x1-3,y0+1),fill=(255,255,255));d.line((x0+2,y0+2,x0+2,y0+2),fill=(255,255,255))
        d.line((x1-1,y0+3,x1-1,y1-2),fill=(85,85,85));d.line((x0+3,y1-1,x1-2,y1-1),fill=(85,85,85))
    def arrow(d,x,y,color=(139,139,139)):
        d.rectangle((x,y+5,x+15,y+10),fill=color);d.polygon([(x+14,y),(x+22,y+7),(x+14,y+15)],fill=color)
    def arrow_long(d,color=(139,139,139)):
        x0,y=sxy(23);x1=sxy(24)[0]+12;d.rectangle((x0+1,y+5,x1-8,y+10),fill=color);d.polygon([(x1-9,y),(x1,y+7),(x1-9,y+15)],fill=color)
    def hammer(d,x,y,head=(198,198,198),dark=(113,113,113),handle=(110,72,38)):
        # Small 16x16 pixel hammer in the style of the vanilla anvil-screen icon.
        H=["................",
           ".......OOOO.....",
           "......OhhhhO....",
           ".....OhHHHddO...",
           "....OhHHHHdddO..",
           "....OHHHHddddO..",
           ".....OHHdddO....",
           "....OwOddO......",
           "...OwwO.O.......",
           "..OwwO..........",
           ".OwwO...........",
           "OwwO............",
           "OwO.............",
           ".O..............",
           "................",
           "................"]
        colors={"O":(0,0,0),"h":(255,255,255),"H":head,"d":dark,"w":handle}
        for j,row in enumerate(H):
            for i,ch in enumerate(row):
                if ch in colors:d.point((x+i,y+j),fill=colors[ch])
    def inventory(d,dark=False):
        for row in range(3):
            for col in range(9):x,y=8+18*col,140+18*row;vslot(d,x,y,(139,139,139) if not dark else (40,38,44)) if not dark else rune_slot(d,x,y)
        for col in range(9):
            x,y=8+18*col,198;vslot(d,x,y) if not dark else rune_slot(d,x,y)
    def rune_slot(d,x,y):d.rectangle((x-1,y-1,x+16,y+16),fill=(30,28,32),outline=(70,64,74))
    im=Image.new("RGBA",(W,H),(0,0,0,0));d=ImageDraw.Draw(im)
    art=Path(__file__).with_name("art")/f"gui_{theme}.png"   # hand-painted screens, kept when regenerating
    if art.exists():return Image.open(art).convert("RGBA")
    if theme=="forging":
        return forge_screen(sxy,lambda dd:inventory(dd,dark=True))
    if theme in ("forging","upgrading"):
        panel(d,(0,0,W-1,H-1))
        for slot in [45,46,47,8,53]:vslot(d,*sxy(slot))
        inventory(d)
        if theme=="forging":
            # Like the vanilla anvil: hammer, item + item -> result.
            hammer(d,sxy(19)[0],sxy(19)[1])
            vslot(d,*sxy(20));vslot(d,*sxy(22));vslot(d,*sxy(25))
            x,y=sxy(21);d.rectangle((x+2,y+6,x+13,y+9),fill=(139,139,139));d.rectangle((x+6,y+2,x+9,y+13),fill=(139,139,139))
            arrow_long(d)
        else:
            # Like the vanilla smithing table: hammer, template + gear + ingot -> result, armour stand.
            hammer(d,sxy(19)[0]-18,sxy(19)[1],head=(77,73,77),dark=(49,41,42))
            for slot in [20,21,22,25]:vslot(d,*sxy(slot))
            g=(115,115,115)  # vanilla-style ghost icons in the empty input slots
            ax,ay=sxy(20);d.rectangle((ax+4,ay+2,ax+11,ay+13),outline=g);d.line((ax+6,ay+5,ax+9,ay+5),fill=g);d.line((ax+6,ay+8,ax+9,ay+8),fill=g)
            gx,gy=sxy(21);d.line([(gx+4,gy+11),(gx+12,gy+3)],fill=g,width=2);d.line([(gx+3,gy+9),(gx+6,gy+12)],fill=g)
            ix,iy=sxy(22);d.polygon([(ix+2,iy+11),(ix+5,iy+6),(ix+14,iy+6),(ix+11,iy+11)],outline=g)
            arrow_long(d)
        return im
    # Dark Ritual: carved stone with a glowing magic circle; scythe in the centre, eight ingredients around it.
    rng=random.Random("ritual")
    d.rectangle((0,0,W-1,H-1),fill=(46,44,42))
    for y in range(H):
        for x in range(W):
            n=rng.randint(-4,4);c=46+n;im.putpixel((x,y),(c,c-1,c-3,255))
    for cx,cy,ex,ey in [(10,60,60,120),(60,20,170,70),(100,95,170,130),(0,110,50,135)]:d.line([(cx,cy),((cx+ex)//2+rng.randint(-8,8),(cy+ey)//2),(ex,ey)],fill=(34,33,32))
    d.rectangle((0,0,W-1,H-1),outline=(20,19,18));d.rectangle((1,1,W-2,H-2),outline=(78,75,71));d.rectangle((3,3,W-4,H-4),outline=(30,29,28))
    for i in range(6,W-6,7):
        d.line([(i,2),(i+3,4)],fill=(25,24,23));d.line([(i,H-5),(i+3,H-3)],fill=(25,24,23))
    for i in range(16,H-6,7):d.line([(2,i),(4,i+3)],fill=(25,24,23));d.line([(W-5,i),(W-3,i+3)],fill=(25,24,23))
    cx,cy=8+18*4+8,18+18*2+8  # centre of slot 22
    pink,glow,deep=(214,110,214),(150,70,160),(90,40,100)
    def ring(r,color,width=1):d.ellipse((cx-r,cy-r,cx+r,cy+r),outline=color,width=width)
    # The eight ring slots are all sqrt(5) grid steps (40 px) from the centre, so the circle runs straight through them.
    R=40
    d.ellipse((cx-54,cy-54,cx+54,cy+54),fill=(55,40,58))
    ring(54,deep,3);ring(53,pink);ring(48,glow);ring(47,pink)
    for k in range(48):  # rune band
        a=2*math.pi*k/48;rx,ry=cx+50.5*math.cos(a),cy+50.5*math.sin(a);d.line([(rx-1,ry-1),(rx+1,ry+1)],fill=pink)
    ring(R,glow,1)
    ring_slots=[5,15,33,41,39,29,11,3]
    pts=[(sxy(sl)[0]+7.5,sxy(sl)[1]+7.5) for sl in ring_slots]
    for i in range(8):d.line([pts[i],pts[(i+3)%8]],fill=glow)  # octagram through the ingredients
    for x,y in pts:d.line([(x,y),(cx,cy)],fill=deep)
    def medallion(slot,r,color,fill=(38,30,40)):
        x,y=sxy(slot);mx,my=x+7.5,y+7.5;d.ellipse((mx-r,my-r,mx+r,my+r),fill=fill,outline=color,width=2);d.ellipse((mx-r+3,my-r+3,mx+r-3,my+r-3),outline=deep)
    for slot in ring_slots:medallion(slot,10,pink)
    # The scythe: a bright halo and a pale core so its dark blade stands out.
    for r,c in [(24,(110,50,120)),(21,(160,80,170)),(18,(220,140,230))]:d.ellipse((cx-r,cy-r,cx+r,cy+r),fill=c)
    d.ellipse((cx-15,cy-15,cx+15,cy+15),fill=(245,225,250),outline=(255,215,120),width=2)
    for k in range(12):a=2*math.pi*k/12;d.line([(cx+19*math.cos(a),cy+19*math.sin(a)),(cx+25*math.cos(a),cy+25*math.sin(a))],fill=(255,215,120))
    for slot in [45,46,47,8,49,53]:medallion(slot,9,glow)
    d.line([(8,130),(167,130)],fill=(25,24,23))
    inventory(d,dark=True)
    return im

def outline_slot(draw,slot,color):
    x,y=8+18*(slot%9),18+18*(slot//9);draw.rectangle((x-1,y-1,x+16,y+16),outline=color)

def item_model(id):
    if id.endswith("_spear"):
        return {"type":"minecraft:select","property":"minecraft:display_context","cases":[{"when":["gui","ground","fixed","on_shelf"],"model":{"type":"minecraft:model","model":f"blastforge:item/{id}"}}],"fallback":{"type":"minecraft:model","model":f"blastforge:item/{id}_in_hand"}}
    if id=="wardenite_helmet":
        # Flat icon in the inventory; the 3D helmet with Warden tendrils on the head and in hand.
        return {"type": "minecraft:select", "property": "minecraft:display_context", "cases": [{"when": ["gui", "ground", "fixed", "on_shelf"], "model": {"type": "minecraft:model", "model": "blastforge:item/wardenite_helmet"}}], "fallback": {"type": "minecraft:model", "model": "blastforge:item/wardenite_helmet_3d"}}
    return {"type":"minecraft:model","model":f"blastforge:item/{id}"}

def generate(client):
    entries=catalog();PACK.mkdir(parents=True,exist_ok=True);OUT.mkdir(parents=True,exist_ok=True);ART.mkdir(parents=True,exist_ok=True)
    write_json(PACK/"pack.mcmeta",{"pack":{"description":"The Forge • Java 1.21.x","min_format":[75,0],"max_format":[75,0]}})
    icons={}
    for id,number,carrier in entries:
        icon=pixel_image(id);icons[id]=icon;save_image(PACK/f"assets/blastforge/textures/item/{id}.png",icon)
        model=geometry(id)
        override=ART_DIR/f"model_{id}.json"   # hand-made models (e.g. the Rune Hearth Anvil) win over generated ones
        if override.exists():
            model=json.loads(override.read_text())
            for texture in model.get("textures",{}).values():
                name=texture.split("/")[-1]
                if (ART_DIR/f"{name}.png").exists():save_image(PACK/f"assets/blastforge/textures/item/{name}.png",Image.open(ART_DIR/f"{name}.png").convert("RGBA"))
        elif model:save_image(PACK/f"assets/blastforge/textures/item/{id}_surface.png",material_texture(id))
        else:model={"parent":"minecraft:item/handheld" if id.rsplit("_",1)[-1] in PIECES[:5] else "minecraft:item/generated","textures":{"layer0":f"blastforge:item/{id}"}}
        if id.endswith("_spear"):
            # Spears, like vanilla: a flat icon in menus and a long in-hand model when held.
            save_image(PACK/f"assets/blastforge/textures/item/{id}_in_hand.png",Image.open(ART_DIR/f"{id}_in_hand.png").convert("RGBA"))
            write_json(PACK/f"assets/blastforge/models/item/{id}_in_hand.json",{"parent":"minecraft:item/spear_in_hand","textures":{"layer0":f"blastforge:item/{id}_in_hand"}})
        write_json(PACK/f"assets/blastforge/models/item/{id}.json",model)
        write_json(PACK/f"assets/blastforge/items/{id}.json",{"model":item_model(id)})
    save_image(PACK/"assets/blastforge/textures/item/legendary_scythe_art.png",scythe_pixels())
    carriers={carrier:[] for _,_,carrier in entries}
    for id,number,carrier in entries:carriers[carrier].append((number,id))
    for carrier,mappings in carriers.items():
        original=json.loads(client.read(f"assets/minecraft/items/{carrier}.json"));fallback=original["model"];dispatch=[]
        numbers={number for number,_ in mappings}
        for number,id in mappings:
            dispatch.append({"threshold":number,"model":item_model(id)})
            if number+1 not in numbers:dispatch.append({"threshold":number+1,"model":fallback})
        original["model"]={"type":"minecraft:range_dispatch","property":"minecraft:custom_model_data","index":0,"entries":sorted(dispatch,key=lambda x:x["threshold"]),"fallback":fallback}
        write_json(PACK/f"assets/minecraft/items/{carrier}.json",original)
    for tier in TIER:
        write_json(PACK/f"assets/blastforge/equipment/{tier}.json",{"layers":{"humanoid":[{"texture":f"blastforge:{tier}"}],"humanoid_leggings":[{"texture":f"blastforge:{tier}"}]}})
        for layer,legs in [("humanoid",False),("humanoid_leggings",True)]:
            # Hand-made worn-armor art (.tools/art/equipment_<layer>_<tier>.png) wins over the generated layer.
            art=ART_DIR/f"equipment_{layer}_{tier}.png"
            save_image(PACK/f"assets/blastforge/textures/entity/equipment/{layer}/{tier}.png",Image.open(art).convert("RGBA") if art.exists() else armor(tier,legs))
    providers=[{"type":"space","advances":{"\ue0f0":-8,"\ue0f1":-169}}]
    for index,theme in enumerate(["forging","upgrading","ritual"]):
        background=gui(theme);save_image(PACK/f"assets/blastforge/textures/font/{theme}.png",background);save_image(ART/f"gui-{theme}.png",background.resize((704,888),Image.Resampling.NEAREST));providers.append({"type":"bitmap","file":f"blastforge:font/{theme}.png","height":222,"ascent":13,"chars":[chr(0xE000+index)]})
    for index,mask in enumerate([1,2,3]):
        overlay=Image.new("RGBA",(176,222));d=ImageDraw.Draw(overlay)
        for bit,slot,color in [(1,20,(255,222,61)),(2,22,(92,255,153))]:
            if mask&bit:
                outline_slot(d,slot,color);x,y=8+18*(slot%9),18+18*(slot//9);d.rectangle((x-2,y-2,x+17,y+17),outline=(*color,90))
        # Bitmap advance depends on the rightmost alpha; a barely visible bottom-frame pixel fixes it at 177.
        overlay.putpixel((175,221),(30,50,60,1));save_image(PACK/f"assets/blastforge/textures/font/outline_{mask}.png",overlay);providers.append({"type":"bitmap","file":f"blastforge:font/outline_{mask}.png","height":222,"ascent":13,"chars":[chr(0xE010+index)]})
    write_json(PACK/"assets/blastforge/font/gui.json",{"providers":providers})
    # Add PUA providers, preserving the actual vanilla default font references.
    default=json.loads(client.read("assets/minecraft/font/default.json"));default["providers"].insert(0,{"type":"reference","id":"blastforge:gui"});write_json(PACK/"assets/minecraft/font/default.json",default)
    manifest={"minecraft":"1.21.11","resource_format":[75,0],"client_sha1":CLIENT_SHA,"models":[{"id":id,"number":number,"carrier":carrier} for id,number,carrier in entries]};write_json(PACK/"blastforge-manifest.json",manifest)
    preview_catalog(entries,icons);preview_scythe();preview_models()
    save_image(PACK/"pack.png",icons["enchanting_anvil"].resize((128,128),Image.Resampling.NEAREST))
    return entries

def preview_catalog(entries,icons):
    sheet=Image.new("RGB",(1100,5*146),(13,20,30));d=ImageDraw.Draw(sheet)
    for i,(id,number,carrier) in enumerate(entries):
        x,y=(i%11)*100,(i//11)*146;d.rounded_rectangle((x+3,y+3,x+96,y+141),5,fill=(20,32,44),outline=(42,66,80));sheet.paste(icons[id].resize((80,80),Image.Resampling.NEAREST),(x+10,y+8),icons[id].resize((80,80),Image.Resampling.NEAREST));words=id.split("_");label="\n".join(["_".join(words[:-1]),words[-1]]) if len(words)>1 else id;d.multiline_text((x+6,y+93),label,fill=(197,220,225),spacing=2);d.text((x+6,y+126),str(number),fill=GOLD)
    save_image(ART/"catalog-contact-sheet.png",sheet)

def preview_scythe():
    art=scythe_pixels();canvas=Image.new("RGBA",(1100,840),(13,17,26,255));d=ImageDraw.Draw(canvas)
    d.text((35,25),"BLASTFORGE / LEGENDARY SCYTHE",fill=(221,211,238));d.text((35,48),"Dark hooked crown • forged obsidian • violet fracture inlay",fill=(152,122,183))
    big=art.resize((448,672),Image.Resampling.NEAREST);canvas.alpha_composite(big,(30,100))
    # Orthographic projection of the actual model's cuboid faces, including extrusion.
    model=geometry("legendary_scythe");polygons=[]
    for element in model["elements"]:
        a,b=element["from"],element["to"]
        verts=[(x,y,z) for x in [a[0],b[0]] for y in [a[1],b[1]] for z in [a[2],b[2]]]
        def project(v):x,y,z=v;return (700+(x-8)*14+(z-8)*8,540-(y-8)*14+(z-8)*4)
        for face,indices in [("south",[1,3,7,5]),("east",[4,5,7,6]),("up",[2,3,7,6])]:
            points=[project(verts[n]) for n in indices];color=(42,32,54,255)
            if face=="south" and "#art"==element["faces"][face]["texture"]:
                # Split the face into its texel columns so the preview shows the texture the client renders.
                uv=element["faces"][face]["uv"];x0,x1=int(round(uv[0]*8)),int(round(uv[2]*8));py=min(191,max(0,int(uv[1]*12)))
                for tx in range(x0,max(x0+1,x1)):
                    fx0=a[0]+(b[0]-a[0])*(tx-x0)/max(1,x1-x0);fx1=a[0]+(b[0]-a[0])*(tx-x0+1)/max(1,x1-x0);z=b[2]
                    quad=[project((fx0,a[1],z)),project((fx1,a[1],z)),project((fx1,b[1],z)),project((fx0,b[1],z))]
                    texel=art.getpixel((min(127,tx),py))
                    if texel[3]:polygons.append((4*z+.2*(fx0+fx1)+.001,quad,texel))
                continue
            elif face=="east":color=(13,10,20,255)
            polygons.append((sum(verts[n][2]+verts[n][0]*.1 for n in indices),points,color))
    for _,points,color in sorted(polygons,key=lambda e:e[0]):d.polygon(points,fill=color)
    d.text((35,795),"Authored silhouette / texture",fill=(190,172,215));d.text((580,795),"Actual cuboid model / oblique projection",fill=(190,172,215));save_image(ART/"scythe-model-preview.png",canvas.convert("RGB"))

def preview_models():
    ids=["enchanting_anvil","anvil_hammer","anvil_tools","magician_robes","miner_helmet","miner_backpack"];sheet=Image.new("RGB",(1080,440),(13,20,30));d=ImageDraw.Draw(sheet)
    for i,id in enumerate(ids):
        model=geometry(id);cx=180*(i%6)+85;cy=265;polys=[]
        for element in model["elements"]:
            a,b=element["from"],element["to"];verts=[(x,y,z) for x in [a[0],b[0]] for y in [a[1],b[1]] for z in [a[2],b[2]]]
            def project(p):x,y,z=p;return(cx+(x-8)*6+(z-8)*3,cy-y*7+(z-8)*2)
            for face,indices,color in [("south",[1,3,7,5],(42,134,153)),("east",[4,5,7,6],(20,65,81)),("up",[2,3,7,6],(91,191,197))]:
                if element["faces"][face]["uv"][0]==0 and element["faces"][face]["uv"][2]==4:color=GOLD
                if id=="magician_robes":color=(97,46,139) if face=="south" else (62,27,93)
                if id.startswith("miner"):color=(157,109,47) if face=="south" else (82,55,33)
                polys.append((sum(verts[n][2]+verts[n][0] for n in indices),[project(verts[n]) for n in indices],color))
        for _,points,color in sorted(polys,key=lambda e:e[0]):d.polygon(points,fill=color,outline=(9,14,22))
        d.text((180*i+8,340),id.replace("_","\n"),fill=(211,226,229))
    save_image(ART/"model-contact-sheet.png",sheet)

def validate(client,entries):
    names=set(client.namelist());local={p.relative_to(PACK).as_posix() for p in PACK.rglob("*") if p.is_file()}
    errors=[]
    def exists(path):return path in local or path in names
    def resolve(identifier,folder,suffix=".json"):
        namespace,path=(identifier.split(":",1) if ":" in identifier else ["minecraft",identifier]);return f"assets/{namespace}/{folder}/{path}{suffix}"
    def nodes(value):
        if isinstance(value,dict):yield value
        if isinstance(value,dict):
            for v in value.values():yield from nodes(v)
        elif isinstance(value,list):
            for v in value:yield from nodes(v)
    for path in PACK.rglob("*.json"):
        data=json.loads(path.read_text(encoding="utf-8"));rel=path.relative_to(PACK).as_posix()
        if not re.fullmatch(r"[a-z0-9_./-]+",rel):errors.append("Invalid path: "+rel)
        for node in nodes(data):
            if "parent" in node and not node["parent"].startswith("builtin/") and not exists(resolve(node["parent"],"models")):errors.append("Missing parent "+node["parent"])
            if node.get("type")=="minecraft:model" and not exists(resolve(node["model"],"models")):errors.append("Missing item model "+node["model"])
            if "textures" in node and isinstance(node["textures"],dict):
                for tex in node["textures"].values():
                    if isinstance(tex,str) and not tex.startswith("#") and not exists(resolve(tex,"textures",".png")):errors.append("Missing texture "+tex)
            if node.get("type")=="bitmap" and not exists(resolve(node["file"],"textures","")):errors.append("Missing font "+node["file"])
            if node.get("type")=="reference" and not exists(resolve(node["id"],"font")):errors.append("Missing font ref "+node["id"])
            if "from" in node and "to" in node:
                for axis in range(3):
                    if not -16<=node["from"][axis]<node["to"][axis]<=32:errors.append("Invalid model bound "+rel)
        if "/equipment/" in rel:
            for layer,definitions in data.get("layers",{}).items():
                for node in definitions:
                    namespace,tex=node["texture"].split(":");target=f"assets/{namespace}/textures/entity/equipment/{layer}/{tex}.png"
                    if not exists(target):errors.append("Missing equipment "+target)
    for path in PACK.rglob("*.png"):
        with Image.open(path) as image:
            image.verify()
    # One pack for every 1.21.x client: formats 34 (1.21.0) to 75 (1.21.11), with version overlays (see legacy_pack.py)
    metadata=json.loads((PACK/"pack.mcmeta").read_text());pack=metadata["pack"]
    assert pack["pack_format"]==34 and pack["supported_formats"]==[34,75] and pack["min_format"]==34 and pack["max_format"]==75,pack
    for overlay in metadata.get("overlays",{}).get("entries",[]):
        assert (PACK/overlay["directory"]).is_dir() and overlay["formats"]==[overlay["min_format"],overlay["max_format"]],overlay
        assert 34<=overlay["min_format"]<=overlay["max_format"]<=75,overlay
    # classic overrides used by 1.21.0-1.21.3 must point at models that exist (vanilla trim models use their old names)
    for path in PACK.glob("overlay_*/assets/*/models/item/*.json"):
        for override in json.loads(path.read_text()).get("overrides",[]):
            if "custom_model_data" in override["predicate"] and not exists(resolve(override["model"],"models")):errors.append("Missing legacy override model "+override["model"])
    for id,number,carrier in entries:
        data=json.loads((PACK/f"assets/minecraft/items/{carrier}.json").read_text());node=data["model"];assert any(e["threshold"]==number and e["model"]==item_model(id) for e in node["entries"])
        assert node["fallback"]==json.loads(client.read(f"assets/minecraft/items/{carrier}.json"))["model"]
        # Every unreserved integer falls back, including the upper range.
        allowed={n for _,n,c in entries if c==carrier}
        for n in [0,8999,*range(9000,9067),10000]:
            chosen=node["fallback"]
            for e in node["entries"]:
                if e["threshold"]<=n:chosen=e["model"]
            if n not in allowed:assert chosen==node["fallback"],(carrier,n)
    if errors:raise AssertionError("\n".join(errors))
    return {"models":len(entries),"json":len(list(PACK.rglob("*.json"))),"png":len(list(PACK.rglob("*.png"))),"status":"PASS"}

def repack(client,entries):
    """Validate then package existing editable JSON/PNG files; never regenerate art."""
    import legacy_pack;legacy_pack.CLIENT=CLIENT;legacy_pack.main()   # overlays for 1.21.0-1.21.10 clients
    report=validate(client,entries);OUT.mkdir(parents=True,exist_ok=True)
    archive=OUT/"TheForge-pack.zip";temporary=archive.with_suffix(".zip.tmp")
    try:
        with zipfile.ZipFile(temporary,"w",zipfile.ZIP_DEFLATED,compresslevel=9) as z:
            for path in sorted(PACK.rglob("*")):
                if path.is_file():
                    # Fixed archive timestamps make an unchanged source tree reproduce byte-for-byte.
                    info=zipfile.ZipInfo(path.relative_to(PACK).as_posix(),(2026,1,1,0,0,0));info.compress_type=zipfile.ZIP_DEFLATED
                    z.writestr(info,path.read_bytes())
        with zipfile.ZipFile(temporary) as z:assert "pack.mcmeta" in z.namelist() and z.testzip() is None
        temporary.replace(archive)
    finally:
        if temporary.exists():temporary.unlink()
    report["zip"]=str(archive);report["sha1"]=hashlib.sha1(archive.read_bytes()).hexdigest()
    (OUT/"TheForge-pack.sha1").write_text(report["sha1"]+"\n")
    write_json(OUT/"pack-validation.json",report)
    return report

def main():
    parser=argparse.ArgumentParser(description=__doc__);mode=parser.add_mutually_exclusive_group();mode.add_argument("--validate-only",action="store_true",help="Validate editable pack sources, without generating or packaging");mode.add_argument("--repack",action="store_true",help="Validate and package existing source assets, preserving manual edits");args=parser.parse_args()
    if not CLIENT.exists():urllib.request.urlretrieve(f"https://piston-data.mojang.com/v1/objects/{CLIENT_SHA}/client.jar",CLIENT)
    assert hashlib.sha1(CLIENT.read_bytes()).hexdigest()==CLIENT_SHA
    with zipfile.ZipFile(CLIENT) as client:
        entries=catalog() if args.validate_only or args.repack else generate(client)
        report=validate(client,entries) if args.validate_only else repack(client,entries)
    if args.validate_only:write_json(OUT/"pack-validation.json",report)
    print(json.dumps(report,indent=2))

if __name__=="__main__":main()
