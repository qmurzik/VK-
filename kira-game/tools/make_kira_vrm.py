"""Готовит модель Киры из образца Seed-san (VirtualCast, Inc., лицензия VRM 1.0):
перекрашивает текстуры в стиль Киры, убирает рюкзак и роборуку, сжимает файл.
Запуск: python3 -I tools/make_kira_vrm.py Seed-san.vrm game/assets/kira_vrm.js"""
import base64
import colorsys
import copy
import io
import json
import struct
import sys

import numpy as np
from PIL import Image

src, out_js = sys.argv[1], sys.argv[2]
d = open(src, 'rb').read()
jl = struct.unpack('<I', d[12:16])[0]
j = json.loads(d[20:20 + jl])
b0 = 20 + jl + 8
binbuf = d[b0:]


def view_bytes(i):
    bv = j['bufferViews'][i]
    o = bv.get('byteOffset', 0)
    return binbuf[o:o + bv['byteLength']]


def img_of(name):
    for i, im in enumerate(j['images']):
        if im.get('name') == name:
            return i
    raise KeyError(name)


def load_img(name):
    return Image.open(io.BytesIO(view_bytes(j['images'][img_of(name)]['bufferView']))).convert('RGBA')


new_view_data = {}  # bufferView index -> bytes


def save_img(name, im, size=None):
    if size:
        im = im.resize(size, Image.LANCZOS)
    b = io.BytesIO()
    im.save(b, 'PNG', optimize=True)
    new_view_data[j['images'][img_of(name)]['bufferView']] = b.getvalue()


def hsv_arrays(a):
    rgb = a[..., :3].astype(np.float32) / 255
    mx = rgb.max(-1)
    mn = rgb.min(-1)
    v = mx
    s = np.where(mx > 0, (mx - mn) / np.maximum(mx, 1e-6), 0)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    dlt = np.maximum(mx - mn, 1e-6)
    h = np.where(mx == r, ((g - b) / dlt) % 6, np.where(mx == g, (b - r) / dlt + 2, (r - g) / dlt + 4)) * 60
    return h, s, v


def from_hsv(h, s, v):
    h = (h % 360) / 60
    c = v * s
    x = c * (1 - np.abs(h % 2 - 1))
    m = v - c
    z = np.zeros_like(h)
    conds = [(h < 1), (h < 2), (h < 3), (h < 4), (h < 5), (h >= 5)]
    rs = [c, x, z, z, x, c]
    gs = [x, c, c, x, z, z]
    bs = [z, z, x, c, c, x]
    r = np.select(conds, rs); g = np.select(conds, gs); b = np.select(conds, bs)
    return np.stack([r + m, g + m, b + m], -1)


PURPLE_H = 272

# --- одежда: белое -> чёрное, голубое -> фиолетовое ---
im = load_img('wear')
a = np.array(im).astype(np.float32)
h, s, v = hsv_arrays(a)
lum = a[..., :3].mean(-1) / 255
cyan = (h > 170) & (h < 225) & (s > 0.25)
dark = np.stack([0.075 + 0.2 * lum ** 1.4, 0.07 + 0.18 * lum ** 1.4, 0.095 + 0.24 * lum ** 1.4], -1)
purple = from_hsv(np.full_like(h, PURPLE_H), np.clip(s * 1.1, 0, 1), np.clip(v * 0.95, 0, 1))
rgb = np.where(cyan[..., None], purple, dark)
a[..., :3] = np.clip(rgb * 255, 0, 255)
save_img('wear', Image.fromarray(a.astype(np.uint8)))

# --- тело: голубые детали перчаток -> фиолетовые ---
im = load_img('body')
a = np.array(im).astype(np.float32)
h, s, v = hsv_arrays(a)
cyan = (h > 170) & (h < 225) & (s > 0.3)
purple = from_hsv(np.full_like(h, PURPLE_H), s, v)
a[..., :3] = np.where(cyan[..., None], purple * 255, a[..., :3])
save_img('body', Image.fromarray(a.astype(np.uint8)))

# --- глаза: янтарные -> фиолетовые ---
im = load_img('faceparts')
a = np.array(im).astype(np.float32)
H, Wd = a.shape[:2]
h, s, v = hsv_arrays(a)
yy, xx = np.mgrid[0:H, 0:Wd]
iris = (h > 15) & (h < 65) & (s > 0.2) & (yy < H * 0.62) & (a[..., 3] > 10)
# зрачок/радужка: сохраняем яркость, сдвигаем оттенок
purple = from_hsv(np.full_like(h, 275), np.clip(s * 1.15, 0, 1), np.clip(v * 1.05, 0, 1))
a[..., :3] = np.where(iris[..., None], purple * 255, a[..., :3])
save_img('faceparts', Image.fromarray(a.astype(np.uint8)))

# --- волосы: фиолетовые кончики ---
hair = load_img('hair')
base = np.array(hair).astype(np.float32)
H, Wd = base.shape[:2]
pur = np.array([140, 77, 255], np.float32)


def tint(arr, mask_t):
    # mask_t: 0..1 сила фиолетового по строкам
    lum = arr[..., :3].mean(-1, keepdims=True) / 255
    col = pur * (0.35 + 0.9 * lum)
    k = mask_t[:, None, None]
    out = arr.copy()
    out[..., :3] = arr[..., :3] * (1 - k) + np.clip(col, 0, 255) * k
    return out


t = np.linspace(0, 1, H)
main = tint(base, np.clip((t - 0.62) / 0.3, 0, 1) ** 1.3 * 0.95)  # низ картинки = кончики
save_img('hair', Image.fromarray(main.astype(np.uint8)))
tail = tint(base, np.clip((0.4 - t) / 0.35, 0, 1) ** 1.2 * 0.95)  # у «хвостика» кончики вверху
# новая картинка/текстура/материал для хвостика
tb = io.BytesIO()
Image.fromarray(tail.astype(np.uint8)).save(tb, 'PNG', optimize=True)
j['bufferViews'].append({'buffer': 0, 'byteOffset': 0, 'byteLength': 0})
tail_view = len(j['bufferViews']) - 1
new_view_data[tail_view] = tb.getvalue()
j['images'].append({'name': 'hair_tail', 'mimeType': 'image/png', 'bufferView': tail_view})
hair_tex = None
hair_mat_i = [m['name'] for m in j['materials']].index('hair')
hair_mat = j['materials'][hair_mat_i]
hair_tex_i = hair_mat['pbrMetallicRoughness']['baseColorTexture']['index']
tex = copy.deepcopy(j['textures'][hair_tex_i])
tex['source'] = len(j['images']) - 1
j['textures'].append(tex)
new_tex_i = len(j['textures']) - 1
m2 = copy.deepcopy(hair_mat)
m2['name'] = 'hair_tail'
m2['pbrMetallicRoughness']['baseColorTexture']['index'] = new_tex_i
mt = m2.get('extensions', {}).get('VRMC_materials_mtoon')
if mt and 'shadeMultiplyTexture' in mt and mt['shadeMultiplyTexture']['index'] == hair_tex_i:
    mt['shadeMultiplyTexture']['index'] = new_tex_i
j['materials'].append(m2)
for mesh in j['meshes']:
    if mesh['name'] == 'hair_tail':
        for p in mesh['primitives']:
            if p['material'] == hair_mat_i:
                p['material'] = len(j['materials']) - 1

# --- убираем рюкзак, роборуку и прочее ---
REMOVE = {'arm_mat', 'arm_plastic', 'green_emit', 'wear_metal', 'armgear_plastic', 'robo_face', 'glass',
          'backpack_metal', 'backpack_nm', 'backpack_plastic', 'anim_logo'}
names = [m['name'] for m in j['materials']]
for mesh in j['meshes']:
    mesh['primitives'] = [p for p in mesh['primitives'] if names[p['material']] not in REMOVE]
for n in j['nodes']:
    if 'mesh' in n and not j['meshes'][n['mesh']]['primitives']:
        del n['mesh']
        n.pop('skin', None)

# --- сборка мусора: неиспользуемые аксессоры и картинки ---
used_acc = set()
for mesh in j['meshes']:
    for p in mesh['primitives']:
        used_acc.update(p['attributes'].values())
        if 'indices' in p:
            used_acc.add(p['indices'])
        for tg in p.get('targets', []):
            used_acc.update(tg.values())
for sk in j.get('skins', []):
    if 'inverseBindMatrices' in sk:
        used_acc.add(sk['inverseBindMatrices'])
for an in j.get('animations', []):
    for smp in an['samplers']:
        used_acc.update([smp['input'], smp['output']])
for i, acc in enumerate(j['accessors']):
    if i not in used_acc:
        acc.pop('bufferView', None)
        acc.pop('byteOffset', None)
        acc.pop('sparse', None)
used_mat = {p['material'] for m in j['meshes'] for p in m['primitives']}
used_tex = set()


def walk(o):
    if isinstance(o, dict):
        if 'index' in o and isinstance(o['index'], int) and set(o.keys()) <= {'index', 'texCoord', 'scale', 'strength', 'extensions'}:
            used_tex.add(o['index'])
        for v2 in o.values():
            walk(v2)
    elif isinstance(o, list):
        for v2 in o:
            walk(v2)


for i in used_mat:
    walk(j['materials'][i])
used_img = {j['textures'][t]['source'] for t in used_tex}
vrm = j['extensions']['VRMC_vrm']
if 'thumbnailImage' in vrm['meta']:
    used_img.add(vrm['meta']['thumbnailImage'])
tiny = io.BytesIO()
Image.new('RGBA', (2, 2), (0, 0, 0, 0)).save(tiny, 'PNG')
for i, img in enumerate(j['images']):
    if i not in used_img:
        new_view_data[img['bufferView']] = tiny.getvalue()
        img['mimeType'] = 'image/png'
    elif i == vrm['meta'].get('thumbnailImage'):
        th = Image.open(io.BytesIO(new_view_data.get(img['bufferView'], view_bytes(img['bufferView'])))).resize((128, 128))
        b = io.BytesIO(); th.save(b, 'PNG'); new_view_data[img['bufferView']] = b.getvalue()

used_views = {a['bufferView'] for a in j['accessors'] if 'bufferView' in a} | {im['bufferView'] for im in j['images']}
for a in j['accessors']:
    if 'sparse' in a:
        used_views |= {a['sparse']['indices']['bufferView'], a['sparse']['values']['bufferView']}
remap = {}
blob = bytearray()
new_views = []
for i, bv in enumerate(j['bufferViews']):
    if i not in used_views:
        continue
    data = new_view_data.get(i, view_bytes(i) if i < len(j['bufferViews']) and bv['byteLength'] else b'')
    while len(blob) % 4:
        blob.append(0)
    nb = dict(bv)
    nb['byteOffset'] = len(blob)
    nb['byteLength'] = len(data)
    blob += data
    remap[i] = len(new_views)
    new_views.append(nb)
j['bufferViews'] = new_views
for a in j['accessors']:
    if 'bufferView' in a:
        a['bufferView'] = remap[a['bufferView']]
    if 'sparse' in a:
        a['sparse']['indices']['bufferView'] = remap[a['sparse']['indices']['bufferView']]
        a['sparse']['values']['bufferView'] = remap[a['sparse']['values']['bufferView']]
for im in j['images']:
    im['bufferView'] = remap[im['bufferView']]
while len(blob) % 4:
    blob.append(0)
j['buffers'] = [{'byteLength': len(blob)}]

js = json.dumps(j, separators=(',', ':')).encode()
while len(js) % 4:
    js += b' '
glb = struct.pack('<III', 0x46546C67, 2, 12 + 8 + len(js) + 8 + len(blob))
glb += struct.pack('<II', len(js), 0x4E4F534A) + js + struct.pack('<II', len(blob), 0x004E4942) + bytes(blob)
open(out_js, 'w').write('// Модель Киры: основа — Seed-san (c) VirtualCast, Inc., VRM Public License 1.0 (https://vrm.dev/licenses/1.0/)\n'
                        'window.KIRA_VRM_B64="' + base64.b64encode(glb).decode() + '";\n')
print('glb bytes', len(glb))
