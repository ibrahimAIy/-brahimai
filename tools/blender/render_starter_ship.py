import bpy
import math
import os
from mathutils import Vector

# ---------------- Scene ----------------
bpy.ops.object.select_all(action='SELECT')
bpy.ops.object.delete(use_global=False)

scene = bpy.context.scene
try:
    scene.render.engine = 'BLENDER_EEVEE_NEXT'
except Exception:
    scene.render.engine = 'BLENDER_EEVEE'

scene.render.resolution_x = 192
scene.render.resolution_y = 192
scene.render.resolution_percentage = 100
scene.render.image_settings.file_format = 'PNG'
scene.render.image_settings.color_mode = 'RGBA'
scene.render.film_transparent = True
try:
    scene.view_settings.look = 'AgX - Medium High Contrast'
except Exception:
    pass

# ---------------- Helpers ----------------
def mat(name, color, rough=0.55, metallic=0.0, emission=None, emission_strength=0.0):
    m = bpy.data.materials.new(name)
    m.use_nodes = True
    bsdf = m.node_tree.nodes.get('Principled BSDF')
    if bsdf:
        if 'Base Color' in bsdf.inputs:
            bsdf.inputs['Base Color'].default_value = (*color, 1.0)
        if 'Roughness' in bsdf.inputs:
            bsdf.inputs['Roughness'].default_value = rough
        if 'Metallic' in bsdf.inputs:
            bsdf.inputs['Metallic'].default_value = metallic
        if emission:
            if 'Emission Color' in bsdf.inputs:
                bsdf.inputs['Emission Color'].default_value = (*emission, 1.0)
            elif 'Emission' in bsdf.inputs:
                bsdf.inputs['Emission'].default_value = (*emission, 1.0)
            if 'Emission Strength' in bsdf.inputs:
                bsdf.inputs['Emission Strength'].default_value = emission_strength
    return m

def look_at(obj, target):
    direction = Vector(target) - obj.location
    obj.rotation_euler = direction.to_track_quat('-Z', 'Y').to_euler()

def cylinder_between(name, a, b, radius, material, vertices=12):
    a = Vector(a)
    b = Vector(b)
    mid = (a + b) * 0.5
    d = b - a
    bpy.ops.mesh.primitive_cylinder_add(vertices=vertices, radius=radius, depth=d.length, location=mid)
    obj = bpy.context.object
    obj.name = name
    obj.rotation_mode = 'QUATERNION'
    obj.rotation_quaternion = d.to_track_quat('Z', 'Y')
    obj.data.materials.append(material)
    return obj

def rope_curve(name, pts, bevel, material):
    curve = bpy.data.curves.new(name, 'CURVE')
    curve.dimensions = '3D'
    curve.resolution_u = 2
    curve.bevel_depth = bevel
    curve.bevel_resolution = 2
    spline = curve.splines.new('POLY')
    spline.points.add(len(pts) - 1)
    for p, co in zip(spline.points, pts):
        p.co = (*co, 1.0)
    obj = bpy.data.objects.new(name, curve)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(material)
    return obj

# ---------------- Materials ----------------
wood = mat('HullWood', (0.20, 0.075, 0.025), 0.62)
wood_light = mat('DeckWood', (0.43, 0.20, 0.055), 0.58)
wood_edge = mat('EdgeWood', (0.11, 0.038, 0.014), 0.64)
rope = mat('Rope', (0.34, 0.22, 0.10), 0.8)
sail = mat('Sail', (0.82, 0.77, 0.63), 0.72)
sail_edge = mat('SailEdge', (0.43, 0.34, 0.22), 0.78)
metal = mat('Metal', (0.12, 0.13, 0.13), 0.30, 0.72)
bronze = mat('Bronze', (0.34, 0.19, 0.065), 0.32, 0.55)
flag = mat('Flag', (0.40, 0.025, 0.012), 0.65)
lantern = mat('LanternGlow', (0.55, 0.26, 0.03), 0.25, 0.15, (1.0, 0.38, 0.035), 2.2)

# ---------------- Root ----------------
root = bpy.data.objects.new('StarterSloopRoot', None)
bpy.context.collection.objects.link(root)

# ---------------- Hull ----------------
# Long but tiny beginner hull
hull_verts = [
    (3.75, 0.0, 0.62),
    (2.6, -0.86, 0.58),
    (0.7, -1.08, 0.54),
    (-2.25, -0.95, 0.58),
    (-3.55, -0.58, 0.64),
    (-3.95, 0.0, 0.70),
    (-3.55, 0.58, 0.64),
    (-2.25, 0.95, 0.58),
    (0.7, 1.08, 0.54),
    (2.6, 0.86, 0.58),
    (3.15, 0.0, -0.36),
    (2.2, -0.54, -0.48),
    (0.4, -0.68, -0.56),
    (-2.35, -0.56, -0.47),
    (-3.25, -0.32, -0.31),
    (-3.55, 0.0, -0.24),
    (-3.25, 0.32, -0.31),
    (-2.35, 0.56, -0.47),
    (0.4, 0.68, -0.56),
    (2.2, 0.54, -0.48),
]
hull_faces = [
    (0,1,2,3,4,5,6,7,8,9),
    (10,19,18,17,16,15,14,13,12,11),
    (0,10,11,1),(1,11,12,2),(2,12,13,3),(3,13,14,4),(4,14,15,5),
    (5,15,16,6),(6,16,17,7),(7,17,18,8),(8,18,19,9),(9,19,10,0),
]
hm = bpy.data.meshes.new('StarterHullMesh')
hm.from_pydata(hull_verts, [], hull_faces)
hm.update()
hull = bpy.data.objects.new('StarterHull', hm)
bpy.context.collection.objects.link(hull)
hull.data.materials.append(wood)
hull.parent = root

# Deck
bpy.ops.mesh.primitive_cube_add(location=(-0.1, 0, 0.72), scale=(2.65, 0.72, 0.11))
deck = bpy.context.object
deck.name = 'StarterDeck'
deck.data.materials.append(wood_light)
deck.parent = root

# Raised rear platform
bpy.ops.mesh.primitive_cube_add(location=(-2.72, 0, 1.02), scale=(0.78, 0.70, 0.18))
stern = bpy.context.object
stern.name = 'StarterSternDeck'
stern.data.materials.append(wood_light)
stern.parent = root

# Gunwales / rails
for y in (-0.92, 0.92):
    obj = cylinder_between('Gunwale', (-3.35,y,0.92),(2.9,y*0.88,0.84),0.055,wood_edge,10)
    obj.parent = root
for x in (-2.9,-2.0,-1.1,-0.2,0.7,1.6,2.5):
    for y in (-0.91,0.91):
        post = cylinder_between('RailPost',(x,y,0.82),(x,y,1.18),0.032,wood_edge,8)
        post.parent = root

# Bow beam
bow = cylinder_between('Bowsprit',(2.6,0,0.98),(4.9,0,1.55),0.065,wood_edge,10)
bow.parent = root

# ---------------- Mast + single sail ----------------
mast = cylinder_between('MainMast',(-0.25,0,0.65),(-0.25,0,5.7),0.085,wood_edge,14)
mast.parent = root
yard = cylinder_between('MainYard',(-0.25,-1.52,4.85),(-0.25,1.52,4.85),0.060,wood_edge,12)
yard.parent = root

# One dominant rectangular-triangular cloth, slightly bowed
sail_mesh = bpy.data.meshes.new('StarterSailMesh')
sail_mesh.from_pydata([
    (-0.28,-1.40,4.80),
    (-0.28, 1.40,4.80),
    (-0.20, 1.10,2.10),
    (-0.12, 0.0,1.78),
    (-0.20,-1.10,2.10),
    ( 0.18, 0.0,3.15),
], [], [
    (0,1,5),
    (1,2,5),
    (2,3,5),
    (3,4,5),
    (4,0,5),
])
sail_mesh.update()
sail_obj = bpy.data.objects.new('SingleMainSail', sail_mesh)
bpy.context.collection.objects.link(sail_obj)
sail_obj.data.materials.append(sail)
sail_obj.parent = root

# Sail seams
for y in (-1.15,-0.58,0,0.58,1.15):
    seam = cylinder_between('SailSeam',(-0.285,y,4.70),(-0.19,y*0.79,2.18),0.013,sail_edge,8)
    seam.parent = root

# Rigging
for p1,p2 in [
    ((-0.25,0,5.58),(-3.55,0,0.88)),
    ((-0.25,0,5.58),(3.72,0,0.94)),
    ((-0.25,0,5.25),(-2.8,-0.82,0.86)),
    ((-0.25,0,5.25),(-2.8,0.82,0.86)),
    ((-0.25,0,5.25),(2.35,-0.77,0.84)),
    ((-0.25,0,5.25),(2.35,0.77,0.84)),
]:
    r = rope_curve('Rigging',[p1,p2],0.014,rope)
    r.parent = root

# Rope coils on deck
for x,y in [(-1.65,-0.28),(-1.55,0.35)]:
    bpy.ops.mesh.primitive_torus_add(major_radius=0.24, minor_radius=0.035, major_segments=18, minor_segments=6, location=(x,y,1.03))
    coil = bpy.context.object
    coil.data.materials.append(rope)
    coil.rotation_euler[0] = math.radians(90)
    coil.parent = root

# ---------------- Tiny cannon ----------------
bpy.ops.mesh.primitive_cube_add(location=(1.65,0,1.00), scale=(0.50,0.34,0.13))
carriage = bpy.context.object
carriage.data.materials.append(wood_edge)
carriage.parent = root
bpy.ops.mesh.primitive_cylinder_add(vertices=16, radius=0.145, depth=1.15, location=(2.06,0,1.25))
cannon = bpy.context.object
cannon.rotation_euler[1] = math.radians(90)
cannon.data.materials.append(metal)
cannon.parent = root
bpy.ops.mesh.primitive_torus_add(major_radius=0.16, minor_radius=0.03, major_segments=18, minor_segments=6, location=(2.58,0,1.25))
ring = bpy.context.object
ring.rotation_euler[1] = math.radians(90)
ring.data.materials.append(bronze)
ring.parent = root

# ---------------- Barrel + crate ----------------
bpy.ops.mesh.primitive_cylinder_add(vertices=14, radius=0.32, depth=0.58, location=(-1.72,0.45,1.18))
barrel = bpy.context.object
barrel.data.materials.append(wood_light)
barrel.parent = root
for z in (0.98,1.37):
    bpy.ops.mesh.primitive_torus_add(major_radius=0.31, minor_radius=0.025, major_segments=16, minor_segments=6, location=(-1.72,0.45,z))
    hoop = bpy.context.object
    hoop.data.materials.append(metal)
    hoop.parent = root

bpy.ops.mesh.primitive_cube_add(location=(-2.0,-0.37,1.17), scale=(0.38,0.30,0.30))
crate = bpy.context.object
crate.data.materials.append(wood_light)
crate.parent = root

# ---------------- Stern lantern + red pennant ----------------
post = cylinder_between('LanternPost',(-3.05,-0.62,1.02),(-3.05,-0.62,2.02),0.045,wood_edge,8)
post.parent = root
bpy.ops.mesh.primitive_cube_add(location=(-3.05,-0.62,1.88), scale=(0.12,0.12,0.18))
lamp = bpy.context.object
lamp.data.materials.append(lantern)
lamp.parent = root

flag_mesh = bpy.data.meshes.new('PennantMesh')
flag_mesh.from_pydata([
    (-0.25,0,5.67),
    (-0.25,0,6.02),
    (-1.55,0,5.86),
], [], [(0,1,2)])
flag_mesh.update()
pennant = bpy.data.objects.new('RedPennant',flag_mesh)
bpy.context.collection.objects.link(pennant)
pennant.data.materials.append(flag)
pennant.parent = root

# ---------------- Lighting ----------------
bpy.ops.object.light_add(type='SUN', location=(0,0,10))
sun = bpy.context.object
sun.rotation_euler = (math.radians(28), math.radians(-18), math.radians(-32))
sun.data.energy = 4.2
sun.data.angle = math.radians(3.5)

bpy.ops.object.light_add(type='AREA', location=(-6,-8,10))
fill = bpy.context.object
fill.data.energy = 650
fill.data.shape = 'DISK'
fill.data.size = 9
look_at(fill,(0,0,1.8))

world = scene.world
world.use_nodes = True
bg = world.node_tree.nodes.get('Background')
if bg:
    bg.inputs['Color'].default_value = (0.055,0.08,0.10,1)
    bg.inputs['Strength'].default_value = 0.35

# ---------------- Camera ----------------
bpy.ops.object.camera_add(location=(9.6,-13.0,10.2))
cam = bpy.context.object
cam.data.type = 'ORTHO'
cam.data.ortho_scale = 10.2
look_at(cam,(0,0,2.0))
scene.camera = cam

# ---------------- Output ----------------
out_dir = os.path.join(os.getcwd(),'render')
frames_dir = os.path.join(out_dir,'frames')
os.makedirs(frames_dir,exist_ok=True)

bpy.ops.wm.save_as_mainfile(filepath=os.path.join(out_dir,'starter_sloop.blend'))

for idx in range(32):
    # 0 = east/right; rotate model to cover full 360
    root.rotation_euler[2] = -(idx / 32.0) * math.tau
    scene.render.filepath = os.path.join(frames_dir, f'starter_{idx:02d}.png')
    bpy.ops.render.render(write_still=True)

print('Rendered 32 starter sloop directions')
